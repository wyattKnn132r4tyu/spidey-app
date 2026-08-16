# Spidey Tracker — Brainstorm

Status: concept only. Nothing built yet.

## Concept

A mobile-first web app with two halves that feed each other:

- **Sighting map** — a live, crowd-sourced map of Spider-Man sightings across the
  city. Anyone can drop a pin; everyone else confirms or denies it.
- **Patrol** — you track your own movement through the city. Your route draws as
  a web-line on the same map, and sightings you report mid-patrol carry extra
  weight.

The map is the hook. The patrol is the reason to open it tomorrow.

## Core loop

1. Open the app → dark stylized city map, pins from the last few hours, hottest
   clusters glowing.
2. Something happens near you → drop a pin (tag + optional note + photo).
3. Nearby users confirm or deny → the pin's confidence rises or decays.
4. Start a patrol → your route records, you cover blocks, you catch sightings
   first.
5. The Bugle feed writes the day's story back to you.

## The mechanic that matters: confidence decay

Every sighting has a confidence score that:

- **decays with time** — a 4-hour-old pin is cold no matter how many confirms it
  had
- **rises with nearby confirmations** — weighted by how close the confirmer was
  and how good their track record is
- **rises faster from patrolling users** — someone actually out there beats
  someone tapping from the couch

This single rule produces everything interesting: hot zones emerge and fade on
their own, the map is never stale, and spam sinks without moderation.

Display it as three states — **COLD / WARM / HOT** — not a number. Numbers invite
arguing; colors invite chasing.

## Features

### Must have (v0)

- Map home screen with live pins, dark comic-styled tiles
- Report sheet: tag picker (`swinging by`, `stopped something`, `just a red
  blur`, `suit spotted`), note, auto-location
- Confirm / deny on any pin, with confidence decay running live
- Heatmap toggle — density over the last N hours
- Patrol mode: start/stop, route polyline, distance, sightings logged
- Daily Bugle feed — auto-generated headlines from pin clusters
  ("THREE SIGHTINGS IN CHELSEA INSIDE AN HOUR — MENACE?")
- Seeded demo sightings so the map is alive on first open

### Should have (v1)

- Real multi-user with realtime pins appearing as others drop them
- Spidey-sense proximity: subtle tingle animation + haptic as you close on a hot
  cluster
- Patrol streaks and ranks (Friendly Neighborhood → Web-Head → ...)
- Reporter reputation feeding confirmation weight
- Push notifications: "Spotted 0.4 mi from you, 6 min ago"

### Could have

- Territory — patrolled blocks light up as yours, decaying if you stop covering
  them
- Photo attachments with blur-first reveal
- Weekly Bugle front page, shareable as an image
- Multiple cities

### Won't have (for now)

- Background location tracking (see Risks)
- Any real-person location features — sightings are of a fictional character,
  and it stays that way
- Official Marvel/Sony art, logos, or naming

## Screens

| Screen | Purpose |
|---|---|
| Map (home) | Pins, heatmap toggle, report button, patrol start |
| Report sheet | Tag, note, location confirm, optional photo |
| Sighting detail | Confirms, distance from you, decay timer, comments |
| Bugle feed | Generated headlines, newest first |
| Patrol summary | Route drawn, distance, sightings logged, rank progress |
| Profile | Reputation, streak, badges |

## Data model sketch

```
Sighting
  id, lat, lng, created_at, tag, note, photo_url?
  reporter_id, confirms[], denies[]
  confidence  (computed, not stored)

Patrol
  id, user_id, started_at, ended_at
  route (polyline), distance_m, sighting_ids[]

User
  id, handle, reputation, streak_days, rank
```

## Tech shape

- **React + TypeScript + Vite**, built as a PWA — installable, no app store
- **Leaflet** for the map. CARTO `dark_matter` tiles are free and already the
  right mood; OSM standard under a CSS filter is the fallback with zero
  dependencies on a tile provider's terms.
- **Geolocation `watchPosition`** for patrol routes
- **v0 persistence: localStorage.** No backend, no auth, fully demoable.
- **v1 persistence: Supabase.** Postgres + realtime subscriptions + auth, and
  realtime is what makes other people's pins pop onto your map live — the actual
  wow moment.
- Seeded data generator that produces plausible sighting clusters so the app is
  never an empty map

## Risks / open questions

- **Cold start.** A sighting map with no users is a blank screen. Mitigated by
  seeding, and by patrol being valuable solo.
- **iOS background location.** Safari won't track a PWA in the background —
  patrols only record while the app is foregrounded. Either accept it (patrols
  are an active activity, screen-on) or reconsider native later. Worth deciding
  before building patrol.
- **Battery** during long patrols; throttle `watchPosition` accuracy.
- **Real geography or fictional?** Real map of the user's actual city, or always
  NYC regardless of where they are?
- **Account required?** Ties into whether v0 stays local-only.

## Recommended first build

v0, local-only: map + seeded sightings + report + confirm + decay + heatmap +
Bugle feed + patrol tracking. No backend, no auth. That's a complete, demoable
app, and it proves the confidence-decay mechanic before any infrastructure
exists.
