# DevSecOps

This document describes how trawhile is built, verified, and delivered: the continuous integration pipeline, the security and quality gates that run within it, and the delivery path to production.

**Scope.** This document owns the standing build, verification, and delivery machinery. It does not own:

- the development *process* — phases, sequencing, the agentic coding workflow, and ID/traceability conventions are in [process.md](process.md);
- the deployment *topology* — what runs where in production is in [architecture.md](architecture.md), chapter 7; this document covers only how a build reaches that topology;
- runtime security controls — CSRF protection, security headers, session management, and rate limiting are architectural concerns and are recorded as ADRs under [docs/adr/](adr/).

## CI/CD pipeline

Continuous integration and deployment run on GitHub Actions. The repository is hosted on GitHub and the production target is a single VPS running Docker Compose, so GitHub Actions keeps the pipeline close to the source and compatible with the deployment model.

The pipeline runs on every push and pull request:

- Maven verification and backend tests
- SpotBugs with Find Security Bugs
- OWASP Dependency Check
- npm audit and the frontend production build
- CycloneDX SBOM generation for Maven and npm
- requirement-to-test traceability checks
- Docker image build

## Security and quality gates

The build fails on HIGH or CRITICAL findings. Security analysis is non-optional for every change, and runs in the pipeline at no runtime cost.

**SpotBugs with Find Security Bugs.** The backend uses explicit SQL and, transitionally, Spring Data JDBC; neither provides the structural SQL-construction guarantees of a fully generated DSL. SpotBugs with the Find Security Bugs plugin runs as a mandatory gate to surface common injection and security mistakes before merge. It is a net beneath careful SQL design, parameter binding, code review, and PostgreSQL-backed authorization tests — not a replacement for them.

**Supply-chain gates.** OWASP Dependency Check and npm audit cover known vulnerabilities in third-party dependencies. CycloneDX SBOM generation records the dependency inventory for Maven and npm.

**Traceability check.** The pipeline verifies requirement-to-test traceability; the matrix itself is in [spec/test-plan.md](../spec/test-plan.md).

## Delivery

Deployment is from the `main` branch: the pipeline pushes the built Docker image and updates the VPS over SSH using Docker Compose. This delivers to the topology described in [architecture.md](architecture.md), chapter 7. Delivery depends on GitHub Actions availability and on securely managed deployment secrets.
