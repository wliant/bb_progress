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
- **Deployment**: docker compose based development. Externalize key properties (ports, credentials, instance names) into a `.env` file. Multiple instances of the compose stack must be able to run on the same machine — so no hardcoded ports, container names, volume names, or network names; derive them from `.env` (`INSTANCE` + port variables — *not* `COMPOSE_PROJECT_NAME`, see below).
- **Testing**: full test pyramid. Unit tests and integration tests live inside `app/` and `web/` respectively; e2e tests are a separate project.
- **Migrations, not resets**: the app is live with real data. Every schema change ships as a new
  forward Flyway migration that preserves what is already stored — see
  [Database migrations](#database-migrations--the-app-is-live). Never edit an applied migration and
  never wipe a volume to make a change fit.
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

# stack (repo root; needs .env — copy from .env.example). Two compose projects:
#   docker-compose.infra.yml -> <INSTANCE>-infra : postgres + minio, long-lived
#   docker-compose.yml       -> <INSTANCE>       : app + web, recreated freely
docker compose -f docker-compose.infra.yml up -d --wait   # infrastructure FIRST
docker compose up -d --build --force-recreate             # then the application
docker compose --env-file .env.instance2 -f docker-compose.infra.yml up -d --wait  # 2nd instance
docker compose --env-file .env.instance2 up -d --build --force-recreate
docker compose -f docker-compose.infra.yml -f docker-compose.dev.yml up -d --wait  # publish db/minio for native dev
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

**The stacks are split and `depends_on` does not cross compose projects.** Start
`docker-compose.infra.yml` with `--wait` before the application stack. The app carries
`restart: unless-stopped` so it recovers on its own if infrastructure appears late, and `web`
depends on the app only being *started*, not healthy — otherwise the UI would not come up at all
while the backend waits. Project, network and volume names derive from `INSTANCE` in `.env`; do
not use `COMPOSE_PROJECT_NAME`, which as an environment variable overrides each file's `name:` and
merges the two stacks into one project. Volume names are pinned to the pre-split names so the data
that existed before the split is reused rather than stranded.

**`docker compose up -d --build` can leave the old container running** even after it rebuilds
the image, so verification silently tests stale code. Always add `--force-recreate`, and sanity
check with `docker compose exec app ls -l /app/app.jar` if behaviour looks unchanged.

## Database migrations — the app is live

There is real data in the everyday instance (profile, records, media). Treat the database as
production from here on.

- **Never edit a migration that has been applied.** V1–V9 are applied in the live database;
  changing one is caught by Flyway's checksum validation at startup, so the app simply refuses to
  boot. Fix forward with a new `V<n+1>__*.sql` instead — including for mistakes in a migration
  that already shipped.
- **Never `flyway clean`, never `docker compose -f docker-compose.infra.yml down -v`** on the
  everyday instance — the volumes hold the only copy. `down -v` is for the e2e stack, which is
  what `run-isolated.sh` exists for.
- **Additive by default.** New columns arrive nullable or with a `DEFAULT`; a `NOT NULL` column on
  an existing table needs a default or a backfill in the same migration, or the migration fails on
  a non-empty table. Note this only bites now that tables have rows — the same migration would
  have passed cleanly during scaffolding.
- **A rename is three steps, not one**: add the new column, backfill it from the old one, and drop
  the old one in a *later* migration once no deployed code reads it. V9 is the pattern to copy — it
  created `media`, copied the existing `photo_path` values across, and only then dropped the
  columns.
- **`ddl-auto: validate` is load-bearing**: an entity that disagrees with the migrated schema fails
  startup rather than corrupting data, and every integration test boots against a freshly migrated
  Testcontainers database, so the whole chain is exercised on each run. Keep it that way — do not
  switch it to `update`.
- **SQL migrations do not move stored objects.** Object keys (`baby/<uuid>.<ext>`,
  `care-logs/<uuid>.jpg`, `milestones/<uuid>.jpg`) live in the database while the bytes live in
  S3/MinIO, so any change to key shape or storage layout needs a matching data migration that
  rewrites the objects too — and there is no transactional rollback across the two. Prefer leaving
  existing keys untouched and changing only how new ones are written.
- **Back up before migrating.** A Flyway failure mid-migration leaves the schema partly changed;
  Postgres runs each migration in a transaction, but a backup is the only recovery from a
  migration that succeeds and is wrong.

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
  means real AWS, a URL means MinIO, which is what compose runs. `SPRING_DATASOURCE_URL` and
  `S3_ENDPOINT` both come from `.env` rather than being written into `docker-compose.yml`, so
  repointing at an external database or a real bucket touches no compose file. Blank credentials fall back to the
  AWS default chain so IAM roles work. Objects are streamed through the API rather than exposed
  directly, keeping URLs, ETags and the error contract in one place.
- **Attachments are a `media` table**, many per care-log entry or milestone achievement, with
  exactly one owner each (DB check constraint). Kind is decided from the upload's content type:
  `PHOTO` goes through `storeCompressed` (JPEG ≤1 MB, EXIF orientation applied) while `VIDEO` and
  `AUDIO` use `storeAs` and are kept byte-for-byte — there is no transcoder, so **only photos have
  thumbnails**. The **profile photo** remains a single image on `baby.photo_path`, stored at
  original quality. Tests that upload a photo must use real image bytes — the compressor decodes
  them; video and audio are never decoded so any bytes will do.
- Uploads accept 200 MB (Spring `max-file-size`) for phone video; nginx allows 240 MB so the app
  answers oversized uploads itself, and nginx's own 413 returns the same coded JSON. `?size=thumb`
  serves a ≤320 px sibling object, created on first request and deleted with the original; the size
  is part of the ETag, or a cached full image would answer a thumbnail request.
- Rows cascade with their owner in the database, but **stored objects never do** — every delete path
  must enumerate the media and remove the objects explicitly before the rows go.
- **Voice can be recorded in-app** (`VoiceRecorder`, MediaRecorder API), producing a File that goes
  through the same upload path. Recorders report types like `audio/webm;codecs=opus`, so
  `MediaService.baseContentType` strips parameters before the kind lookup — without it the app
  would refuse its own recordings. `getUserMedia` needs a secure context, so recording works on
  https and localhost but **not over plain http on a LAN address**; the component detects that and
  says so instead of offering a button that cannot work.
- Headless Chromium on macOS cannot open an audio source at all (`NotReadableError`), even with
  `--use-fake-device-for-media-stream`. The e2e recording spec therefore stubs only the browser's
  capture via `addInitScript`; the File, upload, storage and playback stay real.
- Integration tests run against **real MinIO**, not a mock. It is a singleton `GenericContainer` in
  `TestcontainersConfiguration` rather than a bean, because `PostgreSQLContainer` is also a
  `GenericContainer` and injecting one by type is ambiguous.
- **The media gallery owns no data**: `/api/media` is a read-only view over care-log, milestone and
  profile media. Adding or removing happens on the owning entry, so any mutation touching an
  attachment must invalidate `['care-logs']`, `['milestones']` and `['media']` together — see
  `invalidateMedia` in `web/src/api/hooks.ts`.
