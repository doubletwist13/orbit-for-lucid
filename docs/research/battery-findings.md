# Battery cost of the phone and watch (#46, #47)

Measured 2026-10-30 and 2026-10-31 on one Pixel 10 Pro (4635 mAh) and one Venu 3, with the adaptive build: the phone
reads the car every 60 s asleep, 30 s awake or charging, and 10 s for 2 minutes after a watch request, and pushes
only a change to the watch (see `docs/protocol.md`). Phone numbers are Android's modelled per-app estimates from
`dumpsys batterystats` after a `--reset`; watch numbers are the percentage shown by the watch.

## Orbit

| Run | Window | Watch | Phone, Orbit only |
|---|---|---|---|
| Daytime, normal use | 5 h 29 min | 92% to 89% (0.55%/h) | 33.7 mAh (6.4 mAh/h, 0.14%/h) |
| Overnight, car charging, watch asleep | 8 h 10 min | 100% to 95% (0.6%/h) | 0.217 mAh (0.03 mAh/h) |

- The watch drains at the same rate with and without the faster reads and pushes (earlier idle run: 0.55%/h).
- The phone figure depends on the network. The daytime run included cellular use (44 radio wakeups, 59 s of radio
  time), and Android charges most of its estimate to the radio. Overnight the phone was on Wi-Fi (137 wakeups, 13 s of
  CPU, no cellular use) and Orbit cost almost nothing.
- For comparison, the earlier 5-minute push build measured about 0.02% over 14 h on the phone.
- The daytime phone figure was 1.5% of the phone's total drain in that window.

## The official Lucid app, for comparison

Read from the same overnight `batterystats` (8 h 16 min on battery, app version 2.19.0). The app is not ours and
nothing here can be changed from outside it; it is recorded because it sets the bar Orbit is compared with.

- 10.9 mAh in the window, about 50 times Orbit's overnight figure.
- It scanned for Bluetooth LE devices for the whole window: 44,120 scan results, flagged by Android as an unoptimized
  scan running for 8 h 9 min. The scanning uses the AltBeacon library's `ScanJob`, which ran 677 times, held partial
  wake locks for about 53 minutes and ran 1 h 57 min in total. This looks like the phone-as-key and proximity feature,
  and it accounts for most of the drain.
- It also sent 45 MB and received 9 MB over Wi-Fi (about 92,000 packets sent), with an analytics flush job that ran
  about 360 times, and 315 alarm wakeups.
- Accubattery Pro showed it at 27.9 mAh (448 mA while active) in a different window, which fits an app that is both
  scanning in the background and used on screen.

Orbit has none of these costs: it makes no Bluetooth scans (the Connect IQ link is managed by the Garmin Connect
Mobile app), uses no location, and wakes only for its own timer, the watch's messages and the 5-minute push.

## Method notes

- `adb shell dumpsys batterystats --reset` at the start, then read the per-app section (`UID u0a...` lines and the
  app's own block) at the end. Charging can clear the stats once the battery is high, so read before it gets there.
- The per-app mAh is an estimate, not a measurement; the radio and wake locks are the least reliable parts.
- One phone, one watch, one car, one network each time. Treat the numbers as an order of magnitude.
