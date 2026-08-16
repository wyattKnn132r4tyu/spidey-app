import { create } from 'zustand';
import type { LatLng, Patrol, Sighting, SightingTag, UserProfile } from '../types';
import { dayKey, distanceM, routeLength } from '../lib/geo';
import { seedSightings } from '../lib/seed';
import { load, makeProfile, save, clear } from '../lib/storage';

/** Falls back to midtown Manhattan when the user declines location. */
export const DEFAULT_HOME: LatLng = { lat: 40.7484, lng: -73.9857 };

/** Ignore GPS jitter below this when accumulating a patrol route. */
const MIN_STEP_M = 8;

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
  showHeat: boolean;
  /** Bumped on a timer so decaying values re-render. */
  clock: number;

  init: () => Promise<void>;
  setPosition: (at: LatLng) => void;
  setTab: (tab: Tab) => void;
  select: (id: string | null) => void;
  setReporting: (open: boolean) => void;
  toggleHeat: () => void;
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
  showHeat: true,
  clock: Date.now(),

  init: async () => {
    const stored = load();
    const fix = await getCurrentPosition();
    const home = fix ?? stored?.seededFor ?? DEFAULT_HOME;

    // Re-seed if this is a first run, or if the user has moved far enough that
    // the old city's pins are no longer anywhere near them.
    const staleSeed =
      !stored || (stored.seededFor ? distanceM(stored.seededFor, home) > 20_000 : true);

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

  setPosition: (at) => {
    const { activePatrol } = get();
    set({ position: at, locationDenied: false });

    if (!activePatrol) return;

    const last = activePatrol.route[activePatrol.route.length - 1];
    if (last && distanceM(last, at) < MIN_STEP_M) return;

    const route = [...activePatrol.route, at];
    set({ activePatrol: { ...activePatrol, route, distanceM: routeLength(route) } });
  },

  setTab: (tab) => set({ tab }),
  select: (selectedId) => set({ selectedId }),
  setReporting: (reporting) => set({ reporting }),
  toggleHeat: () => set({ showHeat: !get().showHeat }),
  tick: () => set({ clock: Date.now() }),

  report: (tag, note) => {
    const { position, home, profile, activePatrol } = get();
    const at = position ?? home;
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
    };

    set({
      sightings: [sighting, ...get().sightings],
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
    const today = dayKey(finished.endedAt!);
    const yesterday = dayKey(finished.endedAt! - 86_400_000);

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
    const home = get().home;
    set({ sightings: seedSightings(home), patrols: [], activePatrol: null, selectedId: null });
    persist(get());
  },
}));
