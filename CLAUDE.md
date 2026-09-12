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

# e2e/ (run from e2e/; stack must be up)
npm test                          # all specs
npx playwright test tests/growth.spec.ts     # single spec
E2E_BASE_URL=http://localhost:8090 npm test  # custom port

# stack (repo root; needs .env — copy from .env.example)
docker compose up -d --build
docker compose --env-file .env.instance2 up -d   # second instance (unique COMPOSE_PROJECT_NAME + APP_PORT)
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d db  # Postgres only, published on :5433 for native dev
```
