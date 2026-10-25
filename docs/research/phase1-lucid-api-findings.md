# Phase 1: Lucid API findings

Tested 2026-10-24 against one real account (MFA enabled) and one vehicle (Lucid Air, parked in a garage) using
`python-lucidmotors` 1.4.x. No tokens, VINs, emails or locations are recorded here.

## Authentication

- `LucidAPI.login(username, password)` succeeds for an MFA-enabled account. Neither `python-lucidmotors` nor
  `ha-lucidmotors` has an MFA step, and Lucid did not prompt for one.
- `login_with_refresh_token(token)` works. The app needs the password exactly once (first login); afterwards only the
  refresh token is stored.
- Each login or refresh yields an `id_token` valid ~5 minutes (observed `session_time_remaining` 4:59). Refreshing twice
  in quick succession returned an equivalent session. Refresh only when under ~60-90 s remain.
- Refresh token lifetime on the server: unknown. Re-test after days/weeks. Handle `UNAUTHENTICATED` by asking the user
  to log in again.

## Status (read)

`fetch_vehicles()` returns cached state even when the car is asleep. Fields relevant to the watch:

| Need | Field | Notes |
|---|---|---|
| SOC | `state.battery.charge_percent` | float, e.g. 87.0 |
| Range | `state.battery.remaining_range` | |
| Locked | `state.body.door_locks` | `LOCK_STATE_UNLOCKED`=1, `LOCK_STATE_LOCKED`=2 |
| Plugged/charging | `state.charging.charge_state` | e.g. `CHARGE_STATE_CABLE_CONNECTED` |
| Charge power | `state.charging.port_power` | |
| Climate on | `state.hvac.power` | `HVAC_OFF`=2, `HVAC_PRECONDITION`=3 |
| Precondition | `state.hvac.precondition_status` | |
| Awake | `lucid.vehicle_is_awake(v)`, `state.power`, `state.cloud_connection` | |
| Freshness | `state.last_updated_ms` | epoch ms; show data age |
| Nickname | `config.nickname` | |

Sensitive fields to never log or display carelessly: `state.gps`, VIN, owner name/email.

## Commands

| Action | Call | Observed behaviour |
|---|---|---|
| Wake | `wakeup_vehicle(v)` | Accepted at once; car needed >36 s to wake. Commands sent while asleep were ignored. |
| Unlock | `doors_unlock(v)` | Accepted in ~0.1 s; state flipped to UNLOCKED 12-16 s later. |
| Lock | `doors_lock(v)` | Accepted in ~0.1 s; state read LOCKED within ~40 s (not yet flipped at 16 s). |
| Precondition on | `set_cabin_temperature(v, 21.0)` | `hvac.power`=PRECONDITION, status active within ~10 s. |
| Precondition off | `set_cabin_temperature(v, None)` | `hvac.power`=OFF ~30 s later; status then `TIMEOUT`. |

`battery_precon_*` is a separate battery warm-up and was not tested.

## Implications for the app

1. "Command accepted" is not "done". The watch shows a pending state ("Unlocking...") and the phone polls status until
   the expected state appears, with a ~90 s cap and a clear "no confirmation yet" message. Poll every 10 s, not
   3-4 s: in the Android app (2026-10-24) `GetUserVehicles` returned RESOURCE_EXHAUSTED (status 8) at 4-6 s polling
   after a few commands in a row, and a 2/4/8 s retry did not clear it. With a 10 s status cache and 10 s polling it
   stopped. The gateway's limit is unmeasured; python-lucidmotors caches this call for 15 s.
2. A sleeping car needs wake first, and wake can take 30 s or more. Show "Waking car..." rather than a generic spinner.
   Consider sending wake once and retrying the command after `power`/`cloud_connection` indicates awake.
3. Show data age from `last_updated_ms`.
4. Command latency from the app is tiny; the delay is the car's. Per-command polling needs the rate-limit care already
   in the plan.
5. Treat unlock as the only action needing a confirm tap; lock and precondition can be one tap.

## Not yet tested

- Refresh token longevity and revocation behaviour.
- Behaviour when the car is away from home (cellular), rate-limit errors, a second concurrent session.
- `battery_precon_*`, charge control, frunk/trunk/horn/lights (out of v1 scope).
