# Installing on your phone

Two things to install: the **app** (a PWA, works on both phones) and the
**widget** (different route on each platform, because no web technology can put
a live widget on a home screen).

---

## 1. Put the app online

The app needs an HTTPS address before a phone can install it. A GitHub Actions
workflow is already set up to publish it.

**One setting has to be flipped by hand — I cannot do it from here:**

1. Go to the repo → **Settings** → **Pages**
2. Under **Build and deployment → Source**, choose **GitHub Actions**
3. Go to **Actions** → **Deploy to GitHub Pages** → **Run workflow**

The app then lives at:

```
https://wyattknn132r4tyu.github.io/spidey-app/
```

If you host it somewhere else, update `APP_URL` in `widgets/ios/shell.js` and
`SpideyWidgetProvider.kt`, and set `BASE_PATH=/` when building for a domain root.

---

## 2. Install the app

### iPhone

1. Open the URL **in Safari** (Chrome on iOS cannot install PWAs)
2. Tap **Share** → **Add to Home Screen** → **Add**

It launches fullscreen with no browser chrome, and works offline after the first
open. Allow location when asked, or the map centres on midtown.

### Android

1. Open the URL in Chrome
2. Tap the **⋮ menu** → **Install app** (or accept the install prompt)

Android installs it as a proper app with its own entry in the app drawer.

---

## 3. Install the widget

### iPhone — via Scriptable

iOS widgets need native code, but **Scriptable** runs JavaScript widgets without
Xcode or a developer account.

1. Install **Scriptable** from the App Store (free)
2. Open `widgets/ios/SpideyWidget.js` from this repo and copy the whole file
3. In Scriptable: **+** → paste → name it **Spidey Tracker** → **Done**
4. Run it once inside Scriptable and **allow location** when prompted — the
   widget cannot ask for permission itself
5. Long-press your home screen → **+** → **Scriptable** → pick a size → **Add**
6. Long-press the new widget → **Edit Widget** → **Script: Spidey Tracker**

Sizes: **small** shows the hot count and the nearest sighting; **medium** adds a
list of active zones; **lock screen** (`accessoryRectangular`/`accessoryInline`)
shows a one-line summary. Tapping opens the app.

iOS decides when widgets refresh — roughly every 15–30 minutes in practice, not
on demand.

**If you edit the widget**, edit `widgets/ios/shell.js` and run
`npm run build:widget`, not the generated `SpideyWidget.js`.

### Android — via the APK

A debug-signed APK is built from `widgets/android`.

**To install the one already built:**

1. Get `app-debug.apk` (attached in chat, or from the **Android widget** workflow
   run → **Artifacts**)
2. Copy it to your phone and open it
3. Android will warn about installing from an unknown source — allow it for your
   file manager or browser, then install

**To build it yourself:**

```bash
cd widgets/android
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:assembleDebug     # or: gradle :app:assembleDebug
```

Then:

1. Open **Spidey Widget** from the app drawer and tap **Grant location** — the
   widget cannot request permission itself
2. Long-press the home screen → **Widgets** → **Spidey Widget** → drag it out

Android refreshes it every 30 minutes (the platform minimum). **Refresh widget
now** in the app forces an update. Tapping the widget opens the web app.

---

## What the widgets show

Hot and warm counts, and the sighting most worth chasing — its distance,
direction and how long until it cools to the next band.

Both widgets reproduce the app's own model rather than reading its data, because
neither can see the browser's storage:

- The **iOS widget** is built by `npm run build:widget`, which bundles the real
  modules from `src/lib` — one source of truth, no hand-copy.
- The **Android widget** is a Kotlin port, since RemoteViews cannot run
  JavaScript. `npm run build:parity` regenerates a fixture from the TypeScript
  and `SpideyCoreTest` asserts the port reproduces it exactly — positions,
  timestamps, vote counts and confidence to 1e-12.

The seed is keyed to the day and your location, so app and widget agree on what
is hot at any given moment.

**They will not show pins you drop in the app.** Those live in the browser's
localStorage, which neither widget can read. That needs the backend described in
the README.

---

## Troubleshooting

**Widget says "location unavailable"** — grant location (Scriptable on iOS, the
Spidey Widget app on Android). iOS also needs Scriptable set to "While Using" or
"Always" in Settings → Privacy → Location Services.

**Map is blank but pins show** — tile requests are failing. The pins and the
model work offline; the basemap does not.

**iPhone install has no icon** — you opened it in Chrome. iOS only installs PWAs
from Safari.

**Android install blocked** — the APK is debug-signed, not from the Play Store.
Allow your file manager to install unknown apps in Settings → Apps → Special
access.
