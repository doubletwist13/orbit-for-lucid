# Orbit for Lucid: Project Plan

## Goal
Control the core functions of a Lucid (status/SOC, lock, unlock, precondition) from a Garmin Venu 3 via an Android
companion app. Priorities: **secure** (no secrets in git, best-practice secret handling on Android and Garmin),
**responsive** (watch and phone react quickly), **simple**, and following Android and Garmin best practices.

Decisions so far:
- Android only. Venu 3 primary (only test device); other touch Garmins best-effort.
- v1 scope: status (SOC, plugged in/charging, locked), lock, unlock, precondition (HVAC). Frunk/trunk/horn/lights are
  post-v1.
- Public GitHub repo. No Play Store / Connect IQ store release until a terms review (Phase 7) is done.
- Name is a working name (**Orbit for Lucid**) until trademark/store searches are done.

## Architecture
```
Venu 3 (Monkey C device app + glance)
   | Connect IQ messaging via Garmin Connect Mobile (BLE)
   v
Android app (Kotlin)
  - Connect IQ Mobile SDK receiver (watch commands)
  - Lucid gRPC client (protoc-generated from vendored MIT protos, OkHttp HTTP/2)
  - Keystore-encrypted refresh token
   | HTTPS / HTTP2 gRPC
   v
Lucid mobile gateway (unofficial, reverse-engineered)
```
- The phone does the Lucid calls; we do not proxy through any server. Verified against the Connect IQ
  `Communications.makeWebRequest` docs (API 1.3.0 page, checked 2026-10-24): since CIQ 6.0.3 it accepts a String or
  ByteArray `:body`, a string Content-Type, and an octet-stream response as a ByteArray, so binary protobuf is no longer
  impossible on new devices. It is still a poor fit for Lucid's gRPC gateway, for these reasons:
  - Docs say nothing about HTTP/2, trailers (`grpc-status` arrives in trailers) or `TE: trailers`; requests are relayed
    by Garmin Connect Mobile or Wi-Fi, so behaviour is unknown.
  - Binary responses require `Content-Type: application/octet-stream`; gRPC replies `application/grpc`, which would
    likely error as `UNSUPPORTED_CONTENT_TYPE_IN_RESPONSE`.
  - The gateway rejects requests carrying `content-length`; the CIQ stack probably adds it.
  - Needs CIQ 6.0.3+, but the Venu 3 is **API level 5.2**, so binary request bodies are not available on the primary
    target at all. It would also put the refresh token and MFA login on the watch, against the security goals.
  - Conclusion: phone companion stays.
- Watch protocol: small dictionary messages, defined and versioned in `docs/protocol.md` (the contract; do not
  duplicate it here). The watch holds no secrets; the phone returns display data only.
- Android client: Kotlin + OkHttp + protobuf-javalite. No Chaquopy (bigger APK, slower cold start). Port only the ~6
  calls needed.

## Reference repos and reuse
- `python-lucidmotors` (MIT, Nick Shipp) and `ha-lucidmotors` (MIT, Michael Borohovski): authoritative API references;
  protos vendored and logic ported with attribution in `NOTICE`.
- Calls ported (`lucidmotors/__init__.py`): `login` / `login_with_refresh_token` / `authentication_refresh`,
  `fetch_vehicles`, `wakeup_vehicle`, `door_locks_control`, `battery_precon_control` / `defrost_control` /
  `set_cabin_temperature`.
- Auth: `id_token` lasts 5 min (every gRPC call), `gigya_jwt` 6 h, `refresh_token` does not rotate. Refresh when under
  ~60 s remain, never more often (`GetNewJWTToken` is rate-limited, `RESOURCE_EXHAUSTED`). Store the refresh token,
  never the password.
- `Lucid-Motors-WearOS-Companion-App`: its author gave permission to reuse their own code; attributed in `NOTICE`. What
  is used: the gateway workaround (it resets the stream if HTTP/2 `content-length` is sent, so `LucidClient` uses a
  streaming body and strips `Content-Length` / `Accept-Encoding`). No artwork is reused: all icons here are original.
- Garmin's `connectiq-android-sdk` sample is a read-only reference for the phone-side Connect IQ plumbing. Its code and
  its bundled AAR are not copied; the app depends on Maven Central `com.garmin.connectiq:ciq-companion-app-sdk`. Flow:
  `ConnectIQ.getInstance(ctx, WIRELESS)` -> `initialize` -> `getKnownDevices()` -> `registerForAppEvents(device,
  IQApp(id), cb)` -> `sendMessage(device, app, payload, cb)`. The SDK is a bound-service client of Garmin Connect
  Mobile, which must be installed.

