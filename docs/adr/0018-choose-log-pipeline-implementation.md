# 0018. Choose log-pipeline implementation

## Status

- Proposed, 2026-06-01

## Context

This decision answers: what concrete implementation backs the `log-pipeline` service named in architecture §7, and what query surface does it expose to operators executing ST-5?

Architecture §7 commits to a single-VPS Docker Compose deployment (UR-00-C12) containing a `log-pipeline` service whose responsibilities are: capturing application log entries from `app` (and supporting services), enforcing fixed 3-year retention at the pipeline boundary (UR-00-C15), preserving correlation identifiers (UR-00-C16), and serving as the read surface for ST-5 (UR-01-F11). The application's responsibility ends at emitting structured log entries (UR-00-C14); transport, indexing, retention enforcement, and operator query mechanics are properties of the pipeline implementation.

The Monitoring stack chosen for metrics is Prometheus + AlertManager + Grafana (architecture §5, §7; SR-01-F10.F03). Grafana is therefore already part of the operator's tooling.

Constraints from the deployment context:

- Single-VPS footprint — log-pipeline containers must coexist with `caddy`, `app`, `db`, `redis`, and `backup` on commodity VPS hardware. Multi-GB RAM consumers (Elasticsearch, MongoDB) crowd out the application.
- Operator skill surface — every new query language or admin UI adds to what an operator must learn. Reusing Grafana keeps the surface narrow.
- Application emits already-structured JSON log entries with correlation labels — heavy parsing or transformation upstream of storage is not needed.
- Retention is policy-driven (fixed 3 years), not query-driven; a storage engine with built-in retention configuration is sufficient.

Candidate implementations:

- **Grafana Loki + Promtail.** Promtail tails container log files and ships labelled streams to Loki. Loki indexes only labels, stores log content as compressed chunks, and exposes the LogQL query language. Grafana queries Loki natively via the same Explore / dashboard surface used for Prometheus metrics. Retention is enforced through `limits_config.retention_period` in Loki configuration.

- **Vector + Loki.** Vector replaces Promtail as the collector with richer transformation capabilities. Storage and query surface unchanged. The transformation capability is unused because the application already emits structured JSON; the extra component costs operational complexity without adding value at the current scope.

- **Fluentd + Elasticsearch + Kibana.** Mature stack; Elasticsearch provides full-text indexing. Elasticsearch's memory footprint (multi-GB heap baseline) is excessive for single-VPS coexistence with the application database. Full-text indexing is not a requirement for trawhile's log volume.

- **Graylog.** Requires MongoDB and Elasticsearch alongside the Graylog server itself. Same footprint problem as Fluentd + Elasticsearch + Kibana, plus a second NoSQL store. Strong UI for log search but adds a second admin surface separate from Grafana.

- **Plain rotating files + `docker logs`.** No service component; the operator reads logs via shell. Fails ST-5's spirit (UR-01-F11 implies a usable access surface) and pushes retention enforcement into Docker daemon configuration that is brittle and hostile to audit.

## Decision

Adopt **Grafana Loki + Promtail** as the `log-pipeline` implementation.

The Docker Compose `log-pipeline` service is realised as two cooperating containers: `loki` (storage and query API) and `promtail` (per-host log tailer). Promtail tails `app` and supporting service log files via the Docker logging driver's file output, attaches a fixed label set including `service`, `container`, `trace_id`, and `request_id`, and ships streams to Loki. Loki persists chunks to a volume mounted on the host and enforces 3-year retention through its `limits_config.retention_period` setting.

Operator log access for ST-5 is provided by Grafana Explore configured with Loki as a datasource. Grafana already runs as part of the external Monitoring stack (architecture §5, §7); no separate log-viewer admin surface is introduced. The operator's query language is LogQL.

## Consequences

The Docker Compose `log-pipeline` entry expands to two containers (`loki`, `promtail`) sharing the `log-pipeline` role from the deployment table. The shared role identity is preserved in architecture §7; the internal split is an implementation detail of the chosen stack.

The operator query mechanism for ST-5 is fixed: LogQL via Grafana Explore. SR-01-F11.C01 can name this surface concretely rather than punting to a future decision.

Retention enforcement moves to Loki configuration. The retention period is set to 3 years in `deploy/loki/loki-config.yml` (or equivalent path under the same operator-tooling root chosen in SR-01-F10.F03). The application code does not implement log retention.

Correlation identifiers (UR-00-C16) flow end-to-end without parsing logic in the pipeline because the application emits them as fields in structured JSON; Promtail's pipeline stage extracts them as Loki labels for cheap filtering.

Adding full-text search over log message bodies is *not* supported at the chosen scale; LogQL filters on labelled streams. If future requirements demand full-text indexing, a downstream re-evaluation is required, but at the trawhile scope (single small-company instance) label-based filtering is sufficient.

The metrics query surface (PromQL via Grafana) and the log query surface (LogQL via Grafana) share the same Grafana instance. Operators learn one tool; dashboards can interleave metric and log panels.
