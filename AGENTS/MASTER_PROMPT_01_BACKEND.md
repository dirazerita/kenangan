# MASTER_PROMPT 01 — License & Config Mini-Backend (Laravel 12)

## TIMING (owner decision D-002)
Do NOT start this phase until the desktop **Stabilization checklist** in `PROGRESS.md` is fully checked. The app must be feature-complete and stable before any license enforcement exists.

## ROLE
Senior Laravel engineer. Build a **small, stateless** backend on the owner's **dedicated license website** (already prepared). If that site runs Laravel, follow this spec as-is; if it runs another stack, keep the endpoint contract, payloads, and crypto identical and adapt the implementation. Reuse the owner's existing Midtrans account and mailer. Scope: licensing, remote config, store, update check, opt-out telemetry. **No AI proxying, no media, no queue** (AD-02/06/09).

## SESSION PROTOCOL
Read `MEMORY.md` + `PROGRESS.md` first; plan; small commits; update both docs each session. Prefix tables `kenang_`, routes `/api/kenang/v1/`.

## HARD RULES
- This service must never accept photo/audio/video uploads or user API keys. Reject multipart on all routes.
- All responses < 2s; no long-running work. Shared-hosting friendly.
- License integrity = **Ed25519 signatures** (libsodium): private key in env (`KENANG_LICENSE_SK`), public key exported once for embedding in the desktop app (document the export command in `docs/KEYS.md`). Never rotate silently — version the keypair (`kid` field).

## DATA MODEL (migrations)
- `kenang_licenses` (id, key_hash [sha256 of "KNG-XXXX-…"], plan[trial_gift|personal|studio], max_devices, status[active|revoked], update_until, order_id?, email, created_at)
- `kenang_devices` (id, license_id, fingerprint, app_version, activated_at, last_seen, deactivated_at?)
- `kenang_orders` (id, sku, amount_idr, midtrans_order_id, status, email, license_id?, timestamps)
- `kenang_remote_config` (key, json_value, updated_at)
- `kenang_events` (id, anon_install_id, name, props_json, created_at) — optional telemetry, NO PII, no content

## ENDPOINTS
1. `GET /config` — public, cache 5 min. Payload (see MEMORY §6 PriceHint): `{ vibes[], tier_routing{hemat|standar|premium → model_slug}, price_hints[], fx_idr, min_version, latest_version, download_url, flags{trial_max_exports:3, ...} }`. Admin-editable via config table. The desktop currently ships with a bundled `app-config.json` of the same schema; once this endpoint is live, the app's `CONFIG_URL` simply points here.
2. `POST /licenses/activate` `{license_key, fingerprint, app_version}` → validate key (hash lookup, status, seat count < max_devices; reactivating same fingerprint reuses seat) → respond `{license_blob, signature, kid}` where blob = canonical JSON of MEMORY §6 `LicenseState` server fields (`plan, device_fingerprint, activated_at, update_until, issued_at`). Errors: `invalid_key | revoked | seat_limit`.
3. `POST /licenses/heartbeat` `{license_key, fingerprint}` → update last_seen, return fresh signed blob (or `revoked`). Client calls weekly; tolerates 30-day offline (client-side grace, AD-08).
4. `POST /licenses/deactivate` `{license_key, fingerprint}` → frees the seat (sets deactivated_at).
5. `POST /checkout` `{sku, email}` → create `kenang_orders` + Midtrans transaction (reuse existing service) → `{payment_url}`.
6. `POST /midtrans/webhook` (reuse verified handler) → on settlement: generate license key `KNG-` + 16 chars (crockford base32, store hash only, show plaintext once), create `kenang_licenses`, email the key via existing mailer (template `emails/kenang_license`), mark order done. **Idempotent** by midtrans_order_id.
7. `POST /events` — optional batch telemetry `{anon_install_id, events[]}`; hard-cap 50/req; silently drop extras.

Rate limits: activate/heartbeat 30/day per key; checkout 10/h per IP; config 120/h per IP.

## ADMIN (minimal)
Filament or plain Blade behind existing admin auth: licenses list (search by email/key-suffix, revoke, extend update_until, add gift license), orders list, config JSON editor with validation + "publish" button, simple metrics page (activations/day, heartbeats, trial installs from events).

## TESTS (Pest — must pass)
Signature verifies against exported public key; seat-limit enforcement incl. reactivation of same fingerprint; revoke propagates on heartbeat; deactivate frees seat; webhook idempotency (double-fire → one license); config shape snapshot; multipart rejected everywhere.

## DELIVERABLES & DoD
`docs/kenang-license.http` request collection; `docs/KEYS.md` (keypair generation/rotation); `docs/DEPLOY_KENANG.md` (env vars, Hostinger notes — no cron needed except optional daily events prune). All Phase-01 PROGRESS boxes checked; a full manual walkthrough proven: checkout (sandbox) → webhook → email received → activate on fingerprint A → seat-limit hit on C → deactivate A → activate C → revoke → heartbeat returns revoked.
