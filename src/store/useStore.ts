import { create } from 'zustand';
import type { Heat, LatLng, Patrol, Sighting, SightingTag, UserProfile } from '../types';
import { dayKey, distanceM, localDayKey } from '../lib/geo';
import { heatOf, isLive } from '../lib/confidence';
import { seedSightings } from '../lib/seed';
import { blip } from '../lib/sound';
import { load, makeProfile, save, clear } from '../lib/storage';

/** Falls back to midtown Manhattan when the user declines location. */
export const DEFAULT_HOME: LatLng = { lat: 40.7484, lng: -73.9857 };

/** Ignore GPS jitter below this when accumulating a patrol route. */
const MIN_STEP_M = 8;

/**
 * Drop fixes vaguer than this while patrolling. A phone that briefly falls back
 * to cell-tower positioning reports jumps of hundreds of metres without the user
 * moving, which would otherwise be banked as distance covered.
 */
const MAX_ACCURACY_M = 50;

/** Close enough that spidey-sense is worth firing. Matches the Android build. */
export const SENSE_RADIUS_M = 400;

/**
 * Photos live in localStorage as downscaled data URLs, so the number kept is
 * capped — a browser gives roughly 5 MB and a dead app is worse than a lost
 * snapshot.
 */
const MAX_PHOTOS = 20;

export type Tab = 'map' | 'bugle' | 'patrol';

interface State {
  ready: boolean;
  home: LatLng;
  position: LatLng | null;
  locationDenied: boolean;
  sightings: Sighting[];
  patrols: Patrol[];
  activePatrol: Patrol | null;
  profile: UserProfile;
  tab: Tab;
  selectedId: string | null;
  reporting: boolean;
  /** Heat bands switched off with the edge tabs. */
  hiddenHeats: Heat[];
  soundOn: boolean;
  senseOn: boolean;
  /** Set when the map should jump back to the user; cleared once consumed. */
  recenterAt: LatLng | null;
  /** A photo taken but not yet attached to a report, as a data URL. */
  pendingPhoto: string | null;
  /** Id of the sighting spidey-sense last fired on. */
  lastSense: string | null;
  /** Sightings already announced, so one pin does not buzz on every fix. */
  sensed: string[];
  /** Where the map is looking. Used to place a pin when there is no GPS fix. */
  mapCentre: LatLng | null;
  /** Bumped on a timer so decaying values re-render. */
  clock: number;

  init: () => Promise<void>;
  setPosition: (at: LatLng, accuracyM?: number) => void;
  setMapCentre: (at: LatLng) => void;
  setTab: (tab: Tab) => void;
  select: (id: string | null) => void;
  setReporting: (open: boolean) => void;
  toggleSound: () => void;
  toggleSense: () => void;
  toggleHeatFilter: (heat: Heat) => void;
  requestRecenter: () => void;
  recenterHandled: () => void;
  setPendingPhoto: (dataUrl: string | null) => void;
  runSense: (at: LatLng) => void;
  tick: () => void;

  report: (tag: SightingTag, note: string) => void;
  vote: (id: string, kind: 'confirm' | 'deny') => void;
  startPatrol: () => void;
  stopPatrol: () => void;
  reset: () => void;
}

const persist = (state: State) =>
  save({
    sightings: state.sightings,
    patrols: state.patrols,
    profile: state.profile,
    seededFor: state.home,
    seededDay: dayKey(Date.now()),
  });

function getCurrentPosition(): Promise<LatLng | null> {
  if (!('geolocation' in navigator)) return Promise.resolve(null);
  return new Promise((resolve) => {
    navigator.geolocation.getCurrentPosition(
      (pos) => resolve({ lat: pos.coords.latitude, lng: pos.coords.longitude }),
      () => resolve(null),
      { enableHighAccuracy: false, timeout: 8000, maximumAge: 60_000 },
    );
  });
}

