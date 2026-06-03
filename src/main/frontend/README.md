# trawhile frontend

Angular 21.x SPA. Stack pinned in [docs/architecture.md §3](../../../docs/architecture.md):

- **Framework**: Angular 21.x (standalone components, zoneless-ready)
- **UI**: PrimeNG 21.x with Chart.js-backed chart components
- **Styling**: Tailwind CSS 4.x (CSS-first config; see `src/styles.scss`)
- **State**: NgRx 21.x (per ADR 0013)
- **i18n**: ngx-translate 16.x; dialects en-GB (default), de-DE, fr-FR, es-ES
- **PDF export**: jsPDF + jsPDF-AutoTable (for [SR-04-F06.F01](../../../docs/requirements-sr.md))

## Development

```bash
# From the frontend directory:
npm ci
npm start              # Angular dev server with hot reload
npm run build          # Production build → dist/trawhile/
```

> The test runner (planned: Vitest) is added in Phase 7 alongside the first
> test tasks. The Phase 5 baseline ships no test runner — Karma + Jasmine
> were deliberately omitted because their transitive dependencies still drag
> in deprecated packages (`glob@7`, `inflight`, `rimraf@3`, `uuid@8`).

The Maven build wires the frontend production build into the Spring Boot jar
when the `-Pfrontend` profile is supplied (see root `pom.xml`):

```bash
# From the repo root:
./scripts/mvn-local.sh -Pfrontend package
```

Without `-Pfrontend`, the backend builds standalone and Angular is served
separately by `ng serve` during development.

## Layout

```
src/
  index.html
  main.ts              ← bootstrap (standalone)
  styles.scss          ← global stylesheet + Tailwind import
  app/
    app.config.ts      ← providers (router, HTTP, NgRx, ngx-translate)
    app.routes.ts      ← route configuration
    app.ts             ← root component (locale resolution per SR-00-C18.F01)
    app.html
    app.scss
  assets/
    i18n/
      en-GB.json       ← default dialect
      de-DE.json
      fr-FR.json
      es-ES.json
```

## Adding features

Feature folders go under `src/app/features/<feature>/`. Each feature owns its
own NgRx slice (`features/<feature>/state/`), its API service (typed by an
OpenAPI-generated client), its presenter components, and its routes (loaded
via `loadChildren` in `app.routes.ts`).
