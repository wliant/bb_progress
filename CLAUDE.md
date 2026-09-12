# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Baby development monitoring app for a single baby, personal use.

## Repository structure

- `app/` — API / backend
- `web/` — frontend
- `e2e/` — end-to-end tests (separate project from app/web)

## Non-negotiable engineering practices

- **Dual language**: every user-facing feature must support both Chinese and English (i18n from the start, not retrofitted).
- **Timezone**: use SGT (Asia/Singapore, UTC+8) by default everywhere — storage, display, and defaults.
- **Deployment**: docker compose based development, single compose file. Externalize key properties (ports, credentials, instance names) into a `.env` file. Multiple instances of the compose stack must be able to run on the same machine — so no hardcoded ports, container names, volume names, or network names; derive them from `.env` (e.g. `COMPOSE_PROJECT_NAME` + port variables).
- **Testing**: full test pyramid. Unit tests and integration tests live inside `app/` and `web/` respectively; e2e tests are a separate project.
- **Spec-driven development**: write a spec before implementing a feature. Specs live in `specs/`, one file per feature (e.g. `specs/<feature-name>.md`), covering the requirements, behavior, and acceptance criteria. Implementation and tests follow the spec; if the design changes during implementation, update the spec to match.

## Development workflow

Before starting a feature: write its spec in `specs/` and get it confirmed.

After completing each feature:
1. Ensure unit/integration tests are created for the feature.
2. Start the docker compose stack in local mode.
3. Verify the feature in the browser using the Playwright MCP.

## Tech stack

- `app/`: Java 21, Spring Boot 4 (note: Jackson 3 — `tools.jackson.*` imports, not `com.fasterxml.*`), PostgreSQL, Flyway, Gradle Kotlin DSL. Gradle auto-provisions JDK 21 via the foojay toolchain resolver.
- `web/`: React 19 + Vite + TypeScript, Tailwind CSS v4, TanStack Query, react-i18next (default `zh-CN`), Recharts, Vitest + Testing Library + MSW.
- `e2e/`: Playwright, desktop + mobile (Pixel 7) projects, runs against the compose stack.
- WHO growth-standard LMS data lives in `app/src/main/resources/who/` (generated from official WHO tables — do not hand-edit); CDC milestone seed data in the Flyway migration `V4__seed_milestone_definitions.sql`.
- INTERGROWTH-21st newborn size parameters live in `app/src/main/resources/ig21/` (skew-t mu/sigma/nu/tau
  per gestational day, from the project's published workbooks). Two standards are deliberately kept
  apart: **WHO** answers *growth since birth* (x-axis = age in months) and **INTERGROWTH-21st** answers
  *size at birth* (x-axis = gestational age). Never merge them onto one chart. The bundled newborn range
  is 33+0–42+6 weeks; the Very Preterm standard below that is a separate publication and is not included.

## Commands

```bash
# app/ (run from app/)
./gradlew test                    # unit tests (no Docker needed)
./gradlew integrationTest         # integration tests (Testcontainers, needs Docker)
./gradlew test --tests 'com.bb.progress.growth.WhoPercentileServiceTest'   # single test
./gradlew bootRun                 # run on :8080 (needs Postgres, see below)

# web/ (run from web/)
npm run dev                       # dev server :5173, /api proxied to :8080
npm test                          # vitest run
npx vitest run src/pages/HomePage.test.tsx   # single file
npm run typecheck && npm run lint
npm run build

# e2e/ (run from e2e/)
npm run test:isolated             # PREFERRED: throwaway stack on :8099, torn down after
npm run test:isolated tests/growth.spec.ts   # single spec, same isolation
npm test                          # against an already-running stack — DESTRUCTIVE, see below
npx playwright test tests/growth.spec.ts     # single spec

# stack (repo root; needs .env — copy from .env.example)
docker compose up -d --build --force-recreate     # see note below: --force-recreate is required
docker compose --env-file .env.instance2 up -d --build --force-recreate   # second instance
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d db  # Postgres only, published on :5433 for native dev
curl -X DELETE http://localhost:8090/api/baby     # wipe profile + all records (not exposed in the UI)
```

**Never pipe a test or build command into `tail`/`head`** — the pipeline's exit status is the
pager's, so a failing Gradle build reports success. Redirect to a file and check `$?` instead:
`./gradlew test integrationTest > /tmp/t.log 2>&1; echo $?`. Confirm tests actually ran by
reading `app/build/test-results/*/`; a compile error produces no results at all.

**The e2e specs are destructive** — they delete records and `DELETE /api/baby` to test the
first-run state. Never point them at an instance holding real data. `e2e/run-isolated.sh`
(`npm run test:isolated`) brings up a separate `bbprogress-e2e` stack on its own port, runs the
suite there, and tears it down with its volumes, which is what the multi-instance requirement
exists for.

**`docker compose up -d --build` can leave the old container running** even after it rebuilds
the image, so verification silently tests stale code. Always add `--force-recreate`, and sanity
check with `docker compose exec app ls -l /app/app.jar` if behaviour looks unchanged.

## Conventions worth preserving

- **Error contract**: every API error returns `{status, code, message, fieldErrors[]}` with a
  stable `code`. `GlobalExceptionHandler` extends `ResponseEntityExceptionHandler` so Spring's
  own MVC exceptions are rewritten into that shape too — add new codes there, and a matching
  `errors.<CODE>` entry in **both** `web/src/i18n/locales/*.json`.
- **No silent failures**: failed writes surface through the shared `MutationCache` handler in
  `web/src/main.tsx` (a toast via `lib/toast.ts`), so mutations need no per-call `onError`.
  Failed reads render `<ErrorState>` in place — never an empty list.
- **Pages gate on the profile** with `<RequireBaby>`; every write endpoint enforces the same
  precondition server-side.
- Dialogs must derive their subject from the query cache (by id), not hold a snapshot in
  state, or an edit made inside the dialog won't be reflected until it is reopened.
- **Photos live in S3-compatible object storage**, never on the app's filesystem; the database holds
  only the object key (`baby/<uuid>.<ext>`, `care-logs/<uuid>.jpg`, `milestones/<uuid>.jpg`). One
  implementation (`PhotoStorageService` + AWS SDK v2) serves both deployments: a blank `S3_ENDPOINT`
  means real AWS, a URL means MinIO, which is what compose runs. Blank credentials fall back to the
  AWS default chain so IAM roles work. Objects are streamed through the API rather than exposed
  directly, keeping URLs, ETags and the error contract in one place.
- Uploads accept 25 MB (Spring `max-file-size`), nginx allows 32 MB so the app answers oversized
  uploads itself, and nginx's own 413 returns the same coded JSON. Care-log and milestone photos go
  through `storeCompressed` (re-encoded to JPEG ≤1 MB, EXIF orientation applied); the **profile
  photo alone** uses `store` and keeps the original bytes. Tests that upload must use real image
  bytes — the compressor decodes them. `?size=thumb` serves a ≤320 px sibling object, created on
  first request and deleted with the original; the size is part of the ETag, or a cached full image
  would answer a thumbnail request.
- Integration tests run against **real MinIO**, not a mock. It is a singleton `GenericContainer` in
  `TestcontainersConfiguration` rather than a bean, because `PostgreSQLContainer` is also a
  `GenericContainer` and injecting one by type is ambiguous.
- **The photo gallery owns no data**: `/api/photos` is a read-only view over care-log, milestone and
  profile photos. Adding or removing a photo happens on its own entry, so any mutation that touches
  a photo must also invalidate the `['photos']` query.
