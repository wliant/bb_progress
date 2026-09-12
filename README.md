# bb-progress · 宝宝成长记录

Personal baby development monitoring app for a single baby. Bilingual (简体中文 / English, defaults to Chinese), Singapore Time everywhere.

Personal, self-hosted, and not medical advice — see [Data sources & attribution](#data-sources--attribution).

**Features**: baby profile (with photo, birth details and gestational age) · growth records with WHO percentile charts · CDC developmental milestone checklist (bilingual) · quick daily care logging (feeding / sleep / diaper) · photo, video and voice attachments on every entry and milestone (voice can be recorded in the app) · a media gallery across everything.

## Stack

| Project | Tech |
|---|---|
| `app/` | Java 21, Spring Boot 4, PostgreSQL, Flyway, Gradle (Kotlin DSL) |
| `web/` | React 19, Vite, TypeScript, Tailwind CSS, TanStack Query, Recharts, react-i18next |
| `e2e/` | Playwright (desktop + mobile viewports) |
| storage | Media in S3-compatible object storage — MinIO in the stack by default, or real AWS S3 |

## Run it

```bash
cp .env.example .env          # adjust APP_PORT etc. as needed

# 1. infrastructure — database and object store, started once and left running
docker compose -f docker-compose.infra.yml up -d --wait

# 2. application — rebuild and recreate this as often as you like
docker compose up -d --build --force-recreate

open http://localhost:8090    # or your APP_PORT
```

The two stacks are separate compose projects (`bbprogress-infra` and `bbprogress`) so the
application can be rebuilt without bouncing the database or the object store. They meet on a shared
network named after `INSTANCE`.

`--force-recreate` matters after a code change: `--build` alone rebuilds the image but can leave
the previous container running. `--wait` on the infrastructure matters because `depends_on` cannot
reach across compose projects — without it the app simply restarts until the database answers.

To start over from scratch (clears the profile, all records and photos):

```bash
curl -X DELETE http://localhost:8090/api/baby
```

### Multiple instances on one machine

Each instance needs its own env file with a unique `INSTANCE` and `APP_PORT`:

```bash
docker compose --env-file .env.instance2 -f docker-compose.infra.yml up -d --wait
docker compose --env-file .env.instance2 up -d --build --force-recreate
```

Project, network and volume names all derive from `INSTANCE`, so instances are fully isolated —
separate databases, separate object storage, separate networks.

Note it is `INSTANCE`, not `COMPOSE_PROJECT_NAME`: the latter is an environment variable that
overrides the `name:` in each compose file, which would merge the two stacks back into one project.

## Media storage

Photos, videos and voice notes live in S3-compatible object storage; the database holds only the
object key. Photos are re-encoded to under 1 MB on upload; video and voice are stored exactly as
recorded, because the app has no transcoder — which also means only photos get thumbnails.

Voice notes can be recorded directly in the app. Browsers only expose the microphone in a secure
context, so recording works when the app is opened on `localhost` or over https; reached over plain
http on a LAN address (for example from a phone at `http://192.168.x.x:8090`) the recorder explains
that instead of offering a button that cannot work. Picking an existing audio file works anywhere. The
compose stack runs **MinIO**, so it needs no AWS account and works offline, and each instance gets
its own bucket and volume.

To use **real AWS S3** instead, no code changes are needed — drop the `minio` service from
`docker-compose.infra.yml` and remove the `S3_ENDPOINT` line from the `app` service in
`docker-compose.yml`, then set in `.env`:

```bash
S3_BUCKET=your-bucket
S3_REGION=ap-southeast-1
S3_PATH_STYLE=false
S3_CREATE_BUCKET=false
S3_ACCESS_KEY=...        # or leave both keys blank to use an IAM role / AWS profile
S3_SECRET_KEY=...
```

## Develop natively (fast iteration)

```bash
# Postgres on ${DB_PORT:-5433} and MinIO on ${S3_PORT:-9000}, published to the host
docker compose -f docker-compose.infra.yml -f docker-compose.dev.yml up -d --wait

cd app && ./gradlew bootRun    # API on :8080
cd web && npm run dev          # UI on :5173, /api proxied to :8080
```

## Tests

```bash
cd app && ./gradlew test              # backend unit tests
cd app && ./gradlew integrationTest   # backend integration tests (needs Docker)
cd web && npm test                    # frontend tests (Vitest)
cd e2e && npm run test:isolated       # e2e on a throwaway stack (:8099), torn down afterwards
```

The e2e specs clear records and reset the profile, so run them with `test:isolated` rather than
against the instance holding your data. `cd e2e && npm test` targets whatever is on `E2E_BASE_URL`
(default `:8090`) and **will wipe it**.

## Data sources & attribution

- Size at birth is assessed against the **INTERGROWTH-21st Newborn Size Standards** (Villar et al.,
  *Lancet* 2014; DOI [10.1016/S0140-6736(14)60932-6](https://doi.org/10.1016/S0140-6736(14)60932-6)),
  using the project's published skew-t parameters for 33+0–42+6 weeks. Source:
  <https://intergrowth21.com/tools-resources/newborn-size>. Below 33+0 weeks the project publishes a
  separate Very Preterm standard which is **not** bundled here; those gestational ages are shown
  without a centile. The implementation reproduces the project's published centile tables to within
  15 g (weight) and 0.055 cm (lengths) across the whole range — see `NewbornSizeServiceTest`.
- Growth percentile curves are computed from the **WHO Child Growth Standards** LMS tables (weight-for-age, length/height-for-age, head-circumference-for-age; birth–36 months), © World Health Organization, licensed CC BY-NC-SA 3.0 IGO. Source: <https://www.who.int/tools/child-growth-standards>. Note: WHO uses length (lying) below 24 months and height (standing) from 24 months, which produces a small step at 24 months in the published tables.
- Milestone checklist is based on the CDC **"Learn the Signs. Act Early."** program milestones (2022 revision), ages 2–36 months: <https://www.cdc.gov/ncbddd/actearly/milestones/>. Simplified Chinese titles follow CDC's Chinese-language checklists.

This app is a personal record-keeping tool, not medical advice. Discuss your child's growth and development with your pediatrician.

## Licence

This project's own code is MIT licensed — see [LICENSE](LICENSE).

The bundled reference data is **not** covered by that licence and keeps its publishers' terms; the
WHO growth standards in particular are CC BY-NC-SA 3.0 IGO, which forbids commercial use. See
[NOTICE](NOTICE) for the full breakdown of what is bundled and under what terms.
