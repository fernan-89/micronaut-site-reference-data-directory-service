# micronaut-site-reference-data-directory-service

BIAN-aligned Service Domain **site-reference-data-directory** (Control Record: `Site`), port `8087`.

Scaffolded by `scripts/new-service.ps1`. Add the aggregate, use cases, controller (`/site-reference-data-directory/v1/{id}/{behavior-qualifier}`),
persistence adapter, Postman suite and ADRs, keeping `gradlew check` at 100% line and branch coverage.

## Error catalog

| Code | HTTP | Meaning |
|---|---|---|
| `ERR-SITE-00404` | 404 | Site not found |
| `ERR-SITE-00409` | 409 | State conflict or duplicate (ADR-019) |
| `ERR-VALIDATION-00400` | 400 | Payload/header/identifier validation failure |
| `ERR-INTERNAL-00500` | 500 | Unexpected technical failure |

## License

Proprietary - all rights reserved. See [LICENSE](LICENSE). This software is not open source.