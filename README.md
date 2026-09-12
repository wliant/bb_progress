# bb-progress · 宝宝成长记录

Personal baby development monitoring app for a single baby. Bilingual (简体中文 / English, defaults to Chinese), Singapore Time everywhere.

**Features**: baby profile (with photo) · growth records with WHO percentile charts · CDC developmental milestone checklist (bilingual) · quick daily care logging (feeding / sleep / diaper).

## Stack

| Project | Tech |
|---|---|
| `app/` | Java 21, Spring Boot 4, PostgreSQL, Flyway, Gradle (Kotlin DSL) |
| `web/` | React 19, Vite, TypeScript, Tailwind CSS, TanStack Query, Recharts, react-i18next |
| `e2e/` | Playwright (desktop + mobile viewports) |

## Run it

```bash
cp .env.example .env          # adjust APP_PORT etc. as needed
docker compose up -d --build
open http://localhost:8090    # or your APP_PORT
```

### Multiple instances on one machine

Each instance needs its own env file with a unique `COMPOSE_PROJECT_NAME` and `APP_PORT`:

```bash
docker compose --env-file .env.instance2 up -d --build
```

Container, network, and volume names are derived from `COMPOSE_PROJECT_NAME`, so instances are fully isolated (including their databases and photo storage).

## Develop natively (fast iteration)

```bash
# containerized Postgres published on ${DB_PORT:-5433}
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d db

cd app && ./gradlew bootRun    # API on :8080
cd web && npm run dev          # UI on :5173, /api proxied to :8080
```

## Tests

```bash
cd app && ./gradlew test              # backend unit tests
cd app && ./gradlew integrationTest   # backend integration tests (needs Docker)
cd web && npm test                    # frontend tests (Vitest)
cd e2e && npm test                    # e2e (stack must be up; E2E_BASE_URL to override port)
```

## Data sources & attribution

- Growth percentile curves are computed from the **WHO Child Growth Standards** LMS tables (weight-for-age, length/height-for-age, head-circumference-for-age; birth–36 months), © World Health Organization, licensed CC BY-NC-SA 3.0 IGO. Source: <https://www.who.int/tools/child-growth-standards>. Note: WHO uses length (lying) below 24 months and height (standing) from 24 months, which produces a small step at 24 months in the published tables.
- Milestone checklist is based on the CDC **"Learn the Signs. Act Early."** program milestones (2022 revision), ages 2–36 months: <https://www.cdc.gov/ncbddd/actearly/milestones/>. Simplified Chinese titles follow CDC's Chinese-language checklists.

This app is a personal record-keeping tool, not medical advice. Discuss your child's growth and development with your pediatrician.
