# Spidey Tracker — Android app

The whole thing, native. Map, feed, patrol and the home screen widget all run
in-process: no web view, no browser, no server. Grep the source for `http` and
the only hit is the XML namespace.

```bash
export ANDROID_HOME=/path/to/sdk
echo "sdk.dir=$ANDROID_HOME" > local.properties
gradle :app:assembleDebug          # or ./gradlew
gradle :app:testDebugUnitTest
```

The APK installs by sideloading — it is debug-signed, not from the Play Store.

## What is where

```
SpideyCore.kt          the model: geography, seeding, confidence maths
Bugle.kt               clustering and headline generation
SpideyRepository.kt    state, JSON persistence, votes, patrols, streaks
SpideyViewModel.kt     UI state, location updates, patrol accumulation
MainActivity.kt        Compose host and screen switching
MapScreen.kt           osmdroid map, bezel chrome, detail card
SightingOverlay.kt     pixel badge pins drawn straight onto the map canvas
ReportSheet.kt         the report form
BugleScreen.kt         the feed
PatrolScreen.kt        rank, streak, history
SpideyWidgetProvider.kt  home screen widget
ArcadeUi.kt            bezels, amber buttons, pixel spider, web compass
Theme.kt               palette and the pixel type scale
```

## The look

Modelled on the in-film Spidey Tracker as it appears at spideytracker.com: a
blue plastic bezel around a navy map, ruler ticks down the edges, chunky pixel
badges for pins, amber for anything meant to be pressed.

Everything is drawn in code rather than lifted: the spiders, the watcher sprite
in the corner and the web compass are pixel grids defined in `ArcadeUi.kt` and
`SightingOverlay.kt`. No Marvel, Sony, Samsung or Google marks ship in the APK.

The typeface is **Press Start 2P** (SIL Open Font License), bundled at
`res/font/press_start_2p.ttf` with its licence in `assets/PressStart2P-OFL.txt`.

## The map

osmdroid renders OpenStreetMap tiles in-process — no API key, and nothing
embeds a browser. A colour matrix pushes the standard basemap into deep navy,
so the map matches without depending on a second tile provider. Navy sits behind
the tiles too, so a slow or failed load still reads as the app.

Pins are one custom `Overlay` rather than a marker each. That
is what lets them be drawn in a known order — cold, warm, hot, then the selected
one — so in a dense cluster the hot pin sits on top and wins the tap, which is
the pin the user is reaching for.

## The widget

Reads the same JSON the app writes, so it shows the user's real city: their
pins, their votes. Before the app has ever run there is nothing to read, so it
generates the same day's seed the app would. Tapping opens the app.

## Parity with the web app

`SpideyCore` is a second implementation of `src/lib`. It reproduces the same
pseudo-random sequence — same LCG, same constants, same order of draws — so the
Android app, the iOS widget and the web app describe the same city at the same
moment.

After changing anything in `src/lib`:

```bash
npm run build:parity     # regenerate the fixture from the TypeScript
gradle :app:testDebugUnitTest
```

`SpideyCoreTest` fails loudly if the port has drifted — positions to 1e-9,
timestamps, vote counts, confidence to 1e-12. That is its whole job.

## Tests

31 of them, all JVM — no device needed.

| Suite | Covers |
| --- | --- |
| `SpideyCoreTest` | parity with the TypeScript model |
| `SpideyRepositoryTest` | persistence, reseeding, votes, patrols, streaks |
| `SpideyWidgetProviderTest` | widget renders, reads app state, opens the app |
| `ScreenshotTest` | renders each screen to `build/screenshots/*.png` |

Screens are clipped to their frame deliberately: the embedded `MapView` is a
real Android view and will otherwise paint over the bezel.

`ScreenshotTest` uses Robolectric native graphics to draw the real Compose tree
to a bitmap. There is no KVM in CI, so no emulator; this is how the UI gets
looked at, and it fails if a screen throws or renders blank.

Not verified here: the app on real hardware. The map's tile fetching, GPS and
the widget's placement on a home screen have not been exercised on a device.
