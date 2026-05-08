# Upload Storage Contracts

## Scope

MVP upload/storage boundary for user-generated review images.

The current implementation is owned by `services/sangui-order-service` because review images are a prerequisite for `POST /api/orders/{orderId}/reviews` and there is no shared upload service yet.

## Review Image Upload API

### `POST /api/uploads/review-images`

Auth:

- Requires mall JWT through gateway.
- Gateway route `/api/uploads/**` forwards to order-service.

Request:

- `multipart/form-data`
- Part name: `file`
- Supported content types:
  - `image/jpeg`
  - `image/png`
  - `image/webp`
- Max file size: `5MB` by default.
- Storage path config:
  - `SANGUI_REVIEW_IMAGE_STORAGE_DIRECTORY`
  - default `./data/uploads/review-images`
- Public path config:
  - `SANGUI_REVIEW_IMAGE_PUBLIC_BASE_PATH`
  - default `/api/uploads/review-images`

Response code: `REVIEW_IMAGE_UPLOADED`.

Response data:

```json
{
  "url": "/api/uploads/review-images/0e9a8c2d-7f4e-4b43-bce0-7e50ef3f58e4.jpg",
  "contentType": "image/jpeg",
  "sizeBytes": 12345
}
```

Rules:

- The service validates both declared content type and image signature.
- Stored file names are generated server-side and must not reuse client-provided file names.
- Response `url` is a public URL path only. It must not expose local disk paths, internal object keys, operator fields, or storage trace fields.
- The upload response may still use the standard `ApiResult` envelope with response `traceId`.

## Local Storage Operations Runbook

Configuration:

- `SANGUI_REVIEW_IMAGE_STORAGE_DIRECTORY` is the local filesystem directory used by order-service for review image bytes. Default is `./data/uploads/review-images`.
- `SANGUI_REVIEW_IMAGE_PUBLIC_BASE_PATH` is the public URL prefix stored in `oms_order_review.image_urls`. Default is `/api/uploads/review-images`.
- The storage directory must be on durable disk for environments where review images are business assets. Container-local ephemeral filesystems are only acceptable for disposable local development.

Capacity and retention boundaries:

- Capacity risk is local disk exhaustion. Operators must monitor free space for the configured directory and alert before writes fail.
- Upload writes are not currently quota-managed per user/order. The application enforces file count through review creation and file size through upload validation; infrastructure still owns disk capacity limits.
- Backups must include both MySQL `oms_order_review.image_urls` and the configured image directory from the same recovery window. Restoring only one side can produce broken public/admin image URLs.
- Migration to object storage or another directory must preserve public URL compatibility or provide a compatibility read route for existing `/api/uploads/review-images/{fileName}` values.

Failure and recovery matrix:

| Case | Operator signal | Recovery |
| --- | --- | --- |
| Upload validation failure | HTTP 400 `VALIDATION_FAILED` with response `traceId` | User can retry with JPEG/PNG/WebP under the size limit; no file should be written. |
| Storage write failure or disk full | HTTP 500 `INTERNAL_ERROR` with response `traceId`; service logs should include sanitized storage failure context | Free or expand disk, verify directory permissions, then retry upload. Do not insert manual `imageUrls` rows. |
| File missing during read | HTTP 404 `ORDER_REVIEW_IMAGE_NOT_FOUND` | Check whether the file was omitted from backup/restore or manually deleted; restore the file from backup if the URL is still referenced by `oms_order_review.image_urls`. |
| Read path traversal or unsafe file name | HTTP 400 `VALIDATION_FAILED` | Treat as invalid request; do not map arbitrary paths to local disk. |
| Admin thumbnail load failure | Browser image error for public URL | Admin UI must show a non-blocking placeholder with the image URL/review id so hide/restore/reply operations remain usable. |

Good/Base/Bad cases:

- Good: backup runbooks copy the storage directory and database snapshot together, then verify sample URLs through `GET /api/uploads/review-images/{fileName}`.
- Good: a missing file is recovered by restoring the exact generated file name referenced by `oms_order_review.image_urls`.
- Base: local development may delete `./data/uploads/review-images` when the database is also reset.
- Bad: operators edit `oms_order_review.image_urls` to point at local disk paths, `file:` URLs, object-store private keys, or external arbitrary URLs.
- Bad: an admin UI failure to load one thumbnail blocks review visibility or reply operations.

## Manual Orphan Cleanup Design

Automatic scheduled deletion is out of scope until image retention and audit policy are defined.

Low-risk manual/internal cleanup may be introduced with this contract:

- Input: storage directory, dry-run flag, and safety window such as `olderThanHours >= 24`.
- Reference source: parse every URL in `oms_order_review.image_urls`; referenced file names are protected regardless of review visibility.
- Candidate rule: delete only files under `SANGUI_REVIEW_IMAGE_STORAGE_DIRECTORY` whose generated file name is not referenced by any `oms_order_review.image_urls` row and whose last-modified time is older than the safety window.
- Safety: default must be dry-run and report candidate count, total bytes, oldest/newest last-modified times, and skipped unsafe file names.
- Execution: manual command or internal admin-only endpoint only; no automatic recurring job in this phase.
- Logging: include `traceId`, `shopId` when applicable, `dryRun`, `candidateCount`, `deletedCount`, `failedCount`, and sanitized failure reason.

Required assertion points for cleanup implementation:

- Good: dry-run reports an unreferenced old file but does not delete it.
- Good: referenced files for visible and hidden reviews are never deleted.
- Good: files newer than the safety window are skipped even when unreferenced.
- Bad: cleanup scans or deletes outside the resolved storage directory.
- Bad: cleanup parses URLs by trusting path traversal fragments or arbitrary absolute paths.

### `GET /api/uploads/review-images/{fileName}`

Auth:

- Anonymous read is allowed through gateway only for safe image file names matching:
  - `^[A-Za-z0-9._-]+\\.(jpg|jpeg|png|webp)$`

Rules:

- Path traversal is rejected.
- Missing files return `ORDER_REVIEW_IMAGE_NOT_FOUND`.
- Only image content types are served.

## Validation and Error Matrix

| Case | HTTP | code |
| --- | --- | --- |
| Missing mall JWT on upload | 401 | `AUTH_TOKEN_MISSING` |
| Missing file, empty file, unsupported content type, mismatched signature, too large, or unsafe file name | 400 | `VALIDATION_FAILED` |
| Missing uploaded image on read | 404 | `ORDER_REVIEW_IMAGE_NOT_FOUND` |
| Storage write failure | 500 | `INTERNAL_ERROR` |

## Required Tests

```powershell
mvn -q "-Dmaven.repo.local=D:\02-WorkSpace\02-Java\SanguiShop\.m2\repository" "-pl=services/sangui-order-service,services/sangui-gateway" -am "-Dtest=ReviewImageStorageServiceTest,ReviewImageUploadControllerTest,GatewayJwtAuthenticationFilterTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

Good/Base/Bad cases:

- Good: valid JPEG/PNG/WebP uploads return only a public URL, content type, and size.
- Good: gateway allows anonymous `GET /api/uploads/review-images/{fileName}` but keeps `POST /api/uploads/review-images` protected.
- Good: generated file names prevent path traversal and do not expose client file names.
- Base: local filesystem storage is acceptable for MVP and is configured by environment.
- Bad: upload response exposes absolute local paths, object storage keys, request trace fields inside `data`, or operator identifiers.
- Bad: frontend bypasses upload API and submits `file:` URLs, local disk paths, or arbitrary external URLs in `imageUrls`.
