# Note API Homebox v0.26.x — verificate dalla spec OpenAPI

Fonte: `backend/app/api/static/docs/swagger.json` del repo `sysadminsmedia/homebox`
(branch `main`, scaricata il 2026-11-09). Swagger 2.0, `basePath: /api`.

Questi appunti correggono alcune ipotesi di `instructions.md` con quanto risulta
davvero dalla spec. L'app e' allineata a questi endpoint.

## Autenticazione
- Un solo schema di sicurezza: header `Authorization`, valore `Bearer <TOKEN>`.
  Vale **sia per il token di login sia per l'API Key `hb_...`**: stesso header.
  (L'app normalizza togliendo un eventuale prefisso "Bearer " gia' presente.)
- `POST /api/v1/users/login` — body `{username, password, stayLoggedIn}` →
  `{token, expiresAt, attachmentToken}`.
- `GET  /api/v1/users/refresh`, `POST /api/v1/users/logout`.
- `GET  /api/v1/users/self` → **wrapped**: `{ item: UserOut }`
  (UserOut: id, name, email, isSuperuser, defaultGroupId, groupIds[]).
- API Key statiche: `GET/POST /api/v1/users/self/api-keys`,
  `DELETE /api/v1/users/self/api-keys/{id}`. Create → restituisce `token`.

## Entities (cuore dell'app)
- `GET  /api/v1/entities` — query: q, page, pageSize, parentIds[], entityTypeIds[],
  tags[], matchAllTags, orderBy, orderDirection → `EntityListResult`.
- `POST /api/v1/entities` — `EntityCreate{ name*, description, entityTypeId,
  locationId, parentId, quantity, manufacturer, modelNumber, tagIds[] }` → `EntityOut`.
- `GET/PUT/PATCH/DELETE /api/v1/entities/{id}`.
- `GET  /api/v1/entities/tree?withItems=bool` → `[TreeItem{ id, name, type, children }]`.
- `GET  /api/v1/entities/{id}/path` → breadcrumb.
- `POST /api/v1/entities/{id}/duplicate`.
- Allegati: `POST /api/v1/entities/{id}/attachments` (+ `/{attachment_id}`).
- Manutenzione: `GET/POST /api/v1/entities/{id}/maintenance`.

Location vs item = determinato dall'**entity type** (`isLocation: bool`), non da
endpoint separati. La gerarchia e' `parentId`; `locationId` fissa la location di
un oggetto (in EntityOut `location` e' risolta e read-only).

## Entity types
- `GET/POST /api/v1/entity-types`, `PUT/DELETE /api/v1/entity-types/{id}`.
- `EntityTypeSummary{ id, name, icon, isLocation, defaultTemplateId, ... }`.

## Tag (NON "labels")
- La spec espone i tag su `GET/POST /api/v1/tags`, `GET/PUT/DELETE /api/v1/tags/{id}`.
  `instructions.md` parlava di `/labels`: sul :latest sono **`/tags`**.
- `TagSummary{ id, name, color, icon, description, parentId }` (gerarchia + icone).

## Statistiche / gruppo
- `GET /api/v1/groups/statistics` → `{ totalItems, totalLocations, totalTags,
  totalUsers, totalItemPrice, totalWithWarranty }`.
- Anche `/groups/statistics/{locations,purchase-price,tags}`.

## Utility / altro
- `GET /api/v1/status` → `APISummary{ title, message, health, versions[], ... }`
  (pubblico: usato per il test connessione, non richiede auth).
- `GET /api/v1/products/search-from-barcode` — lookup per il flusso "scansiona e
  precompila" (M5).
- `GET /api/v1/qrcode` — genera QR entita'.
- `GET /api/v1/currencies`.

## Collezioni / tenant
- Nella spec :latest **non** compare un endpoint dedicato per selezionare la
  collezione, ne' un header `X-Tenant` documentato: il multi-tenant e' modellato
  come **groups** (`/api/v1/groups`, membri, inviti, statistiche). L'utente ha
  `defaultGroupId` e `groupIds[]`. Per ora l'app usa il gruppo di default; la
  selezione collezione va rivalutata sulla spec dell'istanza quando servira'.
