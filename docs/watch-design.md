# Watch layout design note (#55)

Status: built for 0.5.0 except the Charging page. The main screen, the page strip, the info and version pages, the
warning icons and the Access page below are in the app; the Charging page is planned (see "Next") and the questions
under "Open" are undecided.

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
   About        <- versions and the ages of the car's and the phone's data; tap to refresh
   Info         <- the car's numbers as text (see "Data inventory")
   MAIN         <- glance-level state, no controls
   Access       <- swipe up from Main: lock and the frequent actions
   ```
   The Up and Down buttons do the same as the swipes. Four dots down the left edge show the place in the strip; a
   Charging page below Access (see "Next") would make five.
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

7. **One Access page of seven icons**, a "flower": the lock in the middle (the one used most) and six round it at about
   0.17 x width each on the Venu 3: frunk and trunk on top, climate and close-all-windows at the sides, the lights and
   horn below. Lock, frunk, trunk and climate open a choice screen (so the choice never depends on cached state);
   the lights and windows act at once. Unlock, climate, horn, frunk, trunk and windows ask for a confirm tap; lock,
   lights and pet mode do not, and every send shows a toast so a tap is seen to count. Climate's choice screen is off,
   on (with a set point) and pet mode. Refresh moved to the About page (tap it).
8. **Dropped, on purpose.** A separate Climate page (defrost, max A/C, seat and steering wheel heat) and a Signals page:
   those are things you do from the driver's seat, where the car's own controls are, and the car's auto-climate may
   already switch them on when it preconditions. The phone app stays information only (no controls: Lucid's own app
   has them). Left out of the app altogether: panic alarm, sentry and security, software update, keys, and the
   window actions other than close-all. Windows are close-all only; the car ignores many commands in gear or while
   moving (see the README's Limitations).

## Next

- **A Charging page** below Access: start or stop in the middle and, round it, the charge limit, the AC current limit,
  battery preconditioning, the charge port, the schedule and V2X discharge (a mockup exists, not built). Each control
  needs testing on a car first, and the valid AC current values are not known yet (#14). Start and stop, port and
  preconditioning would ask for a confirm tap; the limit and current are pickers. The schedule is probably on and off
  only (the times are set in Lucid's app); discharge needs its own look.
- The 0.5.0 layout looked right in the simulator on the Venu X1 (rectangular) and the fenix 7 (small round); neither has
  been tried on a real watch.

## Open

- Whether lock, unlock, frunk and trunk keep their second choice screen. It adds a level; showing the current state and
  offering only the opposite action would remove it, and ties in with #38.
- Which info goes on which info page, and what counts as "needs attention" on Main. Open windows are not shown on the
  watch yet (#11).
- Back from the Access page: it exits the app, as the pages are not pushed screens. Returning to Main first is untried.
- Button-only watches cannot move the highlight on a strip page, since up/down page, so a touchscreen is a requirement
  (README, PLAN.md). Revisit if someone wants a button-only watch: the highlight could step with up and down and hand
  over to the next page at the ends.

## Data inventory

All of it is in the `GetVehicleState` reply the phone already reads; none needs a new Lucid call. Source:
`android/app/src/main/proto/vehicle_state_service.proto`. Which fields a given car fills in is unchecked beyond the
ones already used.

| Wanted | Fields | Shown today |
|---|---|---|
| Charge level | `battery.charge_percent` | Yes |
| Inside and outside temperature | `cabin.interior_temp`, `exterior_temp` | Yes |
| Plug and charge status | `charging.charge_state` | Plug icon only |
| Charge speed | `charging.charge_rate_kwh_precise`, `port_power` | Yes (kW while charging) |
| Tire pressures | `chassis.*_tire_pressure_bar` (4), warning and sensor flags, last-updated time | Yes (info, icon) |
| Doors, lids, port | `body.front_cargo`, `rear_cargo`, 4 doors, `charge_port` (open, closed, ajar) | Lids, doors |
| Windows | `body.window_position` (4), `all_windows_position` | Only to confirm close-all |
| Cell temperatures | `battery.min_cell_temp`, `max_cell_temp` | Yes (info page) |

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

Done in 0.5.0: the strip and gestures, Main, the Info and About pages, the warning icons, the Access page.
Still to do, each as its own issue: the Charging page (#35, #14), open windows on the watch (#11), the state line on the
choice screens (#38), and a check of the layout on real Venu X1 and fenix 7 hardware.
