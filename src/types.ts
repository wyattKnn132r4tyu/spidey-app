export type SightingTag =
  | 'swinging'
  | 'stopped-something'
  | 'red-blur'
  | 'rooftop'
  | 'webbing'
  | 'acting-strange'
  | 'heavy';

export interface TagMeta {
  id: SightingTag;
  label: string;
  icon: string;
  /** How much this kind of report contributes on its own, before confirmations. */
  baseWeight: number;
}

/**
 * What people actually report in a city working street-level crime across all
 * five boroughs — and, since nobody here can name him, what they can describe:
 * a shape, a sound, what was left behind.
 */
export const TAGS: TagMeta[] = [
  { id: 'swinging', label: 'Swinging through', icon: '🕸️', baseWeight: 1 },
  { id: 'stopped-something', label: 'Stopped a mugging', icon: '🚨', baseWeight: 1.4 },
  { id: 'red-blur', label: 'Just a red blur', icon: '💨', baseWeight: 0.6 },
  { id: 'rooftop', label: 'Rooftop landing', icon: '🏙️', baseWeight: 1.1 },
  { id: 'webbing', label: 'Fresh webbing', icon: '🧵', baseWeight: 0.9 },
  { id: 'acting-strange', label: 'People acting strange', icon: '🌀', baseWeight: 1.2 },
  { id: 'heavy', label: 'Something big fighting back', icon: '⚡', baseWeight: 1.3 },
];

export const TAG_BY_ID: Record<SightingTag, TagMeta> = Object.fromEntries(
  TAGS.map((t) => [t.id, t]),
) as Record<SightingTag, TagMeta>;

export interface Vote {
  /** Who cast it. In v0 that is either the local user or a seeded pseudo-user. */
  userId: string;
  /** Metres between the voter and the sighting when they voted. */
  distanceM: number;
  /** Voters who were mid-patrol carry more weight. */
  onPatrol: boolean;
  createdAt: number;
}

export interface Sighting {
  id: string;
  lat: number;
  lng: number;
  createdAt: number;
  tag: SightingTag;
  note?: string;
  reporterId: string;
  reporterHandle: string;
  /** True when the reporter was on patrol at the time. */
  reportedOnPatrol: boolean;
  confirms: Vote[];
  denies: Vote[];
}

export type Heat = 'cold' | 'warm' | 'hot';

export interface Patrol {
  id: string;
  startedAt: number;
  endedAt?: number;
  route: LatLng[];
  distanceM: number;
  sightingIds: string[];
}

export interface LatLng {
  lat: number;
  lng: number;
}

export interface UserProfile {
  id: string;
  handle: string;
  /** 0..1, drives how much this user's votes and reports count. */
  reputation: number;
  streakDays: number;
  lastPatrolDay?: string;
}

export interface BugleStory {
  id: string;
  headline: string;
  standfirst: string;
  at: number;
  sightingIds: string[];
  /** Drives the accent colour of the card. */
  tone: 'alarm' | 'wry' | 'neutral';
}
