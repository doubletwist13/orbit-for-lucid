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

- Commands: `status`, `lock`, `unlock`, `climate_on`, `climate_off`, `frunk_open`, `frunk_close`, `trunk_open`,
  `trunk_close`, `horn`, `flash`.
- `status` returns the phone's cached state when it is under 4 s old, otherwise it fetches once.
- The watch asks for a confirm tap before it sends `unlock`, `climate_on`, `climate_off`, `horn` or any frunk
  or trunk command. The phone does not repeat the confirmation. `lock`, `flash` and `status` are sent without one.
- `horn` and `flash` (flash the lights) have no state to read back: Lucid's answer to both is an empty message, and
  nothing in the car's state is known to report them. The phone wakes the car if needed, sends, and answers `ok` at
  once, which means only that Lucid accepted the request, not that the car honked or flashed. Progress text says
  "Sending", never "Honking". The frunk and trunk commands wait for the car to report the lid open or closed, like `lock`; a car
  that reports no state for a lid answers after the next status fetch.
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
| `locked` | bool | Doors locked |
| `charge` | string | Charge state, e.g. `CABLE_CONNECTED`; `NOT_CONNECTED` when unplugged |
| `hvac` | bool | Climate/precondition on |
| `frunk`, `trunk` | bool | Frunk or trunk open (ajar counts as open); omitted when the car reports no state for it |
| `fpower` | bool | The frunk has a power strut, so the car can close it; `false` for a gas strut, which only opens |
| `model` | string | `AIR` or `GRAVITY`, for the car drawing on the frunk and trunk icons; omitted for any other model |
| `tset` | number | The car's cabin set point in Celsius (see below) |
| `tin`, `tout` | number | Cabin and outside air temperature in Celsius; each omitted if unreported or outside -50..70 |
| `awake` | bool | Car awake |
| `age` | int | Seconds since the car last reported (from `last_updated_ms`) |
| `pending` | string | Short label while a command is in progress, e.g. `Waking`, `Unlocking`; absent when done |
| `err` | string | Error code, see below |

Status fields are omitted from an error reply that has no state to show.

`tset` is kept by the car even while climate is off, and is left out when the car reports none or a value outside
15..30. It reads back within about 0.4 F of what was set, so the watch rounds it to its own list steps.

## Pushed status

About every 5 minutes the phone fetches status and sends it to the watch unasked, so the glance and the app open on
fresh data. Android may delay the timer while the phone sleeps. The phone does not check that a watch is connected
first: a send to an absent watch only fails, and the status is still fetched. It is a reply with the same status
fields and `age`, but `push` is `true` and `id` is `0`; it answers nothing, so the watch skips the request-id check,
merges it into its saved state and saves it. When the app is closed the same message wakes the app's background
service (`Background.registerForPhoneAppMessageEvent`), which does the same merge and exits.

- The interval is fixed. After a rate-limit answer from Lucid the phone waits twice as long, up to an hour, and goes
  back to 5 minutes after the next success. A setting in the phone app turns pushes off.
- A push is skipped while logged out, and a failed fetch sends nothing.
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
| `unknown` | Anything else | Unknown error |

The phone never puts raw exception text in `err`. The watch itself shows "No phone link" when the phone does not
answer at all; that is not an `err` code.
