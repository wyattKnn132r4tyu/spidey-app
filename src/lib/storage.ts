import type { Patrol, Sighting, UserProfile } from '../types';

const KEY = 'spidey-tracker:v1';

export interface PersistedState {
  sightings: Sighting[];
  patrols: Patrol[];
  profile: UserProfile;
  seededFor?: { lat: number; lng: number };
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
    if (!parsed.sightings || !parsed.profile) return null;
    return parsed;
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
