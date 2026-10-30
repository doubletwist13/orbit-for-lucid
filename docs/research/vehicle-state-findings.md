# GetVehicleState polling (#46)

Tested 2026-10-30 against one real account and one vehicle (a Lucid Air, asleep in a garage) with a throwaway script
using `python-lucidmotors`. The script is not in the repo. No ids, tokens or vehicle data are recorded here.

The WearOS companion app's PR (DeaneG1967/Lucid-Motors-WearOS-Companion-App#14) reports that
`VehicleStateService/GetVehicleState(vehicle_id)` has no rate limit, unlike `LoginSession/GetUserVehicles`, which
allows about 5 calls a minute. This checks it from our side.

## Result

One login and one `GetUserVehicles` call, then `GetVehicleState` for the car, in three phases:

| Interval | Duration | Calls ok | Errors | Median latency |
|---|---|---|---|---|
| 10 s | 60 s | 6 | 0 | 74 ms |
| 3 s | 60 s | 20 | 0 | 77 ms |
| 1 s | 90 s | 90 | 0 | 77 ms |

- 116 calls in about 3.5 minutes with no error, no status 8 and no slowdown, including 90 calls at 1 s.
- The car was asleep (power state 1) the whole time and the report time never changed. A sleeping car's cloud copy is
  frozen, so polling it faster shows nothing new. The WearOS PR saw the same, and saw a report every 1-4 s while awake.
- The call did not wake the car.

### The same car, charging

The car was plugged in and reported power state 6 (sleep-charge), the state the WearOS PR says a charging car sits in
while still reporting live. Same three phases:

| Interval | Duration | Calls ok | Errors | Median latency | Distinct report times |
|---|---|---|---|---|---|
| 10 s | 60 s | 6 | 0 | 75 ms | 6 |
| 3 s | 60 s | 20 | 0 | 76 ms | 20 |
| 1 s | 90 s | 90 | 0 | 78 ms | 76 |

- No errors and no slowdown here either.
- The report time moved on every poll at 3 s and on 76 of 90 polls at 1 s, so a charging car reports about every
  second. Polling faster than about 1 s adds nothing.
- Power state 6 is therefore "reachable", not "asleep". `LucidClient.toStatus()` treats it as awake (`power != SLEEP`),
  which is right for this car, but a rule that calls 6 asleep would be wrong. See #44.

## Not covered

- Whether a lock, frunk or climate change shows up on the next poll, and a car that is awake but not charging.
- Only about 3.5 minutes per car state. A longer run could still find a limit that builds up, so the existing
  back-off stays.
- The phone's own client, not Python's. Orbit's gRPC transport has its own quirks (see `CLAUDE.md`).
- `GetVehicleStateResponse` carries `state` but not `config`, so the nickname, model and frunk strut still have to
  come from `GetUserVehicles`.
