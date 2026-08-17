import type { LatLng, Sighting, SightingTag, Vote } from '../types';
import { offset } from './geo';

/**
 * Seeded sightings so the map is alive on first open.
 *
 * A sighting map with no users is a blank screen, and a blank screen tells you
 * nothing about whether the confidence model feels good. These are generated
 * around wherever the user actually is, in clusters, with vote counts and ages
 * that put at least one zone in each heat band.
 */

const HANDLES = [
  'foresthills_frank', 'f_train_ghost', 'bodega_cat_92', 'nightshiftnurse',
  'chelsea_walkup', 'delivery_dave', 'astoria_ana', 'rooftop_gardener',
  'lateshift_leo', 'bridge_and_tunnel', 'midtown_myra', 'flatiron_finch',
  'yellowcab_yuri', 'harlem_hana', 'brooklyn_bram', 'notjjjameson',
];

const NOTES = [
  'came off the roof of the parking garage and just kept going',
  'heard the thwip before I saw anything',
  'two blocks up, moving north, fast',
  'whole street stopped and looked up',
  'webbing on the streetlight is still there if you want to check',
  'gone before I got my phone out, obviously',
  'nobody round here knows his name. we just know he turns up',
  'landed on the fire escape, waved, left',
  'kid outside the bodega called it before the rest of us looked up',
  '',
];

/** Weighted pick list: the everyday reports outnumber the strange ones. */
const TAGS: SightingTag[] = [
  'swinging', 'swinging', 'red-blur', 'rooftop', 'stopped-something', 'acting-strange',
];

/** Small deterministic PRNG so a reload does not reshuffle the city. */
function makeRandom(seed: number) {
  let state = seed >>> 0;
  return () => {
    state = (state * 1664525 + 1013904223) >>> 0;
    return state / 0x1_0000_0000;
  };
}

const MINUTE = 60_000;

interface ClusterSpec {
  /** Minutes ago the cluster peaked. */
  ageMin: number;
  /** How many sightings in it. */
  count: number;
  /** Confirmations per sighting, roughly. */
  confirms: number;
  radiusM: number;
  /** Metres from the user. */
  distanceM: number;
}

/** Tuned to land one cluster in each heat band. */
const CLUSTERS: ClusterSpec[] = [
  { ageMin: 8, count: 5, confirms: 9, radiusM: 220, distanceM: 700 },
  { ageMin: 35, count: 3, confirms: 5, radiusM: 260, distanceM: 1500 },
  { ageMin: 95, count: 4, confirms: 4, radiusM: 300, distanceM: 2400 },
  { ageMin: 180, count: 2, confirms: 3, radiusM: 180, distanceM: 3100 },
  { ageMin: 260, count: 3, confirms: 2, radiusM: 340, distanceM: 1900 },
];

export function seedSightings(home: LatLng, now = Date.now()): Sighting[] {
  // Seed by day so the city is stable within a session but different tomorrow.
  const random = makeRandom(Math.floor(now / 86_400_000) * 7919 + 13);
  const sightings: Sighting[] = [];

  CLUSTERS.forEach((cluster, clusterIndex) => {
    const centre = offset(home, cluster.distanceM * (0.7 + random() * 0.6), random() * 360);

    for (let i = 0; i < cluster.count; i++) {
      const at = offset(centre, random() * cluster.radiusM, random() * 360);
      // Never in the future: a not-yet-elapsed timestamp has its decay clamped,
      // which would break the exact exponential the cooldown countdown relies on.
      const ageMinutes = Math.max(1, cluster.ageMin + random() * 20 - 10);
      const createdAt = now - ageMinutes * MINUTE;
      const confirmCount = Math.max(0, Math.round(cluster.confirms * (0.5 + random())));
      const denyCount = random() < 0.35 ? Math.round(random() * 2) : 0;

      const makeVote = (): Vote => ({
        userId: `seed-${Math.floor(random() * 9999)}`,
        distanceM: random() * 900,
        onPatrol: random() < 0.25,
        // Votes trickle in after the report rather than landing all at once.
        createdAt: Math.min(now, createdAt + random() * (now - createdAt)),
      });

      const handle = HANDLES[Math.floor(random() * HANDLES.length)];

      sightings.push({
        id: `seed-${clusterIndex}-${i}`,
        lat: at.lat,
        lng: at.lng,
        createdAt,
        tag: TAGS[Math.floor(random() * TAGS.length)],
        note: NOTES[Math.floor(random() * NOTES.length)] || undefined,
        reporterId: `seed-user-${handle}`,
        reporterHandle: handle,
        reportedOnPatrol: random() < 0.3,
        confirms: Array.from({ length: confirmCount }, makeVote),
        denies: Array.from({ length: denyCount }, makeVote),
      });
    }
  });

  return sightings.sort((a, b) => b.createdAt - a.createdAt);
}
