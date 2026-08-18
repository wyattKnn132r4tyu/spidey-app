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

The web app is what iPhone installs, and it carries the same arcade interface,
the same model and the same features as the native Android build in
[`android/`](android/) — sightings with photos, heat filters, patrols, the Bugle,
sound and spidey-sense.

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
  store/useStore.ts single zustand store
  components/       map, heat layer, report sheet, detail, feed, patrol
  hooks/useTracking.ts  geolocation watch + the decay clock

widgets/
  ios/              Scriptable widget, bundled from src/lib
  android/          Kotlin widget, held to src/lib by a parity test
scripts/
  build-widget.mjs         bundles src/lib into the iOS widget
  widget-parity-fixture.mjs generates the Android parity fixture
```

The map is a plain Leaflet instance driven by effects rather than a React
wrapper, and the heat layer is a small custom canvas `L.Layer` — about fifty
lines, which was cheaper than another dependency.

Tiles are CARTO's dark basemap over OpenStreetMap data.

## Tests

```bash
npm test                        # model, store and persistence
cd widgets/android && gradle :app:testDebugUnitTest   # Kotlin parity + widget render
```

The suite runs in `America/New_York` on purpose. Seeding is keyed to the UTC day
so the app and the widgets agree, while streaks count local days — under UTC a
mistake in either is invisible.

## Not in v0

Multi-user and realtime pins (the natural next step: Supabase, whose realtime
subscriptions are what make other people's pins appear live), push
notifications, reputation feeding back into vote weight, photos, and territory.

## Notes

Sightings are of a fictional character. There is nothing here that tracks a real
person, and all artwork and naming is original.
