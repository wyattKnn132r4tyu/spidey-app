import { describe, expect, it } from 'vitest';
import {
  HALF_LIFE_MS,
  confidenceOf,
  heatFromConfidence,
  heatOf,
  isLive,
  msUntilHeat,
  scoreOf,
} from './confidence';
import type { Sighting, Vote } from '../types';

const NOW = 1_760_000_000_000;

const vote = (over: Partial<Vote> = {}): Vote => ({
  userId: 'someone',
  distanceM: 0,
  onPatrol: false,
  createdAt: NOW,
  ...over,
});

const sighting = (over: Partial<Sighting> = {}): Sighting => ({
  id: 's1',
  lat: 40.7484,
  lng: -73.9857,
  createdAt: NOW,
  tag: 'swinging',
  reporterId: 'me',
  reporterHandle: 'me',
  reportedOnPatrol: false,
  confirms: [],
  denies: [],
  ...over,
});

describe('scoreOf', () => {
  it('starts at the tag weight for a fresh unvoted report', () => {
    expect(scoreOf(sighting(), NOW)).toBeCloseTo(1, 10);
  });

  it('weights a report made on patrol more heavily', () => {
    const plain = scoreOf(sighting(), NOW);
    const patrolling = scoreOf(sighting({ reportedOnPatrol: true }), NOW);
    expect(patrolling).toBeGreaterThan(plain);
    expect(patrolling / plain).toBeCloseTo(1.3, 10);
  });

  it('halves over one half-life', () => {
    const s = sighting();
    expect(scoreOf(s, NOW + HALF_LIFE_MS)).toBeCloseTo(scoreOf(s, NOW) / 2, 10);
  });

  it('halves again over a second half-life', () => {
    const s = sighting();
    expect(scoreOf(s, NOW + 2 * HALF_LIFE_MS)).toBeCloseTo(scoreOf(s, NOW) / 4, 10);
  });

  it('counts a nearby confirmation more than a distant one', () => {
    const near = scoreOf(sighting({ confirms: [vote({ distanceM: 0 })] }), NOW);
    const far = scoreOf(sighting({ confirms: [vote({ distanceM: 2000 })] }), NOW);
    expect(near).toBeGreaterThan(far);
  });

  it('counts a confirmation from someone on patrol more', () => {
    const couch = scoreOf(sighting({ confirms: [vote()] }), NOW);
    const patrol = scoreOf(sighting({ confirms: [vote({ onPatrol: true })] }), NOW);
    expect(patrol / couch).toBeGreaterThan(1);
  });

  it('never goes negative, however many denials arrive', () => {
    const denies = Array.from({ length: 50 }, () => vote());
    expect(scoreOf(sighting({ denies }), NOW)).toBe(0);
  });

  it('lets denials outweigh an equal number of equally close confirmations', () => {
    const both = sighting({ confirms: [vote()], denies: [vote()] });
    // Base survives, but the vote pair nets out negative.
    expect(scoreOf(both, NOW)).toBeLessThan(scoreOf(sighting(), NOW));
  });

  it('ignores votes dated in the future rather than inflating the score', () => {
    const future = scoreOf(sighting({ confirms: [vote({ createdAt: NOW + 60_000 })] }), NOW);
    const present = scoreOf(sighting({ confirms: [vote({ createdAt: NOW })] }), NOW);
    // Clamped to full weight, not extrapolated above it.
    expect(future).toBeCloseTo(present, 10);
  });
});

describe('confidenceOf', () => {
  it('stays within 0..1', () => {
    const many = Array.from({ length: 200 }, () => vote());
    expect(confidenceOf(sighting({ confirms: many }), NOW)).toBeLessThanOrEqual(1);
    expect(confidenceOf(sighting({ denies: many }), NOW)).toBeGreaterThanOrEqual(0);
  });

  it('is monotonic in the number of confirmations', () => {
    const values = [0, 1, 2, 5, 10].map((n) =>
      confidenceOf(sighting({ confirms: Array.from({ length: n }, () => vote()) }), NOW),
    );
    const sorted = [...values].sort((a, b) => a - b);
    expect(values).toEqual(sorted);
  });

  it('decreases as a sighting ages', () => {
    const s = sighting({ confirms: [vote(), vote()] });
    const hours = [0, 1, 2, 6].map((h) => confidenceOf(s, NOW + h * 3_600_000));
    for (let i = 1; i < hours.length; i++) expect(hours[i]).toBeLessThan(hours[i - 1]);
  });
});

describe('heat bands', () => {
  it('maps confidence to bands at the documented thresholds', () => {
    expect(heatFromConfidence(0.9)).toBe('hot');
    expect(heatFromConfidence(0.66)).toBe('hot');
    expect(heatFromConfidence(0.65)).toBe('warm');
    expect(heatFromConfidence(0.3)).toBe('warm');
    expect(heatFromConfidence(0.29)).toBe('cold');
    expect(heatFromConfidence(0)).toBe('cold');
  });

  it('rates a well-confirmed fresh sighting hot', () => {
    const confirms = Array.from({ length: 10 }, () => vote({ distanceM: 100 }));
    expect(heatOf(sighting({ confirms }), NOW)).toBe('hot');
  });

  it('rates a lone unconfirmed report cold', () => {
    expect(heatOf(sighting(), NOW)).toBe('cold');
  });
});

describe('msUntilHeat', () => {
  it('predicts the exact moment a sighting leaves the hot band', () => {
    const s = sighting({ confirms: Array.from({ length: 10 }, () => vote({ distanceM: 100 })) });
    const ms = msUntilHeat(s, 'warm', NOW);

    expect(ms).not.toBeNull();
    expect(heatOf(s, NOW + ms! - 1000)).toBe('hot');
    expect(heatOf(s, NOW + ms! + 1000)).toBe('warm');
  });

  it('predicts the exact moment a sighting goes cold', () => {
    const s = sighting({ confirms: Array.from({ length: 4 }, () => vote({ distanceM: 300 })) });
    const ms = msUntilHeat(s, 'cold', NOW);

    expect(ms).not.toBeNull();
    expect(heatOf(s, NOW + ms! - 1000)).not.toBe('cold');
    expect(heatOf(s, NOW + ms! + 1000)).toBe('cold');
  });

  it('returns null once the sighting is already at or past the target band', () => {
    expect(msUntilHeat(sighting(), 'cold', NOW)).toBeNull();
  });

  it('returns null for a fully denied sighting rather than dividing by zero', () => {
    const denies = Array.from({ length: 20 }, () => vote());
    expect(msUntilHeat(sighting({ denies }), 'cold', NOW)).toBeNull();
  });

  it('holds even when votes arrived at different times', () => {
    // The closed form assumes every contribution shares one half-life, which is
    // what makes a mixed-age pin still collapse to a single exponential.
    const s = sighting({
      createdAt: NOW - 40 * 60_000,
      confirms: [
        vote({ createdAt: NOW - 30 * 60_000 }),
        vote({ createdAt: NOW - 5 * 60_000 }),
        vote({ createdAt: NOW }),
      ],
    });
    const ms = msUntilHeat(s, 'cold', NOW);
    expect(ms).not.toBeNull();
    expect(heatOf(s, NOW + ms! - 1000)).not.toBe('cold');
    expect(heatOf(s, NOW + ms! + 1000)).toBe('cold');
  });
});

describe('isLive', () => {
  it('keeps a fresh sighting and drops a long-dead one', () => {
    expect(isLive(sighting(), NOW)).toBe(true);
    expect(isLive(sighting(), NOW + 24 * 3_600_000)).toBe(false);
  });
});