## Q1: full watch app vs watch face + complications
| | Device app (+ glance) | Watch face + complications |
|---|---|---|
| Input | Touch/buttons, confirm dialogs | Effectively none; complications are read-only data feeds |
| Actions (lock/unlock/precon) | Yes | No |
| Status | Yes; glance for quick SOC | SOC only, as fresh as the app/background publishes |
| Memory/power | More memory; runs only while open | Tight limits, always on |

Recommendation: a Connect IQ **device app** with a **glance**, because the core need is actions. A watch face with
complications is on the roadmap (v4).

## Q2: repo structure
One monorepo (the watch<->phone protocol is a shared contract and both halves release together):
```
android/   Kotlin companion (Gradle); vendored Lucid protos in app/src/main/proto
watch/     Monkey C Connect IQ project
docs/      protocol.md, research/ (API notes)
LICENSE  NOTICE  README.md  INSTALL.md  PLAN.md  SECURITY.md  .gitignore  .gitleaks.toml
```

## Q3: naming and branding
- Use "for Lucid" descriptively in tagline/README, not in the brand. Keep "Lucid", "Garmin", "Gravity", "Air" out of the
  product and repo name. No Lucid/Garmin logos, wordmarks, fonts or car art. Use "Connect IQ" only descriptively.
- Every README / About screen: "Unofficial. Not affiliated with, endorsed by, or sponsored by Lucid Motors or Garmin.
  Uses an undocumented API that may change or be withdrawn."

## Q4: license
- **MIT** (matches both upstreams; simplest; permits sideloading and forks). `NOTICE` preserves upstream copyrights.
- Garmin Connect IQ SDK terms are proprietary and the SDK is never in this repo. The MIT license covers only our own
  code.

## Garmin SDK rules
- Do not commit the SDK, simulator, device profiles, Garmin sample apps, the bundled `monkeybrains-sdk-release.aar`, or
  code copied or adapted from the SDK samples. Users install the SDK with Garmin's SDK Manager and accept Garmin's
  license themselves; the Android SDK comes from Maven Central.
- No decompiled dumps or notes quoting SDK internals in `docs/`.
- No Garmin logos or implied affiliation; compatibility statements ("works with Venu 3") stay factual.
- Re-read Garmin's current SDK agreement and Developer Guidelines before any store release.

## Target devices
Phase 4 builds and tests for the Venu 3 only (real hardware), with `Menu2` / `ConfirmationDelegate` so buttons work.

**Status 2026-10-25:** the manifest lists `venu3`, `venux1` (Venu X1, 448x486 rectangular, needs API 6.0.3 so SDK 9.x)
and `fenix7` (260x260). Both extras compile (`monkeyc -d <id>`) but have never run: the SDK simulator segfaults in
freetype/pango at start-up on the author's machine, so the fenix 7 input check and the rectangular-layout check still
need a working simulator or a real owner. The README says plainly that they are untested. Before any store release,
trim the manifest to tested watches (store distribution covers every listed product). The glance uses fractions of the
screen size, so layouts should scale, but that is unverified.

| Model | Screen | Input | Why include it |
|---|---|---|---|
| **Venu 3** (primary) | 454x454 round AMOLED | touch + 2 buttons | Real test hardware; largest AMOLED layout |
| **Venu 3S** or **vivoactive 5** | 390x390 round AMOLED | touch + buttons | Smaller round AMOLED; scaling check |
| **fenix 7 / FR255/955** | 240-280 px round MIP | buttons (touch on some) | Popular line; different input model |
| **Venu Sq 2** (optional) | 320x360 rectangular AMOLED | touch + button | Only rectangular layout |

- Input matters as much as screen size: build on `WatchUi.Menu2` and `ConfirmationDelegate` (work with touch and
  buttons); no custom touch-only widgets.
- One codebase via resource qualifiers (e.g. `resources-round-454x454`) where layouts differ.
- Memory is the main constraint on MIP watches: keep the app small and bitmaps minimal; the glance limit (64 KB) is the
  tightest.
- Skip monochrome Instinct-class watches (tiny, no touch; would need a separate design).

## Phone model (polling, battery, API limits)
- The WearOS app polls every 7 s while interactive and keeps polling in ambient mode under a WAKE_LOCK: roughly 500
  calls/hour while open. Not a pattern to copy.
