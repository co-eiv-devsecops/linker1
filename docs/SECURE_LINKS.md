# Secure link creation

The `POST /link` endpoint validates destinations on the server before any database lookup or insert. It accepts only absolute `http` and `https` URIs with a host and a port in the valid TCP range. Relative URLs, missing hosts, malformed URIs, and schemes such as `javascript:`, `data:`, and `file:` receive HTTP `400` and are not stored.

Custom aliases remain optional. They must contain 1–64 ASCII letters, digits, hyphens, or underscores. Aliases that collide with registered application routes or static resources (`link`, `healthz`, `app.js`, and `styles.css`) are reserved, case-insensitively. Invalid or reserved aliases receive HTTP `400`; an alias already assigned to another URL receives HTTP `409`. Reusing the same alias for the same URL remains idempotent.

Validation errors returned by the web and Lambda APIs are Spanish, actionable messages. They do not echo the submitted destination or alias. Creation, redirect, and HEAD-resolution logs record link IDs and outcomes without logging destination URLs, which may contain private query parameters. The browser uses text nodes for messages and link labels rather than inserting API content as HTML.

## Try the API locally

Start Linker1 as described in the [README](../README.md), then create a valid link:

```sh
curl -i -X POST http://localhost:8080/link \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://example.com/article","alias":"mi-articulo"}'
```

Invalid schemes and reserved aliases return a Spanish `400` message:

```sh
curl -i -X POST http://localhost:8080/link \
  -H 'Content-Type: application/json' \
  -d '{"url":"javascript:alert(1)"}'

curl -i -X POST http://localhost:8080/link \
  -H 'Content-Type: application/json' \
  -d '{"url":"https://example.com","alias":"healthz"}'
```

## Scope and remaining product decisions

This feature implements the destination and alias rules in F1.1 of `AZURE_BOARDS_LINKER1.md`. It does not add accounts or management tokens. The existing `DELETE /{id}` endpoint remains unauthenticated, so anyone who knows a short ID can still delete that link; private link management requires a separate, agreed token feature. Destinations are returned in the redirect `Location` header by design, and HEAD continues to expose the destination under the existing API contract.

Redirect status: when this feature was written the redirect was a permanent `301`. The later click-count feature (HU-03/HU-04) changed it to `302` with `Cache-Control: no-store` so every visit is counted; see the `GET /{id}` entry in the README API section. The Lambda handler (`docs/SERVERLESS.md`) still answers `301` and does not count openings.
