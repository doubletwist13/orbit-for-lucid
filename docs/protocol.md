# Watch <-> phone protocol (v1)

Messages are small Connect IQ dictionaries sent over Garmin Connect Mobile (BLE). The watch holds no secrets and the
phone returns display data only: no VIN, location, owner data or tokens ever cross this link.

The phone accepts messages only through the Connect IQ SDK registration for this watch app's UUID. It ignores any
message whose `v` it does not know.

## Watch to phone (request)

| Key | Type | Meaning |
|---|---|---|
| `v` | int | Protocol version, `1` |
| `id` | int | Request id chosen by the watch; echoed in every reply so stale replies can be dropped |
| `cmd` | string | One of the commands below |
| `temp` | number | `climate_on` only, optional: cabin set point in Celsius (see below) |
| `lim` | int | `limit` only: the charge limit in percent, clamped to 50..100; a request without a number is `badcmd` |
| `wv` | string | Optional: the watch app's version, e.g. `0.3.0`, shown on the phone app's status screen |

- Commands: `status`, `lock`, `unlock`, `climate_on`, `climate_off`, `frunk_open`, `frunk_close`, `trunk_open`,
  `trunk_close`, `horn`, `flash`, `windows_close`, `pet_on`, `pet_off`, `charge_start`, `charge_stop`, `limit`,
  `port_open`, `port_close`.
- `status` returns the phone's cached state when it is under 4 s old, otherwise it fetches once.
- The watch asks for a confirm tap before it sends `unlock`, `climate_on`, `climate_off`, `horn`, `windows_close`, any
  frunk or trunk command, or any charging command (`charge_start`, `charge_stop`, `limit`, `port_open`, `port_close`).
  The phone does not repeat the confirmation. `lock`, `flash` and `status` are sent without one; the watch shows a
  toast for those and for the confirmed ones once sent.
- The charging commands wait for the car to report the change, like `lock`. `charge_start` is done when the charge state
  is `CHARGING` and `charge_stop` when it is anything else. The phone refuses a `charge_start` it knows cannot work,
  from the status it reads just before sending: `nocable` with no cable, `atlimit` at or above the limit (skipped when
  the car reports no limit). Any other state that stops a start (a charger not ready, a fault) ends as the `timeout`
  error. `limit` is done when the reported limit equals `lim`. `port_open` and `port_close` wait for the port to
  report open (ajar counts) or closed, and a car that reports no port state answers after the next status fetch.
- `horn` and `flash` (flash the lights) have no state to read back: Lucid's answer to both is an empty message, and
  nothing in the car's state is known to report them. The phone wakes the car if needed, sends, and answers `ok` at
  once, which means only that Lucid accepted the request, not that the car honked or flashed. Progress text says
  "Sending", never "Honking". `windows_close` closes all four windows and waits for the car to report them closed (each
  one fully closed or at its upper stop, or the "all windows" value); a car that reports no window state answers after
  the next status fetch. The API's other window choices (vent, open all) are not offered. The frunk and trunk commands
  wait for the car to report the lid open or closed, like
  `lock`; a car that reports no state for a lid answers after the next status fetch.
- Lucid accepts a command without saying whether the car will act on it: many are ignored while the car is in gear (D
  or R) or moving. For the commands that wait for the car to report a change, that ends as the `timeout` error after
  about 105 s; for the ones that cannot be read back (`horn`, `flash`) nothing says it was ignored. The car's gear
  position is in the state (`gear_position`) but the phone does not check it before sending.
- `temp` is clamped to 15..30 by the phone and defaults to 21 when missing or not a number. It is also sent while
  climate is already on, to change the set point.
- `soc` is left out of a reply when the car's value is outside 0..100; the watch then shows `--`.
- A new request replaces the one in flight; the phone drops the older command's polling but not the command itself.

## Phone to watch (reply)

A command gets an immediate reply with `pending` set, then further replies with the same `id` as the car catches up
(the car confirms a change 10-40 s after accepting it, and a sleeping car needs 30 s or more to wake). The last reply
for an `id` has `pending` absent or empty.

