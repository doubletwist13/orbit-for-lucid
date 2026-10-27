# CLAUDE.md

Orbit for Lucid: Garmin Connect IQ watch app (Monkey C, `watch/`) + Android companion (Kotlin, `android/`) controlling a
Lucid vehicle via its unofficial gRPC API. Public repo. `PLAN.md` (public) holds design, decisions and phases; update
it on changes. Legal, naming, permission and machine-specific notes go in the gitignored `PLAN.local.md`, never in
PLAN.md.

## Style

- Max line length 120 chars, all file types (wrap prose too).
- Match surrounding code; comments explain why only.

## Rules

- Never commit tokens, VINs, emails, real API responses, keystores or `developer_key*`; anonymize fixtures and logs.
- Never Read/cat credential or key files (`developer_key*`, `*.jks`, `.env*`); use paths only.
- No Garmin SDK material in the repo (SDK, simulator, device profiles, samples, AAR contents, code adapted from them).
- No Lucid artwork or logos; icons must be original. WearOS-app icons go only in gitignored `assets-local/`.
- Keep the "unofficial, not affiliated" disclaimer; attribute reused upstream code in `NOTICE`.
- Don't bypass the gitleaks hook (`--no-verify`) unless told to.

## Architecture

- Phone makes all Lucid calls; the watch holds no secrets. Watch<->phone messages are defined in `docs/protocol.md`.
- Store only the refresh token (Keystore-encrypted); `allowBackup=false`. Refresh JWT only when <60 s remain.
- Gateway resets streams sending HTTP/2 `content-length`: stream the body, strip `Content-Length`/`Accept-Encoding`.
- Lucid calls are on-demand, plus one status fetch every 5 minutes pushed to the watch whether or not it is
  connected (a setting turns it off); never poll faster, and back off after a rate-limit answer.
- Venu 3 is the primary target (CIQ API 5.2); don't use newer APIs without a fallback.

## Tooling

- Run `monkeyc` and `monkeydo` on the host (Temurin 21 on PATH). The simulator only works in the distrobox
  `garmin-ubuntu` (Ubuntu 22.04): on Fedora it segfaults because it exports its own FreeType symbols. Start it with
  `distrobox enter garmin-ubuntu -- ~/.Garmin/ConnectIQ/Sdks/<sdk>/bin/connectiq`, then `monkeydo` from the host. It
  has no phone link; use Simulation > Phone App Message to send a status (docs/protocol.md).
- Commit/push only when asked.
- Edit files only with the Edit/Write tools so changes show as diffs in VS Code. No python/sed/heredoc edits via
  Bash; if there is no alternative, say why first.
