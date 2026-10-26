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
| `cmd` | string | `status`, `lock`, `unlock`, `climate_on`, `climate_off` |
| `temp` | number | `climate_on` only, optional: cabin set point in Celsius (see below) |

- `status` returns the phone's cached state when it is under 4 s old, otherwise it fetches once.
- The watch asks for a confirm tap before it sends `unlock`, `climate_on` or `climate_off`. The phone does not repeat
  the confirmation. `lock` and `status` are sent without one.
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
| `tset` | number | The car's cabin set point in Celsius (see below) |
| `awake` | bool | Car awake |
| `age` | int | Seconds since the car last reported (from `last_updated_ms`) |
| `pending` | string | Short label while a command is in progress, e.g. `Waking`, `Unlocking`; absent when done |
| `err` | string | Error code, see below |

Status fields are omitted from an error reply that has no state to show.

`tset` is kept by the car even while climate is off, and is left out when the car reports none or a value outside
15..30. It reads back within about 0.4 F of what was set, so the watch rounds it to its own picker steps.

## Error codes

| `err` | Meaning | Watch shows |
|---|---|---|
| `auth` | Refresh token rejected; the user must log in on the phone | Open the phone app |
| `offline` | Phone has no route to Lucid | No connection |
| `rate` | Lucid is throttling (gRPC status 8) after retries | Try again in a minute |
| `timeout` | Command accepted but the car did not confirm within about 105 s | No confirmation yet |
| `wake` | Car did not wake | Car did not wake |
| `unknown` | Anything else | Something went wrong |

The phone never puts raw exception text in `err`.