export const useStore = create<State>((set, get) => ({
  ready: false,
  home: DEFAULT_HOME,
  position: null,
  locationDenied: false,
  sightings: [],
  patrols: [],
  activePatrol: null,
  profile: makeProfile(),
  tab: 'map',
  selectedId: null,
  reporting: false,
  hiddenHeats: [],
  soundOn: true,
  senseOn: true,
  recenterAt: null,
  pendingPhoto: null,
  lastSense: null,
  sensed: [],
  mapCentre: null,
  clock: Date.now(),

  init: async () => {
    const stored = load();
    const fix = await getCurrentPosition();
    const home = fix ?? stored?.seededFor ?? DEFAULT_HOME;

    // Re-seed on a first run, on a new day (the generator is day-keyed, and
    // yesterday's pins have decayed to nothing), or when the user has moved far
    // enough that the old city's pins are nowhere near them.
    const movedCities = stored?.seededFor ? distanceM(stored.seededFor, home) > 20_000 : true;
    const newDay = stored?.seededDay !== dayKey(Date.now());
    const staleSeed = !stored || movedCities || newDay;

    set({
      ready: true,
      home,
      position: fix,
      locationDenied: fix === null,
      profile: stored?.profile ?? makeProfile(),
      patrols: stored?.patrols ?? [],
      sightings: staleSeed
        ? [...seedSightings(home), ...(stored?.sightings ?? []).filter((s) => !s.id.startsWith('seed-'))]
        : stored.sightings,
    });
    persist(get());
  },

  setPosition: (at, accuracyM) => {
    const { activePatrol } = get();
    set({ position: at, locationDenied: false });

    if (!activePatrol) return;
    if (accuracyM !== undefined && accuracyM > MAX_ACCURACY_M) return;

    const last = activePatrol.route[activePatrol.route.length - 1];
    const step = last ? distanceM(last, at) : 0;
    if (last && step < MIN_STEP_M) return;

    set({
      activePatrol: {
        ...activePatrol,
        route: [...activePatrol.route, at],
        // Accumulated, not recomputed: re-measuring the whole route on every fix
        // is quadratic, and a long patrol is thousands of fixes.
        distanceM: activePatrol.distanceM + step,
      },
    });
  },

  setMapCentre: (mapCentre) => set({ mapCentre }),

  setTab: (tab) => set({ tab }),
  select: (selectedId) => {
    set({ selectedId });
    if (selectedId) blip('tap');
  },
  setReporting: (reporting) => set({ reporting }),
  toggleSound: () => {
    const soundOn = !get().soundOn;
    set({ soundOn });
    if (soundOn) blip('tap');
  },

  toggleSense: () => {
    set({ senseOn: !get().senseOn });
    blip('tap');
  },

  toggleHeatFilter: (heat) => {
    const hidden = get().hiddenHeats;
    set({
      hiddenHeats: hidden.includes(heat) ? hidden.filter((h) => h !== heat) : [...hidden, heat],
      selectedId: null,
    });
    blip('tap');
  },

  requestRecenter: () => {
    const at = get().position;
    if (!at) return;
    set({ recenterAt: at });
    blip('tap');
  },

  recenterHandled: () => set({ recenterAt: null }),

  setPendingPhoto: (pendingPhoto) => {
    set({ pendingPhoto });
    if (pendingPhoto) blip('confirm');
  },

  /**
   * Spidey-sense: buzz once per hot sighting that comes close.
   *
   * iOS Safari has no Vibration API, so on iPhone this is sound and the on-screen
   * banner only — which is why the alert is visible rather than purely haptic.
   */
  runSense: (at) => {
    const { senseOn, sightings, clock, sensed } = get();
    if (!senseOn) return;

    const near = sightings
      .filter((s) => isLive(s, clock) && heatOf(s, clock) === 'hot' && !sensed.includes(s.id))
      .map((s) => ({ sighting: s, away: distanceM(at, s) }))
      .filter((entry) => entry.away <= SENSE_RADIUS_M)
      .sort((a, b) => a.away - b.away)[0];

    if (!near) return;

    set({ sensed: [...sensed, near.sighting.id], lastSense: near.sighting.id });
    blip('sense');
    if (typeof navigator !== 'undefined' && navigator.vibrate) navigator.vibrate([60, 90, 60]);
  },
  tick: () => set({ clock: Date.now() }),

  report: (tag, note) => {
    const { position, mapCentre, home, profile, activePatrol, pendingPhoto } = get();
    // Without a fix, the pin goes where the user is looking — which is what the
    // report sheet tells them will happen.
    const at = position ?? mapCentre ?? home;
    const sighting: Sighting = {
      id: `local-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 6)}`,
      lat: at.lat,
      lng: at.lng,
      createdAt: Date.now(),
      tag,
      note: note.trim() || undefined,
      reporterId: profile.id,
      reporterHandle: profile.handle,
      reportedOnPatrol: Boolean(activePatrol),
      confirms: [],
      denies: [],
      photo: pendingPhoto ?? undefined,
    };

    // Oldest photos are dropped first once the cap is reached; the pins stay.
    let kept = 0;
    const sightings = [sighting, ...get().sightings].map((s) => {
      if (!s.photo) return s;
      kept += 1;
      return kept <= MAX_PHOTOS ? s : { ...s, photo: undefined };
    });

    blip('drop');

    set({
      sightings,
      pendingPhoto: null,
      reporting: false,
      selectedId: sighting.id,
      activePatrol: activePatrol
        ? { ...activePatrol, sightingIds: [...activePatrol.sightingIds, sighting.id] }
        : null,
    });
    persist(get());
  },

  vote: (id, kind) => {
    const { position, home, profile, activePatrol, sightings } = get();
    const from = position ?? home;

    set({
      sightings: sightings.map((sighting) => {
        if (sighting.id !== id) return sighting;
        // One vote per user per sighting, and a change of mind replaces the old one.
        const confirms = sighting.confirms.filter((v) => v.userId !== profile.id);
        const denies = sighting.denies.filter((v) => v.userId !== profile.id);
        const vote = {
          userId: profile.id,
          distanceM: distanceM(from, sighting),
          onPatrol: Boolean(activePatrol),
          createdAt: Date.now(),
        };
        return kind === 'confirm'
          ? { ...sighting, confirms: [...confirms, vote], denies }
          : { ...sighting, confirms, denies: [...denies, vote] };
      }),
    });
    blip(kind === 'confirm' ? 'confirm' : 'deny');
    persist(get());
  },

  startPatrol: () => {
    const { position } = get();
    set({
      activePatrol: {
        id: `patrol-${Date.now().toString(36)}`,
        startedAt: Date.now(),
        route: position ? [position] : [],
        distanceM: 0,
        sightingIds: [],
      },
      tab: 'map',
    });
  },

  stopPatrol: () => {
    const { activePatrol, patrols, profile } = get();
    if (!activePatrol) return;

    const finished: Patrol = { ...activePatrol, endedAt: Date.now() };
    // Local days, not UTC: a streak is about the user's evenings, not Greenwich's.
    const today = localDayKey(finished.endedAt!);
    const yesterday = localDayKey(finished.endedAt! - 86_400_000);

    // A streak survives one calendar day of silence, not two.
    const streakDays =
      profile.lastPatrolDay === today
        ? profile.streakDays
        : profile.lastPatrolDay === yesterday
          ? profile.streakDays + 1
          : 1;

    set({
      activePatrol: null,
      patrols: [finished, ...patrols],
      profile: { ...profile, streakDays, lastPatrolDay: today },
      tab: 'patrol',
    });
    persist(get());
  },

  reset: () => {
    clear();
    const { home, profile } = get();
    set({
      sightings: seedSightings(home),
      patrols: [],
      activePatrol: null,
      selectedId: null,
      sensed: [],
      // The streak counted patrols that no longer exist.
      profile: { ...profile, streakDays: 0, lastPatrolDay: undefined },
    });
    persist(get());
  },
}));
