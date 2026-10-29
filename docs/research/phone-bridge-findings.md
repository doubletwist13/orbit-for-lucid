# Phone bridge findings

Evidence behind the decisions in [PLAN.md](../../PLAN.md). Timings are one set of tests on one car (a Pixel 10 Pro and a
Venu 3), and Lucid's latency is known to vary wildly.

## Why the phone makes the Lucid calls (not the watch)

Checked against the Connect IQ `Communications.makeWebRequest` docs (API 1.3.0 page, 2026-10-24). Since CIQ 6.0.3 it
accepts a String or ByteArray `:body`, a string Content-Type, and an octet-stream response as a ByteArray, so binary
protobuf is no longer impossible on new devices. It is still a poor fit for Lucid's gRPC gateway:

- The docs say nothing about HTTP/2, trailers (`grpc-status` arrives in trailers) or `TE: trailers`; requests are
  relayed by Garmin Connect Mobile or Wi-Fi, so behaviour is unknown.
- Binary responses require `Content-Type: application/octet-stream`; gRPC replies `application/grpc`, which would
  likely error as `UNSUPPORTED_CONTENT_TYPE_IN_RESPONSE`.
- The gateway rejects requests carrying `content-length`; the CIQ stack probably adds it.
- It needs CIQ 6.0.3+, but the Venu 3 is API level 5.2, so binary request bodies are not available on the primary
  target. It would also put the refresh token and MFA login on the watch, against the security goals.

## Waking a killed phone app

Answered 2026-10-25. A killed app is not woken by a watch message: the watch shows "Delivered" (Garmin Connect Mobile
accepted it) but nothing runs, and a manifest receiver for the SDK's `INCOMING_MESSAGE` broadcast never fires (the SDK
registers it at runtime, and delivery needs a prior bound-service registration).

A `connectedDevice` foreground service that only keeps the SDK registered works: with the app swiped away and the
screen off, a watch tap reached the phone and the reply came back (about 0.4 s foreground, 1.7 s background; one sample
each). The service holds an idle BLE link and makes no Lucid calls itself.

- Needs `FOREGROUND_SERVICE_CONNECTED_DEVICE` plus `CHANGE_NETWORK_STATE` (the type's prerequisite) and a runtime
  `POST_NOTIFICATIONS` prompt (asked on first launch; declining only hides the notification), and a persistent "Watch
  link active" notification (min importance).
- No service on/off switch (too easy to hit by accident, and logging out already stops car control), and no prompt for
  "unrestricted" battery use (the swiped-away app worked on the Pixel at the default setting).
- Reboot: a `BOOT_COMPLETED` receiver restarts the service after the first unlock (tested; it arrived about 2 min after
  a slow boot). A force-stopped app gets no boot broadcast until opened by hand.
- The watch fails a request with `nolink` after 8 s of silence.
- Battery results are in #10.

## Command timings and polling

- After a command the phone wakes the car if needed, sends, then polls 3 times at 5 s and then every 10 s until the
  expected state change (about 105 s cap). Sustained 3-5 s polling triggered `RESOURCE_EXHAUSTED` (see
  `phase1-lucid-api-findings.md`). The WearOS companion polls every 7 s, about 500 calls an hour; not a pattern to copy.
- Unlock from a sleeping car took about 13 s without car wifi and 9-10 s with it; lock on an awake car 1-2 s.
- Rate-limit safety: a single in-memory token, single-flight refresh, retries of 2/4/8 s on `RESOURCE_EXHAUSTED`, and a
  short (4 s) status cache.

## The 5 minute status push (#7)

The phone fetches status on a flat 5 minute timer in `Controller` (the app process the foreground service keeps alive)
and pushes the reply to the watch, which wakes a small background handler
(`Background.registerForPhoneAppMessageEvent`) that writes it into the `Application.Storage` entry the glance and the
app already read.

- The Lucid API has no push or streaming call, so the interval is also the worst-case delay before the watch learns the
  car woke. A slower interval while the car sleeps was rejected for that reason.
- A status fetch reads Lucid's cloud copy and does not wake the car (waking is the separate `WakeupVehicle` call).
- The phone is the one place that respects the gateway's rate limit (polling every 4-6 s hit status 8; every 5 minutes
  is far below that). After a `rate` error it doubles the interval for a while.
- Storage (32 KB per value) is not the limit. The glance (64 KB) and the background handler have small memory, so keep
  the saved state compact and the handler free of the app's big classes.
- The watch's age line is the car's data age, so it cannot show whether pushes arrive. The watch saves the time of the
  last status from the phone (`pt`), and the info screen shows both times (#16).
- v0.2.0 skipped a push unless a watch was "connected", and that check was always false, so no push was ever sent (#15).
  Pushing to an absent watch costs one Lucid call and a failed send, which is cheaper than a gate that can break
  silently.
- A "charging started" notification from the background handler (`Notifications.showNotification`, API 5.1) was tried
  and dropped: it did not show on the author's watch, and the official Lucid app already sends one. If it comes back,
  the phone should say whether to alert (a setting in the phone app), not the watch.

## Units

The Lucid API exposes no unit preference, so the phone always sends and accepts SI (Celsius, bar) and the watch converts
for display from its own `System.getDeviceSettings()` units. The Fahrenheit set point list steps in whole degrees and
converts back to Celsius before sending; that conversion needs a unit test.

## Simulator findings (2026-10-27)

The simulator segfaults on Fedora because it exports its own FreeType symbols; it works in an Ubuntu 22.04 container
(see CLAUDE.md). The main, actions, info, climate and set point screens were checked on the fenix 7 (buttons reach every
item, including the small info button) and the Venu X1 (rectangular layout fits, touch works). That found two problems,
both fixed: error text overlapping the ring on a small or rectangular screen (messages are now at most about 15
characters, and the font drops a size if one is still too wide), and Garmin's `Picker` for the set point, which drew a
white background on the fenix 7 and cut off its OK button on the X1 (it is now a stock `Menu2` list). The simulator
draws stock menus differently from a real watch, so it checks layout and input only.

The action screens use a raw `InputDelegate` (tap, select, up/down, vertical swipe): a `BehaviorDelegate` delivered one
tap as both a tap and a select, which sent lock when unlock was tapped.