| Key | Type | Meaning |
|---|---|---|
| `v` | int | `1` |
| `id` | int | Echo of the request id |
| `ok` | bool | `false` when `err` is set |
| `soc` | int | State of charge, percent |
| `locked` | bool | Doors locked; omitted when the car's lock state is unknown (the watch draws an amber `?` padlock) |
| `charge` | string | Charge state, e.g. `CABLE_CONNECTED`; `NOT_CONNECTED` when unplugged |
| `hvac` | bool | Climate/precondition on |
| `frunk`, `trunk` | bool | Frunk or trunk open (ajar counts as open); omitted when the car reports no state for it |
| `fpower` | bool | The frunk has a power strut, so the car can close it; `false` for a gas strut, which only opens |
| `rate` | int | Charging power in whole kW, only while charging, from 0.5 kW to the model's peak (see below) |
| `lim` | int | The charge limit in percent; omitted when the car reports none or a value outside 50..100 |
| `port` | bool | Charge port open (ajar counts as open); omitted when the car reports no state for it |
| `door` | bool | Any of the four doors open (ajar counts); sent only when `true` |
| `tire` | bool | The car's own tyre pressure warning (soft or hard) is on for a wheel; sent only when `true` |
| `occ` | bool | Unattended-occupant detection is in any warning stage; sent only when `true` (watch: red icon) |
| `pet` | bool | Pet mode is on; sent only when `true`. `pet_on`, `pet_off` wait for the car's report (no confirm) |
| `tp` | array of 4 | Tyre pressures in bar (front left and right, rear left and right), 2 decimals; see below |
| `cmin`, `cmax` | int | Coolest and warmest battery cell, Celsius, rounded; omitted if unreported or outside -40..100 |
| `odo` | int | Odometer in km, rounded; the watch converts to miles by its own distance setting |
| `soh` | number | Battery health in percent (95.0 is 95%), to 1 decimal; omitted when unreported |
| `model` | string | `AIR` or `GRAVITY`, for the car drawing on the frunk and trunk icons; omitted for any other model |
| `tset` | number | The car's cabin set point in Celsius (see below) |
| `tin`, `tout` | number | Cabin and outside air temperature in Celsius; each omitted if unreported or outside -50..70 |
| `awake` | bool | Car awake |
| `age` | int | Seconds since the car last reported (from `last_updated_ms`) |
| `pv` | string | The phone app's version, in every reply and push, shown on the watch's info screen |
| `pending` | string | Short label while a command is in progress, e.g. `Waking`, `Unlocking`; absent when done |
| `err` | string | Error code, see below |

Status fields are omitted from an error reply that has no state to show.

While the car gives power out, `charge` is `DISCHARGING` (or `PREPARING_DISCHARGE` just before) and there is no `rate`:
which field carries that power is not known yet. The watch shows an outlined bolt with a red arrow and offers no
start or stop.

`rate` is left out above the model's peak, which is a bad reading: Air 350 kW, Gravity 450 kW, any other model 2000 kW.
`tp` is left out unless all four pressures are plausible (0.5 to 6 bar), so a pressure is never shown for the wrong
wheel. The car's `remaining_range` is in kilometres, as a rated range scaled by the charge, and is not sent.

`tset` is kept by the car even while climate is off, and is left out when the car reports none or a value outside
15..30. It reads back within about 0.4 F of what was set, so the watch rounds it to its own list steps.

## Pushed status

The phone reads the car on a timer and sends the watch a status unasked when something the watch shows has changed,
so the glance and the app open on fresh data. Android may delay the timer while the phone sleeps. The phone does not
check that a watch is connected first: a send to an absent watch only fails, and the status is still read. It is a
reply with the same status fields and `age`, but `push` is `true` and `id` is `0`; it answers nothing, so the watch
skips the request-id check, merges it into its saved state and saves it. When the app is closed the same message wakes
the app's background service (`Background.registerForPhoneAppMessageEvent`), which does the same merge and exits.

- The read interval depends on the car's last state: 60 s asleep, 30 s awake or charging, and 10 s for 2 minutes after
  the watch last sent a request. A change is pushed at most once a minute (every 10 s in that window); with no change
  the phone still pushes every 5 minutes. Temperatures count as changed only per whole degree, and `age` is ignored.
  After a rate-limit answer from Lucid the phone waits twice as long, up to an hour, and goes back to the normal
  interval after the next success. A setting in the phone app turns pushes off.
- A push is skipped while logged out, and a failed fetch sends nothing. A push that fails to reach the watch is not
  retried on the next read; the 5-minute push is the retry.
- A push carries no error and no `pending`.

## Error codes

| `err` | Meaning | Watch shows |
|---|---|---|
| `auth` | Refresh token rejected; the user must log in on the phone | Open phone app |
| `offline` | Phone has no route to Lucid | No connection |
| `rate` | Lucid is throttling (gRPC status 8) after retries | Try again soon |
| `timeout` | Command accepted but the car did not confirm within about 105 s | Not confirmed |
| `wake` | Car did not wake | Car didn't wake |
| `unsupported` | `frunk_close` on a car whose frunk has a gas strut | Can't do that |
| `novehicle` | The Lucid account lists no vehicle | No car found |
| `badcmd` | The phone does not know the `cmd` (older than the watch app), or `limit` has no number | Update phone |
| `nocable` | `charge_start` while the car reports no cable (`NOT_CONNECTED`); nothing is sent | No cable |
| `atlimit` | `charge_start` while the charge is at or above the limit; nothing is sent | At limit |
| `unknown` | Anything else | Try again later |

The phone never puts raw exception text in `err`. The watch itself shows "No phone link" when the phone does not
answer at all; that is not an `err` code.
