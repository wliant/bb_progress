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

## Development workflow

After completing each feature:
1. Ensure unit/integration tests are created for the feature.
2. Start the docker compose stack in local mode.
3. Verify the feature in the browser using the Playwright MCP.

## Commands

_(To be filled in as the projects are scaffolded — build, lint, test, and single-test commands for `app/`, `web/`, and `e2e/`.)_

- Start the stack: `docker compose up -d` (with `.env` configured)
- Run a second instance: use a different `.env` (distinct `COMPOSE_PROJECT_NAME` and ports), e.g. `docker compose --env-file .env.instance2 up -d`
