# Installing on your phone

**Android** is a native app — install the APK and everything happens inside it.
Nothing opens a browser.

**iPhone** has two routes: install the web app to the home screen (works today,
no Mac needed), or build the native iOS app from the Xcode project in `ios/`
(needs a Mac, and gives you real haptics).

---

## Android

### The app

1. Get `app-debug.apk` — attached in chat, or from the **Android app** workflow
   run → **Artifacts**
2. Copy it to your phone and open it
3. Android will warn about installing from an unknown source, because the APK is
   debug-signed rather than from the Play Store. Allow it for your file manager,
   then install.
4. Open **Spidey Tracker** and allow location when asked

That is the whole install. The map, the Bugle and patrol tracking all run in the
app; the only thing it uses the network for is map tiles.

If you installed an earlier build, uninstall it first — the package was renamed
from `com.spidey.tracker.widget` to `com.spidey.tracker`, so the new one installs
alongside the old rather than over it.

**What it does:** drop sightings with a tag, a note and a photo; confirm or
dispute what other people reported; filter the map by heat with the tabs on the
left edge; track patrols; read the Bugle. Spidey-sense buzzes when a hot sighting
comes within 400 m while the app is open, and the speaker key mutes the blips.

### The widget

Long-press the home screen → **Widgets** → **Spidey Tracker** → drag it out.

It shows hot and warm counts and the nearest sighting worth chasing, reads the
same data the app does — your pins, your votes — and opens the app when tapped.
Android refreshes it every 30 minutes, which is the platform minimum, and the
app refreshes it whenever you leave it.

### Building it yourself

```bash
cd android
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :app:assembleDebug      # or: gradle :app:assembleDebug
```

---

## iPhone

### The app

The web app needs an HTTPS address first. A workflow publishes it, but **one
setting has to be flipped by hand — I cannot do it from here:**

1. Repo → **Settings** → **Pages**
2. **Build and deployment → Source**: choose **GitHub Actions**
3. **Actions** → **Deploy to GitHub Pages** → **Run workflow**

It then lives at `https://wyattknn132r4tyu.github.io/spidey-app/`.

Open that **in Safari** (Chrome on iOS cannot install web apps), then
**Share** → **Add to Home Screen**. It launches fullscreen with no browser
chrome and works offline after the first open — the pixel font, the shell and
your pins are all cached.

**What works on iPhone:** the whole app. The map, sightings with photos (the
camera opens straight from the report sheet), confirm and dispute, heat filters,
patrols, the Bugle, sound, and spidey-sense.

**What differs from Android**, because iOS Safari allows less:

| | |
| --- | --- |
| Vibration | Not supported. Spidey-sense uses a red on-screen banner and a blip. |
| Notifications | Not used. The alert only appears while the app is open. |
| Photos | Stored in the browser, downscaled, and capped at 20 to stay inside Safari's storage. |
| Background | Patrols track while the app is on screen, same as Android. |

### The native app

There is a real Xcode project in [`ios/`](ios/). It wraps the same app in a
native shell — its own icon, its own process, no browser anywhere in the
interface — and it is the only way to get an actual `.ipa` on an iPhone.

**This needs a Mac.** Apple only allows iOS apps to be compiled and signed with
Xcode on macOS, so it cannot be built here or on your phone.

```bash
npm install
npm run build:ios        # builds the web app and syncs it into the Xcode project
cd ios/App && pod install
open App.xcworkspace
```

Then in Xcode: pick your iPhone as the destination, set **Signing & Capabilities
→ Team** to your Apple ID, and press Run.

- A **free** Apple ID works and installs to your own phone. The app stops
  launching after 7 days and you re-run it to renew.
- A **paid** developer account ($99/year) gives a year per build and lets you
  send it to other people.

**What the native build adds over the home-screen install:** real vibration.
Spidey-sense reaches the Taptic Engine through Capacitor's haptics, which Safari
cannot do. Everything else is the same app.

The `ios/` project is committed but its build output is not — `pod install`
and the web bundle are regenerated on whichever Mac builds it. A GitHub Actions
workflow compiles it on a macOS runner on every push, so you know the project
builds before you open it; it cannot produce an installable file, because
signing needs your certificate.

### The widget

iOS widgets need native code, but **Scriptable** runs JavaScript widgets without
Xcode or a developer account.

1. Install **Scriptable** from the App Store (free)
2. Copy the whole of `widgets/ios/SpideyWidget.js`
3. In Scriptable: **+** → paste → name it **Spidey Tracker** → **Done**
4. Run it once inside Scriptable and **allow location** — the widget cannot ask
   for permission itself
5. Long-press the home screen → **+** → **Scriptable** → pick a size → **Add**
6. Long-press the new widget → **Edit Widget** → **Script: Spidey Tracker**

Small shows the hot count and nearest sighting, medium adds active zones, and
the lock screen sizes show a one-line summary. iOS decides when widgets refresh
— roughly every 15–30 minutes.

If you edit it, edit `widgets/ios/shell.js` and run `npm run build:widget`, not
the generated file.

---

## Why the two platforms differ

Android can be built and installed from source with the SDK alone, so the APK is
finished and attached. iOS cannot: Apple requires compilation and signing on a
Mac with Xcode, so the iOS project ships as source you build yourself, and the
home-screen install is what works with no Mac at all.

APKs do not run on iPhone, and nothing converts one. They are different formats
for different processors, and iOS refuses anything not signed for the device.

Both describe the same city: the Android app, the iOS widget and the web app all
reproduce the same model from your location and the clock, and a parity test
keeps them honest.

---

## Troubleshooting

**Android install blocked** — the APK is debug-signed. Settings → Apps → Special
access → Install unknown apps, and allow whichever app you are opening it from.

**Map is dark grey with pins but no streets** — tile requests are failing. The
pins and the model work offline; the basemap does not.

**Widget says "last known location"** — Android has no recent fix. Open the app
once with location on; the widget uses the app's saved position as a fallback.

**Patrol distance is not moving** — patrols track while the app is on screen. A
PWA cannot hold a location watch in the background on iOS, and the Android app
deliberately does not run a background service.

**iPhone install has no icon** — you opened it in Chrome. iOS only installs web
apps from Safari.
