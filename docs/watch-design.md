# Watch layout design note (#55)

Status: partly built. The main screen, the page strip, the info pages and the warning icons below are in the app; the
actions pages still use the old layout and the questions under "Open" are undecided.

## Principles

From Google's Wear OS guidance, third-party summaries of Apple's, and menu research (Garmin's own pages were not
readable, so check them for the Venu X1 and fenix):

- Touch targets of at least 48 dp (about 0.12 x width on the 454 px Venu 3). Round screens lose about 22% of the space,
  so use fractions of width and test the smallest watch (fenix 7, 260 px).
- Readable at a glance, and few taps to anything common. Keep the hierarchy to two or three levels.
- Breadth beats depth in the menu research (mostly phone and car studies, not wrists).
- Do not rely on swipes alone: every gesture has a button equivalent.
- Swipe right means back on every screen.

## Decided

1. **Vertical paging, done by hand.** Pages stack vertically and swipe up or down moves between them, like the stock
   Garmin screens. Swipe right is always back, and a swipe past the first or last page does nothing (no wrap). Not
   `WatchUi.ViewLoop`: it draws its own blue page indicator, which cannot be turned off.
2. **Main is the home page, in the middle of a strip.** It opens there, from the glance or the launcher.
   ```
   About        <- versions and the ages of the car's and the phone's data
   Info         <- the car's numbers as text (see "Data inventory")
   MAIN         <- glance-level state, no controls
   Actions 1    <- swipe up from Main
   Actions 2
   ```
   The Up and Down buttons do the same as the swipes. Five dots down the left edge show the place in the strip.
3. **The top right button on Main opens the lock/unlock choice**, and so does a tap on the bezel mark or the padlock
   pair beside it. Unlock still gets the confirm tap. Elsewhere a tap on Main does nothing, so a wrist brush cannot open
   anything (Main's delegate does not call the base class, which would turn a tap into the button's select).
4. **Slices to reach the neighbours.** A small filled slice at the top ("i") and bottom (three dots) of Main is a tap
   target for the page above and below, with a zone larger than what is drawn.
5. **Main's layout.** The charge is a ring round the number (top left). Temperatures (interior and exterior) sit under
   the lock button's mark, and the charge power with a bolt in the space between the ring and the mark, only while
   charging. The state row has the lock, plug and climate in green, and in amber an open frunk, trunk or door and a
   tyre warning, only while they apply; an unattended-occupant alert is a red icon first in the row and takes over the
   glance title. The status line shows only a pending command or an error. The age of the data is on About, and a stale
   screen is dimmed.
6. **Memory is not the limit.** Measured on the Venu 3: the app uses 37 of 763 KB, the glance 19 of 59 KB and the
   background handler 14 of 59 KB, with about 40 fields saved. More fields are fine until a single phone message gets
   too large, which is untested. The phone has a details screen with the rest of the car's data, and it works out
   efficiency from the odometer and battery energy, since the API has no trip or efficiency data.

## Open

- The Actions pages. Leaning towards pages by topic: Access (lock, frunk, trunk, charge port, windows), Climate (on/off,
  set point, defrost, seat and steering heat, preconditioning), Charging (start/stop, limit, AC current limit) and
  Signals (horn, lights). Inside a page, a "flower" of one icon in the middle and six round it fits seven items at about
  0.17 x width each on the Venu 3. The API also offers schedule, discharge, panic alarm, software update and sentry
  controls, which are better kept on the phone. Each control needs testing on a car first.
- Refresh stays on the second Actions page for now; opening the app and the 10 s window after a request already refresh.
- Whether lock, unlock, frunk and trunk keep their second choice screen. It adds a level; showing the current state and
  offering only the opposite action would remove it, and ties in with #38.
- Which info goes on which info page, and what counts as "needs attention" on Main.
- Per-watch layouts: the Venu X1 (rectangular) and the fenix 7 (small round) are untested for any of this.
- Back from an Actions page: it exits the app, as the pages are not pushed screens. Returning to Main first is untried.
- Button-only watches cannot move the highlight on a strip page, since up/down page; a touch screen reaches every icon.

## Data inventory

All of it is in the `GetVehicleState` reply the phone already reads; none needs a new Lucid call. Source:
`android/app/src/main/proto/vehicle_state_service.proto`. Which fields a given car fills in is unchecked beyond the
ones already used.

| Wanted | Fields | Shown today |
|---|---|---|
| Charge level | `battery.charge_percent` | Yes |
| Inside and outside temperature | `cabin.interior_temp`, `exterior_temp` | Yes |
| Plug and charge status | `charging.charge_state` | Plug icon only |
| Charge speed | `charging.charge_rate_kwh_precise`, `port_power` | No |
| Tire pressures | `chassis.*_tire_pressure_bar` (4), warning and sensor flags, last-updated time | No |
| Doors, frunk, trunk, port | `body.front_cargo`, `rear_cargo`, 4 doors, `charge_port` (open, closed, ajar) | Lids |
| Windows | `body.window_position` (4), `all_windows_position` | No |
| Cell temperatures | `battery.min_cell_temp`, `max_cell_temp` | No |

Other fields that could be shown or used:

- Range: `remaining_range`; energy: `kwhr`, `capacity_kwhr`.
- Charging: `session_minutes_remaining`, `charge_limit_percent`, `charge_session_kwh`, `charge_scheduled_time`,
  `scheduled_charge`, `cable_lock`, `energy_type`, `active_session_ac_current_limit`.
- Battery: `battery_health`, `battery_health_level`, `preconditioning_status`, `preconditioning_time_remaining`, low
  and critical charge warnings.
- Climate: HVAC power mode, defrost, keep-climate and pet mode, seat heat and vent levels, steering wheel and rear
  window heat, set temperature.
- Body: alarm status and mode, keyfob battery, mirror fold, air suspension and ride height, unattended-occupant
  detection.
- Chassis and car: odometer, speed, software version, power state.
- Software update: version available, download and install progress, scheduled start.
- Location (`Gps`): not for the watch; the phone link carries no location (`docs/protocol.md`).

Derived on the phone:

- An open-items summary ("Frunk, rear left door") from the door, cargo and window fields.
- Tire status: the lowest pressure and any warning flag.
- Charge finish time: now plus `session_minutes_remaining`.
- A charge-rate trend would need the phone to keep a few samples; nothing is stored today.

Candidates for an attention line on Main: an open door, window, frunk or trunk; a tire warning; an unattended
occupant; a charge fault; a software update available.

### Limits to design around

- The glance has 64 KB and the background push handler little more, so reply fields stay compact and are summarized on
  the phone (for example `open: "FR,RL"`, not raw enums). See `docs/protocol.md` and #11.
- The glance needs only the charge level, lock state and plug state (and perhaps charge speed), but today it loads the
  whole saved state (`"s"`) to draw them, as does the background push handler. One option is to split it: `"s"` keeps
  the glance fields and the main screen's, and the Info pages read a separate `"d"` that the glance never touches. The
  background handler would write `"d"` without reading the old copy, which needs each push to carry the full details
  block. This is an idea, not a decision, and we may not need it: the details may stay small enough that one
  dictionary is fine. If we do hit the limits, weigh the other ways of handling it before building this (compact or
  fewer fields, requesting details from the phone only when an Info page opens, a smaller push), and measure memory
  on a watch first.
- Tire pressure has its own last-updated time, so it can be older than the rest of the state.
- Some fields will be empty on some cars or while the car sleeps; omit them in the reply, as `tin` and `tout` are.

## Build order

To be written once the open questions are settled. It will split into issues: the strip and gestures, the Info pages,
each new action page, and per-watch layout checks.