- **Wake question: answered 2026-10-25 (Pixel 10 Pro + Venu 3).** A killed app is NOT woken by a watch message: the
  watch shows "Delivered" (Garmin Connect Mobile accepted it) but nothing runs, and a manifest receiver for the SDK's
  `INCOMING_MESSAGE` broadcast never fires (the SDK registers it at runtime, and delivery needs a prior bound-service
  registration). A `connectedDevice` foreground service that only keeps the SDK registered works: with the app swiped
  away and the screen off, a watch tap reached the phone and the reply came back (about 0.4 s foreground, 1.7 s
  background; one sample each). The service holds an idle BLE link and makes NO Lucid calls, so Lucid traffic stays
  on-demand. Needs `FOREGROUND_SERVICE_CONNECTED_DEVICE` plus `CHANGE_NETWORK_STATE` (the type's prerequisite) and a
  runtime `POST_NOTIFICATIONS` prompt (asked on first launch; declining only hides the notification); persistent
  "Watch link active" notification (min importance). Decided against a service on/off switch (too easy to hit by
  accident, and logging out already stops car control) and against prompting for "unrestricted" battery use (the
  swiped-away app worked on the Pixel at the default setting). Reboot: a `BOOT_COMPLETED` receiver restarts the
  service after the first unlock (tested; it arrived about 2 min after a slow boot); a force-stopped app gets no boot
  broadcast until opened by hand. The watch fails a request with `nolink` after 8 s of silence. Still untested:
  hours-long idle, batterystats.
- **Lucid calls stay on-demand.** When the companion receives a watch message it fetches status once (a 4 s cache
  coalesces repeats). After a command it wakes the car if needed, sends, then polls 3 times at 5 s and then every 10 s
  until the expected state change (about 105 s cap); sustained 3-5 s polling triggered RESOURCE_EXHAUSTED (see
  `docs/research/phase1-lucid-api-findings.md`).
- Command logic lives in an app-scoped `Controller`, so it outlives the screen and serves both the UI and the watch.
- A foreground service with continuous polling would cost a persistent notification, radio wakeups and many more calls,
  with a real risk of `RESOURCE_EXHAUSTED`. Revisit only if on-demand wake proves unreliable on some phones (OEM battery
  optimizers).
- Rate-limit safety: single in-memory token, single-flight refresh, retries of 2/4/8 s on `RESOURCE_EXHAUSTED`, a short
  status cache.

## Security
Repo:
- `.gitignore`: `local.properties`, `*.jks`, `*.keystore`, `*.p12`, Garmin `developer_key*` / `*.der`, `.env`, `*.apk` /
  `*.aab` / `*.iq`, build dirs.
- gitleaks pre-commit hook plus a GitHub Action; GitHub secret scanning and push protection on. Signing keys and
  credentials live outside the repo.
- Anonymize all fixtures and logs; no real VIN, email, or tokens in git.
- No secrets in CI unless required; release signing later via GitHub encrypted secrets.

Android:
- Store only the refresh token, encrypted by an Android Keystore AES-GCM key. `allowBackup=false` and everything is
  excluded in `dataExtractionRules`.
- `usesCleartextTraffic=false`. No certificate pinning (the unofficial gateway could rotate certs).
- Optional `BiometricPrompt` gate for unlock (open).
- Never log tokens, email, or VIN; minimal dependencies.
- Accept Connect IQ messages only for this watch app's UUID.

Watch:
- Stores nothing secret. Confirm tap before unlock and climate on/off; lock has none by design. The watch's own passcode / lock is the
  device-side protection.

## Responsiveness
- Watch draws instantly from cached state (`Application.Storage`), shows its age, and updates when the phone replies;
  clear in-flight and error states (offline, auth expired, car asleep).
- Phone keeps the HTTP/2 connection and token warm during a session.
- Keep the Monkey C app small for Venu 3 memory limits; use `Communications.transmit` with a `ConnectionListener` for
  delivery confirmation.

## Phases (with decision gates)
1. **Prove Lucid calls** (DONE 2026-10-24, see `docs/research/phase1-lucid-api-findings.md`): password login works with
   the MFA account (no MFA step in the library); refresh-token login works; status, wake, unlock, lock, cabin
   precondition on/off all work. Key finding: commands are accepted instantly but state confirms 10-40 s later and a
   sleeping car needs 30 s+ to wake, so the UI uses pending states plus capped status polling. Still to test later:
   refresh-token longevity, cellular/away-from-home behaviour.
2. **Repo bootstrap** (DONE): monorepo, LICENSE/NOTICE, `.gitignore`, gitleaks hook, SECURITY.md, disclaimers,
   `docs/protocol.md`, `.github/workflows/gitleaks.yml`.
3. **Android client** (DONE 2026-10-24): protoc codegen, OkHttp gRPC transport (content-length fix), Keystore token
   store, login screen, status + lock/unlock/climate buttons in a green-terminal theme, unit tests with a fake
   transport. Open: persist "pending command" state across app restarts, a configurable climate temperature,
   release-build (R8) check.
4. **Watch app** (Venu 3 first; spike and UI DONE 2026-10-25; the glance shows cached SOC, lock state and age with no
   phone calls; the fenix 7 button check is still open; lock deliberately has no confirm tap; unlock and climate do).
5. **Bridge** (DONE 2026-10-25 on the Pixel 10 Pro + Venu 3). Timings are one set of tests on one car and Lucid's
   latency is known to vary wildly: unlock from a sleeping car about 13 s without car wifi and 9-10 s with it, lock on
   an awake car 1-2 s. Offline, logged-out, swiped-away-app, reboot and car-on-wifi cases pass; batterystats and a long
   idle are still to test. Connect IQ Mobile SDK in the Android app, command logic out of the ViewModel,
   command/response protocol per `docs/protocol.md`, error mapping.
6. **Polish**: backoff (DONE: 2/4/8 s retries on rate limit, gentle polling), stale indicators (DONE: age line, data
   dimmed after 30 min), optional biometric gate, second watch model.
7. **Pre-release review (only if going to stores)**: re-read Garmin's SDK agreement, Developer Guidelines and store
   rules; Lucid's terms; Google Play policy on third-party logins and vehicle control; privacy policy; trademark search.

## Roadmap after v1
Each item below touches `docs/protocol.md`, the phone and the watch, so bump the protocol version (or add optional
fields that v1 watches ignore) when it lands. All the status data comes from the same `GetUserVehicles` call as v1, so
none of it adds Lucid calls.
- **v2 (actions and their state):**
  - HVAC target temperature (`set_cabin_temperature`; v1 hardcodes 21 C). Needs a picker on the watch and a unit
    setting.
  - Frunk and trunk: status (`rear_cargo` is in the vehicle state) and open/close (`frunk_*` / `trunk_*` in
    python-lucidmotors). Opening a closure is a physical action, so give it a confirm tap like unlock. (Frunk will only
    work on vehicles that have a powered frunk; some don't.)
- **v3 (read-only details, one "Details" screen on the watch):**
  - Tire pressure (the four `*_tire_pressure_bar` fields; convert to psi or bar on the phone).
  - Window status (`WindowPositionState`, per window; collapse to "all closed" or a list of open ones).
  - Battery health (`battery_health` warning state and `battery_health_level`).
  - Min/Max Cell Temps (`min_cell_temp` and `max_cell_temp`).
  - Indoor/outdoor temperature (`indoor_temp` and `outdoor_temp`).
  - Could be pulled into v2 cheaply: it is only extra reply fields plus one screen, and carries no write risk. The
    reason to wait is watch UI and message size, not API work. Revisit once the v1 UI is settled on real use.
- **Possible later: background glance refresh.** Today the glance only shows what the app cached when last opened (it
  dims after 30 min and shows its age). A Connect IQ background task (`Background.registerForTemporalEvent`, 5 min
  minimum) could ask the phone for status every 5-15 min so the glance stays current. A status fetch reads Lucid's
  cloud copy and does not wake the car (waking is the separate `WakeupVehicle` call), though the data only changes
  once the car has reported. Costs: watch and phone battery (a BLE wake plus one small call per refresh), and it
  breaks the on-demand-only rule. Only add it if people want it, ideally with a long interval (30+ min) and a
  setting to turn it off. Measure the battery cost first.
- **v4 (watch face and complications):** a custom Orbit watch face showing the car's state at a glance (SOC, lock,
  charge, climate, age), plus complications or tap targets on the face that trigger actions (lock, unlock, climate)
  without opening the app. A watch face is a separate Connect IQ app type with its own memory limit and no input
  beyond complication taps, so it must read the state the main app caches in `Application.Storage` and hand actions
  to the app (a face cannot send phone messages itself, and has to be power-frugal: no polling). Open questions: how
  Garmin lets a face launch an action (complication launch vs. a tap handler), whether the unlock confirm tap still
  fits, and whether to publish it as a second app id. Decide after the glance and v2 show how people use it.

## Verification
- Phase 1 scripts succeed against the real car.
- Android unit tests (transport, token refresh, store) plus a manual run on a physical phone.
- Venu 3 end-to-end sideload; record tap-to-car latency.
- Run `gitleaks detect` over full history; verify backup/data extraction contains no tokens.
- Count API calls in a typical session to confirm no polling storm.

## Known limits
- **Single vehicle only.** The author has one car and cannot test an account with several. `LucidClient.status()`
  takes the first entry of `GetUserVehicles` (marked `shortcut:` in code), and every command targets that vehicle. For
  a multi-vehicle account it is unknown whether the first entry is stable or which car it is, so commands could reach
  the wrong car. An account with no vehicles fails with an unhelpful "List is empty" message. Supporting more cars
  needs a picker on the phone, a stored vehicle id, and a vehicle field in `docs/protocol.md`; it needs a tester with
  a second car. Shared/driver-access vehicles are untested too. The README says this.

## Open questions
- How long the Lucid refresh token stays valid.
- Which extra Garmin models to support, and who can test them.
- Whether Lucid's app and website terms allow this kind of client (the README discloses the risk either way).
