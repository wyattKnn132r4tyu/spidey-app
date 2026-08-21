import { TAG_BY_ID, type Patrol, type Sighting, type SightingTag, type UserProfile } from '../types';

const KEY = 'spidey-tracker:v1';

/** What a report becomes when its tag no longer exists. */
const FALLBACK_TAG: SightingTag = 'swinging';

/**
 * Tags have changed since the first build shipped, and someone who installed it
 * then still has pins filed under names that are gone. An unknown tag is not
 * worth discarding a pin over — and it is certainly not worth discarding the
 * whole state file over — so it falls back to a tag that exists.
 */
function migrate(sightings: Sighting[]): Sighting[] {
  return sightings.map((sighting) =>
    TAG_BY_ID[sighting.tag] ? sighting : { ...sighting, tag: FALLBACK_TAG },
  );
}

export interface PersistedState {
  sightings: Sighting[];
  patrols: Patrol[];
  profile: UserProfile;
  seededFor?: { lat: number; lng: number };
  /** UTC day the seed was generated for. The generator is day-keyed, so a new
   *  day needs a new set — otherwise the map slowly decays to empty and looks
   *  broken, and the home screen widget (which reseeds daily) disagrees. */
  seededDay?: string;
}

const HANDLE_PARTS = [
  ['friendly', 'nightly', 'uptown', 'downtown', 'crosstown', 'rooftop'],
  ['watcher', 'walker', 'lurker', 'regular', 'commuter', 'local'],
];

export function makeProfile(): UserProfile {
  const pick = (list: string[]) => list[Math.floor(Math.random() * list.length)];
  return {
    id: `local-${Math.random().toString(36).slice(2, 10)}`,
    handle: `${pick(HANDLE_PARTS[0])}_${pick(HANDLE_PARTS[1])}`,
    reputation: 0.5,
    streakDays: 0,
  };
}

export function load(): PersistedState | null {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as PersistedState;
    if (!Array.isArray(parsed.sightings) || !parsed.profile) return null;
    return { ...parsed, sightings: migrate(parsed.sightings) };
  } catch {
    // Corrupt or unavailable storage should never stop the app from opening.
    return null;
  }
}

export function save(state: PersistedState): void {
  try {
    localStorage.setItem(KEY, JSON.stringify(state));
  } catch {
    // Quota or private-mode failures are not worth interrupting the user over.
  }
}

export function clear(): void {
  try {
    localStorage.removeItem(KEY);
  } catch {
    /* nothing to do */
  }
}
