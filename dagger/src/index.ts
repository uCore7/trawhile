/**
 * trawhile devsecops pipeline as a Dagger module.
 *
 * Local invocation:
 *   dagger call build --source=.
 *   dagger call unit-test --source=.
 *   dagger call ci --source=.
 *
 * GitHub Actions invokes the same module — see .github/workflows/ci.yml.
 *
 * Stages compose left to right: `ci` runs build → unitTest → traceability →
 * sbom → secretsScan and returns a summary string. Add stages by writing a
 * new `@func()` method and wiring it into `ci`.
 */

import { dag, object, func, Directory, Container, Secret } from "@dagger.io/dagger";

// Container images pinned to the platform tier committed in architecture §3.
// Bumping these is a deliberate change reviewed in the same PR as any
// behaviour relying on the new version.
const JAVA_IMAGE = "maven:3.9-eclipse-temurin-25";
const PYTHON_IMAGE = "python:3.12-alpine";
const GITLEAKS_IMAGE = "zricethezav/gitleaks:latest";

@object()
class Trawhile {

  /**
   * Build the backend: compile + package, skip tests, skip frontend profile.
   * The Maven local-repo cache is mounted so successive builds are fast.
   */
  @func()
  build(source: Directory): Container {
    return this.mavenContainer(source).withExec([
      "mvn", "-B", "-ntp", "package", "-DskipTests",
    ]);
  }

  /**
   * Run the unit-test subset (fast, no PostgreSQL).
   * Phase 7 of the dev process expects unit tests to be the first feedback loop.
   */
  @func()
  unitTest(source: Directory): Container {
    return this.mavenContainer(source).withExec([
      "mvn", "-B", "-ntp", "test",
    ]);
  }

  /**
   * Full verify: SpotBugs+FindSecBugs, OWASP dep-check, CycloneDX SBOM, and
   * any tests Maven knows how to run inside the build container.
   *
   * Requires NVD API key for the OWASP check; pass it as a Dagger secret.
   * If `nvdApiKey` is omitted the OWASP check still runs but may be rate-limited.
   *
   * NOTE on integration tests: Phase 7 tests that need a live PostgreSQL
   * cannot use Testcontainers from inside this container without exposing
   * the Docker socket. The right pattern in Dagger is to declare PostgreSQL
   * as a Dagger service and bind it: replace Testcontainers in `BaseIT` with
   * a Spring datasource that reads its URL from an env var, then add a
   * `withServiceBinding("db", postgresService)` to this container. See
   * https://docs.dagger.io/cookbook/service-binding for the pattern.
   */
  @func()
  async verify(source: Directory, nvdApiKey?: Secret): Promise<string> {
    let container = this.mavenContainer(source);
    if (nvdApiKey) {
      // OWASP dependency-check reads the key from the Maven property `nvdApiKey`,
      // not from the NVD_API_KEY env var directly. We put the secret in the
      // container env and let shell expand it into a -D argument, so the
      // secret never appears in the Dagger API call or process listing of the host.
      container = container.withSecretVariable("NVD_API_KEY", nvdApiKey);
      return await container.withExec([
        "sh", "-c",
        'mvn -B -ntp verify -DnvdApiKey="$NVD_API_KEY"',
      ]).stdout();
    }
    return await container.withExec([
      "mvn", "-B", "-ntp", "verify",
    ]).stdout();
  }

  /**
   * Run the UR → SR → TE traceability check.
   *
   * During Phase 5/6 the planned-implementation gap is the expected state
   * (specs written, tests not yet implemented). The pipeline calls the
   * checker with --allow-planned-without-impl so the structural integrity
   * (UR→SR, SR→planned TE) is enforced while the not-yet-written tests
   * are reported but do not fail the build. Drop the flag in Phase 7 once
   * tests start landing, then drop it entirely once coverage is complete.
   */
  @func()
  async traceability(source: Directory): Promise<string> {
    return await dag.container()
      .from(PYTHON_IMAGE)
      .withDirectory("/repo", source)
      .withWorkdir("/repo")
      .withExec([
        "python", "scripts/check-traceability.py",
        "--no-execution",
        "--allow-planned-without-impl",
      ])
      .stdout();
  }

  /**
   * Produce the CycloneDX SBOM artifact.
   * Uses the cyclonedx-maven-plugin already configured in pom.xml.
   */
  @func()
  sbom(source: Directory): Container {
    return this.mavenContainer(source).withExec([
      "mvn", "-B", "-ntp", "cyclonedx:makeBom",
    ]);
  }

  /**
   * Scan the working tree for committed secrets using gitleaks.
   * Exits non-zero if any finding is reported.
   */
  @func()
  async secretsScan(source: Directory): Promise<string> {
    return await dag.container()
      .from(GITLEAKS_IMAGE)
      .withDirectory("/repo", source)
      .withWorkdir("/repo")
      // Dagger's withExec runs argv[0] directly without honouring the image's
      // ENTRYPOINT, so prepend the binary name explicitly.
      .withExec(["gitleaks", "detect", "--source", ".", "--verbose", "--no-banner"])
      .stdout();
  }

  /**
   * Compose the full pipeline.
   *
   * Each step short-circuits the rest on failure (Dagger propagates exec
   * errors as exceptions). The returned string is a status summary for the
   * caller; failure surfaces as a non-zero exit when invoked from `dagger call`.
   */
  @func()
  async ci(source: Directory, nvdApiKey?: Secret): Promise<string> {
    // Build first; subsequent stages reuse cached layers from this step.
    await this.build(source).sync();
    await this.unitTest(source).sync();
    await this.traceability(source);
    await this.verify(source, nvdApiKey);
    await this.sbom(source).sync();
    await this.secretsScan(source);
    return "trawhile CI pipeline succeeded";
  }

  /**
   * Maven container preconfigured with the source tree mounted and a persistent
   * local-repository cache. Reused by every Maven-running stage so the Maven
   * dependency download happens at most once per cache lifetime.
   */
  private mavenContainer(source: Directory): Container {
    const mavenCache = dag.cacheVolume("trawhile-maven-repo");
    return dag.container()
      .from(JAVA_IMAGE)
      .withMountedCache("/root/.m2/repository", mavenCache)
      .withDirectory("/src", source)
      .withWorkdir("/src");
  }
}
