import type { Patrol } from '../types';

export interface Rank {
  title: string;
  /** Metres of patrolling needed to reach it. */
  at: number;
}

export const RANKS: Rank[] = [
  { title: 'Neighborhood Watch', at: 0 },
  { title: 'Friendly Neighborhood', at: 5_000 },
  { title: 'Web-Head', at: 20_000 },
  { title: 'Night Shift', at: 50_000 },
  { title: 'City-Wide', at: 100_000 },
];

export function totalDistance(patrols: Patrol[]): number {
  return patrols.reduce((sum, patrol) => sum + patrol.distanceM, 0);
}

export function rankFor(metres: number): { current: Rank; next: Rank | null; progress: number } {
  let index = 0;
  for (let i = 0; i < RANKS.length; i++) if (metres >= RANKS[i].at) index = i;

  const current = RANKS[index];
  const next = RANKS[index + 1] ?? null;
  const progress = next ? (metres - current.at) / (next.at - current.at) : 1;

  return { current, next, progress: Math.max(0, Math.min(1, progress)) };
}

export function formatDuration(ms: number): string {
  const minutes = Math.round(ms / 60_000);
  if (minutes < 60) return `${minutes}m`;
  return `${Math.floor(minutes / 60)}h ${minutes % 60}m`;
}
