# Orbit for Lucid: Project Plan

A high-level plan: goals, decisions and direction. Day-to-day work is tracked in
[issues](https://github.com/doubletwist13/orbit-for-lucid/issues) and shipped work in the
[releases](https://github.com/doubletwist13/orbit-for-lucid/releases); evidence behind the decisions is in
`docs/research/`. The watch<->phone messages are defined in `docs/protocol.md` (the contract; not repeated here).

## Goal
Control the core functions of a Lucid (status/SOC, lock, unlock, precondition) from a Garmin Venu 3 via an Android
companion app. Priorities: **secure** (no secrets in git, best-practice secret handling on Android and Garmin),
**responsive** (watch and phone react quickly), **simple**, and following Android and Garmin best practices.

- Android only. Venu 3 primary (only test device); other touch Garmins best-effort.
- Public GitHub repo. No Play Store / Connect IQ store release until a terms review is done (see Later).
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
Decisions (the reasoning and test results are in `docs/research/phone-bridge-findings.md`):
- **The phone makes every Lucid call; there is no server.** The watch cannot do gRPC against the gateway (and the
  Venu 3 is API 5.2), and would have to hold the refresh token. The watch holds no secrets and shows display data only.
- **A device app with a glance, not a watch face.** The core need is actions, and a face has no input. A face is on
  the roadmap for later.
- **A foreground service keeps the phone app reachable.** A killed app is not woken by a watch message.
- **Lucid calls are on-demand, plus one status fetch every 5 minutes** pushed to the watch (a setting turns it off).
  Never poll faster; back off after a rate-limit answer.
- **Kotlin + OkHttp + protobuf-javalite.** No Chaquopy (bigger APK, slower cold start). Only the handful of calls
  needed are ported.
- **The phone and the watch use SI units on the wire** (Celsius, bar); the watch converts for display.
- **One monorepo** (`android/`, `watch/`, `docs/`), since both halves share the protocol and release together.

## Reference repos and reuse
- `python-lucidmotors` (MIT, Nick Shipp) and `ha-lucidmotors` (MIT, Michael Borohovski): authoritative API references;
  protos vendored and logic ported with attribution in `NOTICE`.
- Auth: `id_token` lasts 5 min (every gRPC call), `gigya_jwt` 6 h, `refresh_token` does not rotate. Refresh when under
  ~60 s remain, never more often (`GetNewJWTToken` is rate-limited, `RESOURCE_EXHAUSTED`). Store the refresh token,
  never the password.
- `Lucid-Motors-WearOS-Companion-App`: its author gave permission to reuse their own code; attributed in `NOTICE`. What
  is used: the gateway workaround (it resets the stream if HTTP/2 `content-length` is sent, so `LucidClient` uses a
  streaming body and strips `Content-Length` / `Accept-Encoding`). No artwork is reused: all icons here are original.
- Garmin's `connectiq-android-sdk` sample is a read-only reference for the phone-side plumbing. Its code and bundled
  AAR are not copied; the app depends on Maven Central `com.garmin.connectiq:ciq-companion-app-sdk`.

## Naming, license and Garmin SDK rules
- Use "for Lucid" descriptively in tagline/README, not in the brand. Keep "Lucid", "Garmin", "Gravity", "Air" out of the
  product and repo name. No Lucid/Garmin logos, wordmarks, fonts or car art. Use "Connect IQ" only descriptively.
- Every README / About screen: "Unofficial. Not affiliated with, endorsed by, or sponsored by Lucid Motors or Garmin.
  Uses an undocumented API that may change or be withdrawn."
- **MIT** (matches both upstreams; permits sideloading and forks). `NOTICE` preserves upstream copyrights. The Garmin
  SDK terms are proprietary and the SDK is never in this repo; the MIT license covers only our own code.
- Do not commit the SDK, simulator, device profiles, Garmin sample apps, the bundled `monkeybrains-sdk-release.aar`, or
  code copied or adapted from the SDK samples. Users install the SDK with Garmin's SDK Manager and accept Garmin's
  license themselves; the Android SDK comes from Maven Central.
- No decompiled dumps or notes quoting SDK internals in `docs/`.
- No Garmin logos or implied affiliation; compatibility statements ("works with Venu 3") stay factual.

## Target devices
The Venu 3 is the tested target (real hardware). The manifest also lists `fenix7` and `venux1`; both build and were
checked in the simulator only, so the README calls them untested. Before any store release, trim the manifest to
watches a real owner has tried (store distribution covers every listed product).

- Every screen must work with touch and with buttons; no touch-only widgets.
- One codebase via resource qualifiers (e.g. `resources-round-454x454`) where layouts differ. Use fractions of the
  screen size, not pixels.
- Memory is the main constraint on MIP watches: keep the app small and bitmaps minimal; the glance limit (64 KB) is the
  tightest.
- Skip monochrome Instinct-class watches (tiny, no touch; would need a separate design).

Next models worth a tester:

| Model | Screen | Input | Why include it |
|---|---|---|---|
| **Venu 3S** or **vivoactive 5** | 390x390 round AMOLED | touch + buttons | Smaller round AMOLED; scaling check |
| **fenix 7 / FR255/955** | 240-280 px round MIP | buttons (touch on some) | Popular line; different input model |
| **Venu Sq 2** (optional) | 320x360 rectangular AMOLED | touch + button | Only rectangular layout |
| **Venu X1** | 448x486 rectangular AMOLED | touch + buttons | Needs API 6.0.3 (SDK 9.x) |

## Security
Repo:
- `.gitignore` covers `local.properties`, keystores, Garmin `developer_key*`, `.env`, build output.
- gitleaks pre-commit hook plus a GitHub Action; GitHub secret scanning and push protection on. Signing keys and
  credentials live outside the repo.
- Anonymize all fixtures and logs; no real VIN, email, or tokens in git. No secrets in CI unless required.

Android:
- Store only the refresh token, encrypted by an Android Keystore AES-GCM key. `allowBackup=false` and everything is
  excluded in `dataExtractionRules`.
- `usesCleartextTraffic=false`. No certificate pinning (the unofficial gateway could rotate certs).
- Never log tokens, email, or VIN; minimal dependencies.
- Accept Connect IQ messages only for this watch app's UUID.

Watch:
- Stores nothing secret. Confirm tap before unlock and climate on/off; lock has none by design. The watch's own
  passcode / lock is the device-side protection.

## Responsiveness
- The watch draws instantly from cached state, shows its age on the main screen (the glance has none), and updates when
  the phone replies or pushes; clear in-flight and error states (offline, auth expired, car asleep).
- The phone keeps the HTTP/2 connection and token warm during a session.
- Commands are accepted instantly but the car confirms 10-40 s later and a sleeping car needs 30 s+ to wake, so the UI
  shows pending states and the phone polls gently until the state changes.

## Roadmap
Anything that adds a watch<->phone message touches `docs/protocol.md`, the phone and the watch, so bump the protocol
version (or add optional fields that older watches ignore). The status data all comes from the same `GetUserVehicles`
call, so reading more of it adds no Lucid calls.

**Shipped:** Lucid API proof, repo and security setup, Android client, Venu 3 app (glance, main screen, actions), phone
bridge with a 5 minute background push, phone app as a status and settings screen. See the releases (v0.2.x).

**Now**
- Paged actions screen, with frunk/trunk open and close, horn and flash lights (#12). Built, awaiting a test on the
  car. Opening a closure is physical, so it gets a confirm tap like unlock.
- Watch icons: finish the set (#29). Frunk and trunk are done for the Air and the Gravity; horn and lights are
  still the plain drawings.

**Next**
- Battery preconditioning and a Climate details screen (#13).
- Charging: power readout in kW, start/stop, charge limit, charge port (#35).
- Details screen, read-only: tire pressure, windows, battery health, cell temperatures (#11).
- AC current limit (#14), research first.
- Release-build (R8) check of the Android app.

**Later**
- Multi-vehicle support (#33). Needs a tester with a second car; the WearOS app already stores a selected vehicle id.
- Watch face and complications (#34). A separate app type; decide after the above shows how the app gets used.
- Seat and steering wheel heat, and defrost, in the Climate details screen (#13). The WearOS app cycles seat and
  wheel levels with one tap.
- A demo mode with canned data, so a tester or store reviewer without a Lucid can try the app (the WearOS app has one).
- Refresh faster only while the watch app is open, the glance staying on the 5 minute push (#36). Research first.
- Optional biometric gate before unlock.
- Second watch model with a real owner.
- **Store release (only if going to stores):** re-read Garmin's SDK agreement and Developer Guidelines, Lucid's terms,
  Google Play policy on third-party logins and vehicle control; write a privacy policy; do a trademark search; trim the
  manifest to tested watches.

## Verification
- Android unit tests (transport, token refresh, store, push loop) plus a manual run on a physical phone.
- Venu 3 end-to-end on the watch; record tap-to-car latency.
- Run `gitleaks detect` over full history; verify backup/data extraction contains no tokens.

## Known limits
- **Single vehicle only.** `LucidClient.status()` takes the first entry of `GetUserVehicles` and every command targets
  that vehicle; the author has one car and cannot test more. See #33. The README says this.

## Open questions
- How long the Lucid refresh token stays valid.
- Whether Lucid's app and website terms allow this kind of client (the README discloses the risk either way).
