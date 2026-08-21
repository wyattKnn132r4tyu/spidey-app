import { create } from 'zustand';
import type { Heat, LatLng, Patrol, Sighting, SightingTag, UserProfile } from '../types';
import { dayKey, distanceM, localDayKey } from '../lib/geo';
import { heatOf, isLive } from '../lib/confidence';
import { seedSightings } from '../lib/seed';
import { tingle } from '../lib/haptics';
import { blip, setMuted } from '../lib/sound';
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

/**
 * How long the spidey-sense banner stays up. It announces a moment — something
 * hot came close — and a moment that never ends is just furniture.
 */
export const SENSE_BANNER_MS = 30_000;

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
  /**
   * The UTC day the seeded pins were generated for — carried forward from the
   * load, not restamped on every write. Writing today's date on an unrelated
   * save marks the day seeded when it was not, and the next launch keeps
   * yesterday's decayed pins forever.
   */
  seededDay: string;
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
  dismissSense: () => void;
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
    seededDay: state.seededDay,
  });

/** GeolocationPositionError.PERMISSION_DENIED, without needing the global. */
const PERMISSION_DENIED = 1;

interface Fix {
  at: LatLng | null;
  /** True only for an actual refusal — not a timeout, not an unavailable fix. */
  denied: boolean;
}

function getCurrentPosition(): Promise<Fix> {
  if (!('geolocation' in navigator)) return Promise.resolve({ at: null, denied: false });
  return new Promise((resolve) => {
    navigator.geolocation.getCurrentPosition(
      (pos) => resolve({ at: { lat: pos.coords.latitude, lng: pos.coords.longitude }, denied: false }),
      // A timeout is not a refusal. Telling someone their location is off when
      // it is merely slow sends them to Settings to fix nothing.
      (error) => resolve({ at: null, denied: error.code === PERMISSION_DENIED }),
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
  seededDay: dayKey(Date.now()),
  mapCentre: null,
  clock: Date.now(),

  init: async () => {
    // The activity's effect can fire twice under StrictMode, and a second pass
    // would overwrite whatever the first one settled on.
    if (get().ready) return;

    const stored = load();
    const fix = await getCurrentPosition();
    const home = fix.at ?? stored?.seededFor ?? DEFAULT_HOME;
    const today = dayKey(Date.now());

    // Re-seed on a first run, on a new day (the generator is day-keyed, and
    // yesterday's pins have decayed to nothing), or when the user has moved far
    // enough that the old city's pins are nowhere near them.
    const movedCities = stored?.seededFor ? distanceM(stored.seededFor, home) > 20_000 : true;
    const newDay = stored?.seededDay !== today;
    const staleSeed = !stored || movedCities || newDay;

    set({
      ready: true,
      home,
      position: fix.at,
      locationDenied: fix.denied,
      profile: stored?.profile ?? makeProfile(),
      patrols: stored?.patrols ?? [],
      sightings: staleSeed
        ? [...seedSightings(home), ...(stored?.sightings ?? []).filter((s) => !s.id.startsWith('seed-'))]
        : stored.sightings,
      // Only a run that actually seeded may claim today.
      seededDay: staleSeed ? today : stored.seededDay!,
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
    // Acting on the alert clears it, the same as any other notification.
    set({ selectedId, lastSense: null });
    if (selectedId) blip('tap');
  },
  setReporting: (reporting) => set({ reporting }),
  toggleSound: () => {
    const soundOn = !get().soundOn;
    set({ soundOn });
    // Gate first, then blip: turning it on is confirmed by the sound itself,
    // turning it off is swallowed by the gate we just closed.
    setMuted(!soundOn);
    blip('tap');
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
   * The alert is visible as well as felt: iOS Safari has no Vibration API, so on
   * the installed web app this is a banner and a blip. The native build reaches
   * the Taptic Engine through Capacitor and buzzes as well.
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
    tingle();

    // The banner announces a moment. Left up, it stops meaning anything and
    // starts covering the map.
    setTimeout(() => {
      if (get().lastSense === near.sighting.id) set({ lastSense: null });
    }, SENSE_BANNER_MS);
  },

  dismissSense: () => set({ lastSense: null }),
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
      lastSense: null,
      // Filters hiding bands of a city that no longer exists.
      hiddenHeats: [],
      seededDay: dayKey(Date.now()),
      // The streak counted patrols that no longer exist.
      profile: { ...profile, streakDays: 0, lastPatrolDay: undefined },
    });
    persist(get());
  },
}));
