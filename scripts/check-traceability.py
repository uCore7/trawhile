#!/usr/bin/env python3
"""
check-traceability.py - verify UR -> SR -> TE traceability.

The script reads:
  - docs/requirements-ur.md
  - docs/requirements-sr.md
  - spec/test-plan.md
  - src/test/java/**/*.java
  - target/surefire-reports/TEST-*.xml
  - target/failsafe-reports/TEST-*.xml

It validates three layers of traceability:
  - requirements coverage: required URs have SRs, required SRs have planned TEs
  - implementation coverage: planned TEs are implemented as @Tag("TE-...") tests
  - execution coverage: implemented TE-tagged test methods were executed

By default the script follows the repository's process rules:
  - UR-F and UR-Q must have at least one SR
  - SR-F and SR-Q must have at least one TE

Use --rule-profile strict to require all URs and all SRs to be covered.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable
from xml.etree import ElementTree

BACKEND_TEST_TYPES = {"IT", "UT", "SIT"}
FRONTEND_TEST_TYPES = {"CT", "E2E"}

UR_LINE_RE = re.compile(r"^- (UR-[FQC]\d{3}):\s*(.+)$")
SR_LINE_RE = re.compile(r"^\*\*(SR-[FQC]\d{3}\.[FQC]\d{2}) \(type ([FQC])\):\*\*")
PACKAGE_RE = re.compile(r"^\s*package\s+([a-zA-Z_][\w.]*)\s*;")
CLASS_RE = re.compile(r"^\s*(?:public\s+)?(?:abstract\s+)?(?:final\s+)?class\s+([A-Za-z_]\w*)\b")
TAG_RE = re.compile(r'@Tag\("((?:TE|UR|SR)-[^"]+)"\)')
VOID_METHOD_RE = re.compile(r"\bvoid\s+([A-Za-z_]\w*)\s*\(")


@dataclass(frozen=True)
class UrEntry:
    id: str
    type: str
    retired: bool


@dataclass(frozen=True)
class SrEntry:
    id: str
    type: str
    parent_ur: str


@dataclass(frozen=True)
class PlannedTeEntry:
    id: str
    sr_id: str
    test_type: str
    test_class: str


@dataclass(frozen=True)
class ImplementedMethod:
    te_id: str
    class_name: str
    method_name: str
    source_file: str

    @property
    def key(self) -> tuple[str, str]:
        return self.class_name, self.method_name


@dataclass(frozen=True)
class ExecutedMethod:
    class_name: str
    method_name: str
    status: str
    report_file: str

    @property
    def key(self) -> tuple[str, str]:
        return self.class_name, self.method_name


def project_root_from_script() -> Path:
    return Path(__file__).resolve().parent.parent


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--scope",
        choices=("backend", "frontend", "all"),
        default="backend",
        help="Which planned TEs to evaluate. Default: backend.",
    )
    parser.add_argument(
        "--rule-profile",
        choices=("process", "strict"),
        default="process",
        help="Traceability policy. Default: process.",
    )
    parser.add_argument(
        "--root",
        type=Path,
        default=project_root_from_script(),
        help="Project root. Default: repository root.",
    )
    parser.add_argument(
        "--test-src",
        type=Path,
        default=Path("src/test/java"),
        help="Path to backend test sources relative to --root.",
    )
    parser.add_argument(
        "--reports-dir",
        action="append",
        default=[],
        help="Surefire/Failsafe report directory relative to --root. Repeatable.",
    )
    parser.add_argument(
        "--no-execution",
        action="store_true",
        help="Skip execution checks and only validate planned vs implemented coverage.",
    )
    parser.add_argument(
        "--json",
        action="store_true",
        help="Emit JSON instead of the human-readable report.",
    )
    parser.add_argument(
        "--max-items",
        type=int,
        default=20,
        help="Maximum items to show per section in human-readable output. Default: 20.",
    )
    parser.add_argument(
        "--show-methods",
        action="store_true",
        help="Include per-method execution details for failed, skipped, or not-run TEs.",
    )
    return parser.parse_args()


def selected_test_types(scope: str) -> set[str]:
    if scope == "backend":
        return set(BACKEND_TEST_TYPES)
    if scope == "frontend":
        return set(FRONTEND_TEST_TYPES)
    return BACKEND_TEST_TYPES | FRONTEND_TEST_TYPES


def report_dirs(root: Path, explicit: list[str]) -> list[Path]:
    if explicit:
        return [root / Path(entry) for entry in explicit]
    return [root / Path("target/surefire-reports"), root / Path("target/failsafe-reports")]


def parse_urs(path: Path) -> dict[str, UrEntry]:
    entries: dict[str, UrEntry] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        match = UR_LINE_RE.match(raw_line)
        if not match:
            continue
        ur_id, description = match.groups()
        ur_type = ur_id[3]
        retired = "retired" in description.lower()
        entries[ur_id] = UrEntry(id=ur_id, type=ur_type, retired=retired)
    return entries


def parse_srs(path: Path) -> dict[str, SrEntry]:
    entries: dict[str, SrEntry] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        match = SR_LINE_RE.match(raw_line)
        if not match:
            continue
        sr_id, sr_type = match.groups()
        parent_fragment = sr_id.split(".", 1)[0].removeprefix("SR-")
        entries[sr_id] = SrEntry(id=sr_id, type=sr_type, parent_ur=f"UR-{parent_fragment}")
    return entries


def strip_code_ticks(value: str) -> str:
    return value.strip().strip("`")


def parse_planned_tes(path: Path, allowed_types: set[str] | None = None) -> dict[str, PlannedTeEntry]:
    entries: dict[str, PlannedTeEntry] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line.startswith("| TE-"):
            continue
        parts = [part.strip() for part in line.split("|")[1:-1]]
        if len(parts) < 5:
            continue
        te_id = strip_code_ticks(parts[0])
        sr_id = strip_code_ticks(parts[1])
        test_type = strip_code_ticks(parts[3])
        test_class = strip_code_ticks(parts[4])
        if allowed_types is not None and test_type not in allowed_types:
            continue
        entries[te_id] = PlannedTeEntry(
            id=te_id,
            sr_id=sr_id,
            test_type=test_type,
            test_class=test_class,
        )
    return entries


def parse_implemented_methods(test_src_dir: Path) -> dict[str, list[ImplementedMethod]]:
    implemented: dict[str, list[ImplementedMethod]] = {}
    if not test_src_dir.exists():
        return implemented

    for java_file in sorted(test_src_dir.rglob("*.java")):
        package_name = None
        class_name = None
        pending_te_tags: list[str] = []

        for raw_line in java_file.read_text(encoding="utf-8").splitlines():
            if package_name is None:
                package_match = PACKAGE_RE.match(raw_line)
                if package_match:
                    package_name = package_match.group(1)

            if class_name is None:
                class_match = CLASS_RE.match(raw_line)
                if class_match:
                    simple_class_name = class_match.group(1)
                    class_name = (
                        f"{package_name}.{simple_class_name}"
                        if package_name
                        else simple_class_name
                    )

            for tag_match in TAG_RE.finditer(raw_line):
                tag = tag_match.group(1)
                if tag.startswith("TE-"):
                    pending_te_tags.append(tag)

            method_match = VOID_METHOD_RE.search(raw_line)
            if method_match and pending_te_tags and class_name:
                method_name = method_match.group(1)
                for te_id in pending_te_tags:
                    implemented.setdefault(te_id, []).append(
                        ImplementedMethod(
                            te_id=te_id,
                            class_name=class_name,
                            method_name=method_name,
                            source_file=str(java_file),
                        )
                    )
                pending_te_tags = []
                continue

            stripped = raw_line.strip()
            if not stripped:
                continue
            if stripped.startswith("@") or stripped.startswith("//") or stripped.startswith("*"):
                continue
            if pending_te_tags and not stripped.startswith("/*"):
                pending_te_tags = []

    return implemented


def worst_status(statuses: Iterable[str]) -> str:
    order = {"error": 4, "failure": 3, "skipped": 2, "pass": 1}
    resolved = "pass"
    for status in statuses:
        if order.get(status, 0) > order.get(resolved, 0):
            resolved = status
    return resolved


def parse_executed_methods(report_directories: list[Path]) -> tuple[dict[tuple[str, str], ExecutedMethod], list[str]]:
    executed: dict[tuple[str, str], ExecutedMethod] = {}
    discovered_reports: list[str] = []

    for report_dir in report_directories:
        if not report_dir.exists():
            continue
        for report_file in sorted(report_dir.glob("TEST-*.xml")):
            discovered_reports.append(str(report_file))
            tree = ElementTree.parse(report_file)
            root = tree.getroot()

            for testcase in root.findall("testcase"):
                class_name = testcase.get("classname", "")
                method_name = testcase.get("name", "")
                statuses = []
                if testcase.find("error") is not None:
                    statuses.append("error")
                if testcase.find("failure") is not None:
                    statuses.append("failure")
                if testcase.find("skipped") is not None:
                    statuses.append("skipped")
                status = worst_status(statuses or ["pass"])
                key = (class_name, method_name)

                existing = executed.get(key)
                if existing is None:
                    executed[key] = ExecutedMethod(
                        class_name=class_name,
                        method_name=method_name,
                        status=status,
                        report_file=str(report_file),
                    )
                else:
                    merged_status = worst_status([existing.status, status])
                    executed[key] = ExecutedMethod(
                        class_name=class_name,
                        method_name=method_name,
                        status=merged_status,
                        report_file=existing.report_file,
                    )

    return executed, discovered_reports


def required_urs(urs: dict[str, UrEntry], rule_profile: str) -> list[str]:
    if rule_profile == "strict":
        return sorted(ur_id for ur_id, entry in urs.items() if not entry.retired)
    return sorted(
        ur_id
        for ur_id, entry in urs.items()
        if not entry.retired and entry.type in {"F", "Q"}
    )


def required_srs(srs: dict[str, SrEntry], rule_profile: str) -> list[str]:
    if rule_profile == "strict":
        return sorted(srs)
    return sorted(sr_id for sr_id, entry in srs.items() if entry.type in {"F", "Q"})


def execution_summary(
    te_id: str,
    implemented: dict[str, list[ImplementedMethod]],
    executed: dict[tuple[str, str], ExecutedMethod],
) -> dict[str, object]:
    methods = implemented.get(te_id, [])
    method_results = []
    statuses: list[str] = []

    for method in methods:
        result = executed.get(method.key)
        status = result.status if result is not None else "not_run"
        statuses.append(status)
        method_results.append(
            {
                "class_name": method.class_name,
                "method_name": method.method_name,
                "status": status,
                "source_file": method.source_file,
            }
        )

    if not methods:
        overall_status = "not_implemented"
    elif "error" in statuses or "failure" in statuses:
        overall_status = "failed"
    elif "skipped" in statuses:
        overall_status = "skipped"
    elif "not_run" in statuses:
        overall_status = "not_run"
    else:
        overall_status = "passed"

    return {
        "overall_status": overall_status,
        "implemented_methods": len(methods),
        "executed_methods": sum(1 for status in statuses if status != "not_run"),
        "method_results": method_results,
    }


def category(title: str, items: list[str]) -> dict[str, object]:
    return {
        "title": title,
        "count": len(items),
        "items": items,
    }


def execution_detail_lines(
    te_ids: list[str],
    execution_by_te: dict[str, dict[str, object]],
) -> dict[str, list[str]]:
    details: dict[str, list[str]] = {}
    for te_id in te_ids:
        summary = execution_by_te[te_id]
        lines = []
        for method in summary["method_results"]:
            lines.append(
                f"{method['class_name']}#{method['method_name']} [{method['status']}]"
            )
        details[te_id] = lines
    return details


def build_report(args: argparse.Namespace) -> dict[str, object]:
    root = args.root.resolve()
    urs = parse_urs(root / "docs/requirements-ur.md")
    srs = parse_srs(root / "docs/requirements-sr.md")
    all_planned_tes = parse_planned_tes(root / "spec/test-plan.md")
    selected_types = selected_test_types(args.scope)
    planned_tes = {
        te_id: entry for te_id, entry in all_planned_tes.items() if entry.test_type in selected_types
    }
    implemented = parse_implemented_methods((root / args.test_src).resolve())
    report_directories = report_dirs(root, args.reports_dir)
    executed, discovered_reports = parse_executed_methods(report_directories)

    ur_to_srs: dict[str, set[str]] = {}
    for sr in srs.values():
        ur_to_srs.setdefault(sr.parent_ur, set()).add(sr.id)

    sr_to_tes: dict[str, set[str]] = {}
    for te in planned_tes.values():
        sr_to_tes.setdefault(te.sr_id, set()).add(te.id)

    sr_to_nonselected_tes: dict[str, set[str]] = {}
    for te in all_planned_tes.values():
        if te.test_type in selected_types:
            continue
        sr_to_nonselected_tes.setdefault(te.sr_id, set()).add(te.id)

    required_ur_ids = required_urs(urs, args.rule_profile)
    required_sr_ids = required_srs(srs, args.rule_profile)

    missing_sr_for_ur = sorted(ur_id for ur_id in required_ur_ids if not ur_to_srs.get(ur_id))
    missing_te_for_sr = sorted(
        sr_id
        for sr_id in required_sr_ids
        if not sr_to_tes.get(sr_id) and not sr_to_nonselected_tes.get(sr_id)
    )

    planned_te_ids = sorted(planned_tes)
    implemented_te_ids = sorted(implemented)
    planned_but_not_implemented = sorted(te_id for te_id in planned_te_ids if te_id not in implemented)
    implemented_but_not_planned = sorted(te_id for te_id in implemented_te_ids if te_id not in planned_tes)

    execution_by_te = {
        te_id: execution_summary(te_id, implemented, executed)
        for te_id in planned_te_ids
    }

    te_not_run = sorted(
        te_id for te_id, summary in execution_by_te.items() if summary["overall_status"] == "not_run"
    )
    te_failed = sorted(
        te_id for te_id, summary in execution_by_te.items() if summary["overall_status"] == "failed"
    )
    te_skipped = sorted(
        te_id for te_id, summary in execution_by_te.items() if summary["overall_status"] == "skipped"
    )
    te_passed = sorted(
        te_id for te_id, summary in execution_by_te.items() if summary["overall_status"] == "passed"
    )

    planned_te_type_counts = Counter(entry.test_type for entry in planned_tes.values())

    structural_categories = [
        category("UR without SR", missing_sr_for_ur),
        category("SR without TE", missing_te_for_sr),
        category("Planned TE without implementation", planned_but_not_implemented),
        category("Implemented TE not present in plan", implemented_but_not_planned),
    ]

    execution_categories = []
    execution_reports_missing = False
    if not args.no_execution:
        execution_reports_missing = not discovered_reports
        execution_categories = [
            category("Planned TE not executed", te_not_run),
            category("Planned TE has failing test methods", te_failed),
            category("Planned TE has skipped test methods", te_skipped),
        ]

    findings: list[str] = []
    for entry in structural_categories:
        findings.extend(f"{entry['title']}: {item}" for item in entry["items"])
    if not args.no_execution:
        if execution_reports_missing:
            findings.append(
                "Execution reports not found under: "
                + ", ".join(str(path) for path in report_directories)
            )
        for entry in execution_categories:
            findings.extend(f"{entry['title']}: {item}" for item in entry["items"])

    ok = not findings

    return {
        "ok": ok,
        "scope": args.scope,
        "rule_profile": args.rule_profile,
        "execution_checked": not args.no_execution,
        "summary": {
            "required_ur_count": len(required_ur_ids),
            "required_sr_count": len(required_sr_ids),
            "planned_te_count": len(planned_te_ids),
            "implemented_te_count": len(implemented_te_ids),
            "planned_te_type_counts": dict(sorted(planned_te_type_counts.items())),
            "passed_te_count": len(te_passed),
            "failed_te_count": len(te_failed),
            "skipped_te_count": len(te_skipped),
            "not_run_te_count": len(te_not_run),
        },
        "missing_sr_for_ur": missing_sr_for_ur,
        "missing_te_for_sr": missing_te_for_sr,
        "planned_but_not_implemented": planned_but_not_implemented,
        "implemented_but_not_planned": implemented_but_not_planned,
        "report_directories": [str(path) for path in report_directories],
        "discovered_reports": discovered_reports,
        "execution_reports_missing": execution_reports_missing,
        "structural_categories": structural_categories,
        "execution_categories": execution_categories,
        "execution_method_details": {
            "failed": execution_detail_lines(te_failed, execution_by_te),
            "skipped": execution_detail_lines(te_skipped, execution_by_te),
            "not_run": execution_detail_lines(te_not_run, execution_by_te),
        },
        "execution_by_te": execution_by_te,
        "findings": findings,
        "parsed": {
            "urs": [asdict(entry) for entry in urs.values()],
            "srs": [asdict(entry) for entry in srs.values()],
            "planned_tes": [asdict(entry) for entry in planned_tes.values()],
            "all_planned_tes": [asdict(entry) for entry in all_planned_tes.values()],
        },
    }


def print_section(
    title: str,
    items: list[str],
    max_items: int,
    details: dict[str, list[str]] | None = None,
) -> None:
    print(title)
    print(f"  count: {len(items)}")
    if not items:
        print("  none")
        return

    for item in items[:max_items]:
        print(f"  - {item}")
        if details and item in details:
            for detail in details[item]:
                print(f"    {detail}")

    remaining = len(items) - min(len(items), max_items)
    if remaining > 0:
        print(f"  ... {remaining} more")


def print_human_report(report: dict[str, object]) -> None:
    summary = report["summary"]
    print("Traceability check")
    print(f"  scope: {report['scope']}")
    print(f"  rule profile: {report['rule_profile']}")
    print(f"  execution checked: {report['execution_checked']}")
    print()
    print("Summary")
    print(f"  required URs: {summary['required_ur_count']}")
    print(f"  required SRs: {summary['required_sr_count']}")
    print(f"  planned TEs: {summary['planned_te_count']}")
    print(f"  implemented TEs: {summary['implemented_te_count']}")
    planned_te_type_counts = summary["planned_te_type_counts"]
    if planned_te_type_counts:
        type_counts = ", ".join(
            f"{test_type}={count}" for test_type, count in planned_te_type_counts.items()
        )
        print(f"  planned TE types: {type_counts}")
    if report["execution_checked"]:
        print(f"  passed TEs: {summary['passed_te_count']}")
        print(f"  failed TEs: {summary['failed_te_count']}")
        print(f"  skipped TEs: {summary['skipped_te_count']}")
        print(f"  not run TEs: {summary['not_run_te_count']}")
    print()

    print("Structural coverage")
    for entry in report["structural_categories"]:
        print_section(entry["title"], entry["items"], report["max_items"])
        print()

    if report["execution_checked"]:
        print("Execution coverage")
        if report["execution_reports_missing"]:
            print("Execution reports")
            print("  missing")
            print()
        detail_map = report["execution_method_details"]
        for entry in report["execution_categories"]:
            detail_key = None
            if entry["title"] == "Planned TE has failing test methods":
                detail_key = "failed"
            elif entry["title"] == "Planned TE has skipped test methods":
                detail_key = "skipped"
            elif entry["title"] == "Planned TE not executed":
                detail_key = "not_run"
            details = detail_map[detail_key] if report["show_methods"] and detail_key else None
            print_section(entry["title"], entry["items"], report["max_items"], details)
            print()

    print("Result")
    print(f"  {'ok' if report['ok'] else 'failed'}")


def main() -> int:
    args = parse_args()
    report = build_report(args)
    report["max_items"] = args.max_items
    report["show_methods"] = args.show_methods

    if args.json:
        print(json.dumps(report, indent=2, sort_keys=True))
    else:
        print_human_report(report)

    return 0 if report["ok"] else 1


if __name__ == "__main__":
    sys.exit(main())
