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
