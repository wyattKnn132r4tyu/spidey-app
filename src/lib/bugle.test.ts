import { describe, expect, it } from 'vitest';
import { buildBugle, clusterSightings } from './bugle';
import { offset } from './geo';
import { seedSightings } from './seed';
import type { Sighting, SightingTag, Vote } from '../types';

const HOME = { lat: 40.7484, lng: -73.9857 };
const NOW = 1_760_000_000_000;

let counter = 0;
const vote = (over: Partial<Vote> = {}): Vote => ({
  userId: `voter-${counter++}`,
  distanceM: 50,
  onPatrol: false,
  createdAt: NOW,
  ...over,
});

const at = (
  metres: number,
  bearing: number,
  over: Partial<Sighting> & { tag?: SightingTag } = {},
): Sighting => {
  const point = offset(HOME, metres, bearing);
  return {
    id: `s-${counter++}`,
    lat: point.lat,
    lng: point.lng,
    createdAt: NOW,
    tag: 'swinging',
    reporterId: 'r',
    reporterHandle: 'r',
    reportedOnPatrol: false,
    confirms: [],
    denies: [],
    ...over,
  };
};

describe('clusterSightings', () => {
  it('returns nothing for no sightings', () => {
    expect(clusterSightings([], NOW)).toEqual([]);
  });

  it('groups sightings that are close in space and time', () => {
    const clusters = clusterSightings([at(1000, 0), at(1100, 0), at(1050, 5)], NOW);
    expect(clusters).toHaveLength(1);
    expect(clusters[0].sightings).toHaveLength(3);
  });

  it('separates sightings that are far apart', () => {
    const clusters = clusterSightings([at(1000, 0), at(4000, 180)], NOW);
    expect(clusters).toHaveLength(2);
  });

  it('separates sightings that are hours apart in the same place', () => {
    const clusters = clusterSightings(
      [at(1000, 0), at(1000, 0, { createdAt: NOW - 5 * 3_600_000 })],
      NOW,
    );
    expect(clusters).toHaveLength(2);
  });

  it('places every sighting in exactly one cluster', () => {
    const sightings = seedSightings(HOME, NOW);
    const clustered = clusterSightings(sightings, NOW).flatMap((c) => c.sightings);
    expect(clustered).toHaveLength(sightings.length);
    expect(new Set(clustered.map((s) => s.id)).size).toBe(sightings.length);
  });

  it('rates a cluster by its strongest pin, not its average', () => {
    const strong = at(1000, 0, { confirms: Array.from({ length: 12 }, () => vote()) });
    const weak = at(1050, 0);
    const [cluster] = clusterSightings([strong, weak], NOW);
    expect(cluster.confidence).toBeGreaterThan(0.6);
  });

  it('orders clusters newest first', () => {
    const clusters = clusterSightings(
      [
        at(1000, 0, { createdAt: NOW - 4 * 3_600_000 }),
        at(4000, 180, { createdAt: NOW }),
        at(8000, 90, { createdAt: NOW - 2 * 3_600_000 }),
      ],
      NOW,
    );
    const times = clusters.map((c) => c.latestAt);
    expect(times).toEqual([...times].sort((a, b) => b - a));
  });
});

describe('buildBugle', () => {
  it('writes nothing when there is nothing to report', () => {
    expect(buildBugle([], HOME, NOW)).toEqual([]);
  });

  it('gives every story a unique id', () => {
    const stories = buildBugle(seedSightings(HOME, NOW), HOME, NOW);
    expect(new Set(stories.map((s) => s.id)).size).toBe(stories.length);
  });

  it('refuses an alarmist headline for a single unconfirmed report', () => {
    const [story] = buildBugle([at(1000, 0, { tag: 'stopped-something' })], HOME, NOW);
    expect(story.headline).not.toContain('MASKED VIGILANTE');
    expect(story.tone).not.toBe('alarm');
  });

  it('runs the alarmist headline once someone else backs it up', () => {
    const backed = at(1000, 0, {
      tag: 'stopped-something',
      confirms: Array.from({ length: 3 }, () => vote()),
    });
    const [story] = buildBugle([backed], HOME, NOW);
    expect(story.headline).toContain('MASKED VIGILANTE');
    expect(story.tone).toBe('alarm');
  });

  it('is sceptical when a sighting is disputed more than confirmed', () => {
    const disputed = at(1000, 0, {
      confirms: [vote()],
      denies: [vote(), vote(), vote()],
    });
    const [story] = buildBugle([disputed], HOME, NOW);
    expect(story.tone).toBe('wry');
  });

  it('describes direction relative to the reader', () => {
    const [story] = buildBugle([at(2000, 90)], HOME, NOW);
    expect(story.headline).toContain('EAST');
  });

  it('counts pins, confirmations and disputes in the standfirst', () => {
    const stories = buildBugle(
      [at(1000, 0, { confirms: [vote(), vote()], denies: [vote()] }), at(1100, 0)],
      HOME,
      NOW,
    );
    expect(stories[0].standfirst).toBe('2 pins · 2 confirmed · 1 disputed');
  });

  it('agrees with itself on singular and plural', () => {
    // Warm, so it lands on the generic headline rather than the cold-single one.
    const warm = () => at(1000, 0, { confirms: [vote()] });

    const [single] = buildBugle([warm()], HOME, NOW);
    expect(single.standfirst).toContain('1 pin ·');
    expect(single.headline).toContain('A SINGLE REPORT');

    const [pair] = buildBugle([warm(), warm()], HOME, NOW);
    expect(pair.standfirst).toContain('2 pins ·');
    expect(pair.headline).toContain('TWO REPORTS');
  });

  it('reports a hot multi-pin cluster as a menace', () => {
    const hot = Array.from({ length: 4 }, (_, i) =>
      at(1000 + i * 30, 0, { confirms: Array.from({ length: 12 }, () => vote()) }),
    );
    const [story] = buildBugle(hot, HOME, NOW);
    expect(story.headline).toContain('MENACE');
    expect(story.tone).toBe('alarm');
  });

  it('names every story after a sighting that exists', () => {
    const sightings = seedSightings(HOME, NOW);
    const ids = new Set(sightings.map((s) => s.id));
    for (const story of buildBugle(sightings, HOME, NOW)) {
      for (const id of story.sightingIds) expect(ids.has(id)).toBe(true);
    }
  });
});
