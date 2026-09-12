# Spec 06 — Photo storage

## Overview
Photo bytes live in S3-compatible object storage, not on the application's filesystem. The database
stores only the object key; the application streams objects through its own endpoints so URLs,
caching validators and access rules stay in one place.

## Requirements
- **One implementation, two deployments.** The code talks the S3 API. A configurable endpoint lets
  the same build run against MinIO inside the compose stack (the default, so local development and
  the multi-instance requirement stay self-contained and offline) or against real AWS S3 by pointing
  `.env` at it. Nothing in the application distinguishes the two.
- Configuration comes from the environment: bucket, endpoint (blank = real AWS), region, credentials
  (blank = the AWS default credential chain, so IAM roles work), path-style addressing, and whether
  to create the bucket when missing.
- Keys keep the existing shape — `baby/<uuid>.<ext>`, `care-logs/<uuid>.jpg`,
  `milestones/<uuid>.jpg` — so the values already held in `photo_path` remain valid.
- Thumbnails are sibling objects (`<uuid>_thumb.jpg`), created on first request and deleted with
  the original.
- Objects are served by streaming through the API, preserving the existing ETag and
  `Cache-Control: no-cache` behaviour and setting `Content-Length` from the object's metadata.
- A missing object answers `PHOTO_NOT_FOUND`, the same as before.
- Each compose instance gets its own storage, so two stacks on one machine never share photos.

## Acceptance criteria
- Uploading, serving, thumbnailing and deleting all work against the object store, with no photo
  bytes written to the application container's filesystem.
- The compose stack works with no AWS account and no internet access.
- Switching to real AWS S3 requires only `.env` changes, no code change.
- Integration tests run against a real S3 API (MinIO in a container), not a mock.
