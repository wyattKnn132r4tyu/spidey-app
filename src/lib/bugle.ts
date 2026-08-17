import type { BugleStory, LatLng, Sighting, SightingTag } from '../types';
import { confidenceOf, heatFromConfidence } from './confidence';
import { centroid, compassFrom, distanceM } from './geo';

/**
 * Turns raw pins into the day's story.
 *
 * Sightings are grouped into clusters (close in space, close in time), and each
 * cluster gets a headline chosen from its size, heat and dominant tag. No
 * geocoding involved — locations are described relative to the reader, which
 * keeps it honest and works in any city.
 */

const CLUSTER_RADIUS_M = 600;
const CLUSTER_WINDOW_MS = 75 * 60 * 1000;

export interface Cluster {
  sightings: Sighting[];
  centre: LatLng;
  latestAt: number;
  confidence: number;
}

export function clusterSightings(sightings: Sighting[], now = Date.now()): Cluster[] {
  const remaining = [...sightings].sort((a, b) => b.createdAt - a.createdAt);
  const clusters: Cluster[] = [];

  while (remaining.length > 0) {
    const head = remaining.shift()!;
    const members = [head];

    for (let i = remaining.length - 1; i >= 0; i--) {
      const candidate = remaining[i];
      const closeInSpace = distanceM(head, candidate) <= CLUSTER_RADIUS_M;
      const closeInTime = Math.abs(head.createdAt - candidate.createdAt) <= CLUSTER_WINDOW_MS;
      if (closeInSpace && closeInTime) {
        members.push(candidate);
        remaining.splice(i, 1);
      }
    }

    clusters.push({
      sightings: members,
      centre: centroid(members),
      latestAt: Math.max(...members.map((s) => s.createdAt)),
      // A cluster is as strong as its strongest pin, not the average.
      confidence: Math.max(...members.map((s) => confidenceOf(s, now))),
    });
  }

  return clusters.sort((a, b) => b.latestAt - a.latestAt);
}

const dominantTag = (sightings: Sighting[]): SightingTag => {
  const counts = new Map<SightingTag, number>();
  for (const s of sightings) counts.set(s.tag, (counts.get(s.tag) ?? 0) + 1);
  return [...counts.entries()].sort((a, b) => b[1] - a[1])[0][0];
};

const spell = (n: number) =>
  ['zero', 'a single', 'two', 'three', 'four', 'five', 'six', 'seven', 'eight', 'nine'][n] ??
  String(n);

export function buildBugle(sightings: Sighting[], home: LatLng, now = Date.now()): BugleStory[] {
  return clusterSightings(sightings, now).map((cluster) => {
    const count = cluster.sightings.length;
    const heat = heatFromConfidence(cluster.confidence);
    const where = compassFrom(home, cluster.centre);
    const tag = dominantTag(cluster.sightings);
    const denies = cluster.sightings.reduce((n, s) => n + s.denies.length, 0);
    const confirms = cluster.sightings.reduce((n, s) => n + s.confirms.length, 0);

    let headline: string;
    let tone: BugleStory['tone'] = 'neutral';

    if (tag === 'acting-strange' && heat !== 'cold' && confirms > 0) {
      // The thing nobody can see, reported by the only people who can: witnesses.
      headline = `SOMETHING IS WRONG WITH PEOPLE ${where.toUpperCase()}`;
      tone = 'alarm';
    } else if (tag === 'heavy' && heat !== 'cold') {
      headline = `SOMETHING BIG CAME THROUGH ${where.toUpperCase()}`;
      tone = 'alarm';
    } else if (heat === 'hot' && count >= 3) {
      headline = `${spell(count).toUpperCase()} SIGHTINGS ${where.toUpperCase()} INSIDE THE HOUR — MENACE?`;
      tone = 'alarm';
    } else if (tag === 'stopped-something' && heat !== 'cold' && confirms > 0) {
      // An alarmist headline needs someone other than the reporter to back it up.
      headline = `MASKED VIGILANTE INTERFERES AGAIN, ${where.toUpperCase()}`;
      tone = 'alarm';
    } else if (heat === 'hot') {
      // Four years of this and the city still cannot put a name to him.
      headline = `WHO IS HE? SIGHTING CONFIRMED ${where.toUpperCase()}, STILL NO NAME`;
      tone = 'alarm';
    } else if (denies > confirms) {
      headline = `'SIGHTING' ${where.toUpperCase()} COLLAPSES UNDER SCRUTINY`;
      tone = 'wry';
    } else if (heat === 'cold' && count === 1) {
      headline = `READER REPORTS RED BLUR ${where.toUpperCase()}. BUGLE UNCONVINCED.`;
      tone = 'wry';
    } else if (tag === 'webbing') {
      headline = `WEBBING FOUND ${where.toUpperCase()} — WHO CLEANS THIS UP?`;
      tone = 'wry';
    } else {
      headline = `${spell(count).toUpperCase()} REPORT${count === 1 ? '' : 'S'} ${where.toUpperCase()}, STILL UNVERIFIED`;
      tone = 'neutral';
    }

    const standfirst = `${count} pin${count === 1 ? '' : 's'} · ${confirms} confirmed · ${denies} disputed`;

    return {
      id: `story-${cluster.sightings[0].id}`,
      headline,
      standfirst,
      at: cluster.latestAt,
      sightingIds: cluster.sightings.map((s) => s.id),
      tone,
    };
  });
}
