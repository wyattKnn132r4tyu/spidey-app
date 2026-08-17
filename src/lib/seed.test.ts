import { describe, expect, it } from 'vitest';
import { seedSightings } from './seed';
import { confidenceOf, heatOf } from './confidence';
import { distanceM } from './geo';

const HOME = { lat: 40.7484, lng: -73.9857 };
const NOW = 1_760_000_000_000;

describe('seedSightings', () => {
  it('is deterministic for the same day and location', () => {
    expect(seedSightings(HOME, NOW)).toEqual(seedSightings(HOME, NOW));
  });

  it('produces the same pins at different times of the same UTC day', () => {
    const morning = Date.UTC(2026, 7, 17, 2);
    const evening = Date.UTC(2026, 7, 17, 22);
    const a = seedSightings(HOME, morning).map((s) => s.lat);
    const b = seedSightings(HOME, evening).map((s) => s.lat);
    expect(a).toEqual(b);
  });

  it('produces a different set on a different day', () => {
    const today = seedSightings(HOME, Date.UTC(2026, 7, 17, 12)).map((s) => s.lat);
    const tomorrow = seedSightings(HOME, Date.UTC(2026, 7, 18, 12)).map((s) => s.lat);
    expect(today).not.toEqual(tomorrow);
  });

  it('never dates a sighting in the future', () => {
    // A future timestamp has its decay clamped, which would break the exact
    // exponential the cooldown countdown is derived from.
    for (const sighting of seedSightings(HOME, NOW)) {
      expect(sighting.createdAt).toBeLessThanOrEqual(NOW);
    }
  });

  it('never dates a vote in the future or before its sighting', () => {
    for (const sighting of seedSightings(HOME, NOW)) {
      for (const vote of [...sighting.confirms, ...sighting.denies]) {
        expect(vote.createdAt).toBeLessThanOrEqual(NOW);
        expect(vote.createdAt).toBeGreaterThanOrEqual(sighting.createdAt);
      }
    }
  });

  it('puts every sighting within a few kilometres of home', () => {
    for (const sighting of seedSightings(HOME, NOW)) {
      expect(distanceM(HOME, sighting)).toBeLessThan(6000);
    }
  });

  it('fills all three heat bands so the map demonstrates the model', () => {
    const bands = new Set(seedSightings(HOME, NOW).map((s) => heatOf(s, NOW)));
    expect(bands).toEqual(new Set(['hot', 'warm', 'cold']));
  });

  it('returns newest first', () => {
    const times = seedSightings(HOME, NOW).map((s) => s.createdAt);
    expect(times).toEqual([...times].sort((a, b) => b - a));
  });

  it('gives every sighting a unique id', () => {
    const ids = seedSightings(HOME, NOW).map((s) => s.id);
    expect(new Set(ids).size).toBe(ids.length);
  });

  it('marks every seeded id so user pins can be told apart', () => {
    for (const sighting of seedSightings(HOME, NOW)) {
      expect(sighting.id.startsWith('seed-')).toBe(true);
    }
  });

  it('produces confidences inside the valid range', () => {
    for (const sighting of seedSightings(HOME, NOW)) {
      const confidence = confidenceOf(sighting, NOW);
      expect(confidence).toBeGreaterThanOrEqual(0);
      expect(confidence).toBeLessThanOrEqual(1);
    }
  });

  it('works at extreme latitudes without producing invalid coordinates', () => {
    for (const home of [{ lat: 78.2, lng: 15.6 }, { lat: -54.8, lng: -68.3 }]) {
      for (const sighting of seedSightings(home, NOW)) {
        expect(Number.isFinite(sighting.lat)).toBe(true);
        expect(Number.isFinite(sighting.lng)).toBe(true);
        expect(Math.abs(sighting.lat)).toBeLessThanOrEqual(90);
        expect(Math.abs(sighting.lng)).toBeLessThanOrEqual(180);
      }
    }
  });

  it('works either side of the antimeridian', () => {
    for (const sighting of seedSightings({ lat: -16.9, lng: 179.9 }, NOW)) {
      expect(Math.abs(sighting.lng)).toBeLessThanOrEqual(180);
      expect(distanceM({ lat: -16.9, lng: 179.9 }, sighting)).toBeLessThan(6000);
    }
  });
});
