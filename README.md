# Spidey Tracker

A crowd-sourced sighting map with a patrol tracker attached. Drop a pin when you
see something, confirm what other people saw, and watch hot zones form and fade
on their own.

v0: no backend, no accounts. Everything lives in `localStorage`, and the map is
seeded so it is alive the first time you open it.

```bash
npm install
npm run dev
```

Open it on a phone (or a narrow browser window) — it is built mobile-first and
installs as a PWA.

**Installing it on a phone, with home screen widgets for iOS and Android:** see
[`INSTALL.md`](INSTALL.md).

One model, three shells: the web app here, the native Android app in
[`android/`](android/), and a native iOS project in [`ios/`](ios/) that wraps
this same build with Capacitor. All three carry the same arcade interface and
the same features — sightings with photos, heat filters, patrols, the Bugle,
sound and spidey-sense.

```bash
npm run build:ios   # web build + sync into the Xcode project (build it on a Mac)
```

## How it works

### Confidence decay

Every sighting has a confidence score, shown as **COLD / WARM / HOT** rather than
a number. Three rules produce it, in `src/lib/confidence.ts`:

- **Time decays everything.** The original report and each vote lose half their
  weight every 90 minutes, so the map is never stale.
- **Closer voters count for more.** A confirmation is worth full weight on top of
  the pin and about a sixth of that from 2 km away — you cannot vouch for a
  sighting from another borough.
- **Patrolling counts for more.** Reports and votes from someone actually out
  there outweigh taps from the couch.

Because every contribution shares one half-life, the total score is itself a
clean exponential going forward in time. That gives an exact answer for when a
pin drops to the next heat band, which is what the countdown in the detail card
shows — no simulation, just algebra (`msUntilHeat`).

The useful side effect: spam sinks without moderators, and hot zones emerge from
the data instead of being declared.

### Patrol

Start a patrol and your route records as a web-line across the map. Distance
feeds a rank (Neighborhood Watch → City-Wide), and patrolling raises the weight
of anything you report while out.

Patrols are **foreground-only by design** — a PWA cannot hold a location watch
once backgrounded on iOS, so tracking pauses when the app leaves the screen.

### The Bugle

Pins are clustered by space and time, and each cluster gets a headline chosen
from its size, heat and dominant tag. Locations are described relative to you
("north-east") rather than geocoded, so it works in any city without a
geocoding service.

## Layout

```
src/
  lib/
    confidence.ts   heat model: decay, vote weighting, time-to-next-band
    bugle.ts        clustering + headline generation
    geo.ts          haversine, bearings, formatting
    seed.ts         generated sightings around the user
    rank.ts         patrol ranks
    storage.ts      localStorage persistence
    sound.ts        synthesised blips + photo downscaling
    haptics.ts      vibration, by whatever route the platform allows
  store/useStore.ts single zustand store
  components/       map, pins, report sheet, card, feed, patrol, pixel sprites
  hooks/useTracking.ts  geolocation watch + the decay clock

android/            native Android app and its home screen widget
ios/                native iOS project (Capacitor); build it on a Mac
widgets/ios/        Scriptable widget, bundled from src/lib
scripts/
  build-widget.mjs         bundles src/lib into the iOS widget
  widget-parity-fixture.mjs generates the Android parity fixture
```

The map is a plain Leaflet instance driven by effects rather than a React
wrapper. Pins are pixel badges built as inline SVG from the same grids the Kotlin
draws, so both platforms render the same artwork rather than two attempts at it.

Tiles are CARTO's dark basemap over OpenStreetMap data, pushed to navy in CSS.

## Tests

```bash
npm test                        # model, store and persistence
cd android && gradle :app:testDebugUnitTest            # Kotlin parity, storage, widget, screens
```

The suite runs in `America/New_York` on purpose. Seeding is keyed to the UTC day
so the app and the widgets agree, while streaks count local days — under UTC a
mistake in either is invisible.

## Not yet

Multi-user and realtime pins — the natural next step, and the one thing that
would turn seeded sightings into other people's. Reputation feeding back into
vote weight, and territory, are still open too.

## Notes

Sightings are of a fictional character. There is nothing here that tracks a real
person, and all artwork and naming is original.
