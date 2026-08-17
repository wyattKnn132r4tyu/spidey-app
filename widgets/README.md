# Widgets

Home screen widgets for both platforms. See [`../INSTALL.md`](../INSTALL.md) for
how to get them onto a phone.

## Why there are two implementations

Neither widget can read the app's data — it lives in the browser's localStorage,
which is invisible to iOS and Android alike. So each widget re-derives the same
world from the same inputs: your location and the current time. The seed is
day-keyed and deterministic, so the widget and the app agree on what is hot
without talking to each other.

|          | How the model gets there                                   |
| -------- | ---------------------------------------------------------- |
| iOS      | `npm run build:widget` bundles `src/lib` into the script    |
| Android  | Kotlin port, held to the TypeScript by a generated fixture  |

The iOS path has no duplication at all. The Android path does, because
RemoteViews cannot run JavaScript — so it is pinned by tests instead.

## Changing the model

After editing anything in `src/lib`:

```bash
npm run build:widget    # regenerate the iOS script
npm run build:parity    # regenerate the Android test fixture
cd widgets/android && gradle :app:testDebugUnitTest
```

The Android tests fail loudly if the Kotlin port has drifted — that is their
whole job. CI runs them on every push that touches `src/lib` or
`widgets/android`.

## Layout

```
ios/
  shell.js            widget UI — edit this one
  core-entry.ts       what gets bundled in from src/lib
  SpideyWidget.js     GENERATED — paste this into Scriptable
android/
  app/src/main/       provider, launcher activity, layouts
  app/src/test/       parity test + Robolectric render test
```

## What is verified

- **Parity** — the Kotlin port reproduces the TypeScript's sightings exactly:
  positions to 1e-9, timestamps, vote counts, confidence to 1e-12, and the same
  count in each heat band.
- **Rendering** — a Robolectric test inflates the real widget layout through
  `AppWidgetManager` and asserts on the text that comes out.
- **Decay** — score halves over exactly one half-life.

Not verified here: the iOS widget on a real device (no iOS in CI — it was run
against a stubbed Scriptable API instead), and the Android widget on a real home
screen.
