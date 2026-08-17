import { describe, expect, it } from 'vitest';
import {
  centroid,
  compassFrom,
  dayKey,
  distanceM,
  formatAgo,
  formatDistance,
  localDayKey,
  offset,
  routeLength,
} from './geo';

const TIMES_SQUARE = { lat: 40.758, lng: -73.9855 };
const CENTRAL_PARK = { lat: 40.7829, lng: -73.9654 };

describe('distanceM', () => {
  it('is zero between a point and itself', () => {
    expect(distanceM(TIMES_SQUARE, TIMES_SQUARE)).toBe(0);
  });

  it('matches a known distance', () => {
    // Times Square to Central Park is a little over 3 km.
    expect(distanceM(TIMES_SQUARE, CENTRAL_PARK)).toBeGreaterThan(3000);
    expect(distanceM(TIMES_SQUARE, CENTRAL_PARK)).toBeLessThan(3400);
  });

  it('is symmetric', () => {
    expect(distanceM(TIMES_SQUARE, CENTRAL_PARK)).toBeCloseTo(
      distanceM(CENTRAL_PARK, TIMES_SQUARE),
      6,
    );
  });

  it('handles antimeridian-crossing pairs without blowing up', () => {
    const west = { lat: 0, lng: 179.9 };
    const east = { lat: 0, lng: -179.9 };
    // A shade over 22 km apart the short way, not most of the way round the world.
    expect(distanceM(west, east)).toBeLessThan(25_000);
  });
});

describe('offset', () => {
  it('lands the requested distance away', () => {
    for (const bearing of [0, 45, 90, 180, 270, 359]) {
      const moved = offset(TIMES_SQUARE, 1500, bearing);
      expect(distanceM(TIMES_SQUARE, moved)).toBeCloseTo(1500, 3);
    }
  });

  it('moves north for bearing 0 and east for bearing 90', () => {
    expect(offset(TIMES_SQUARE, 1000, 0).lat).toBeGreaterThan(TIMES_SQUARE.lat);
    expect(offset(TIMES_SQUARE, 1000, 90).lng).toBeGreaterThan(TIMES_SQUARE.lng);
  });

  it('keeps longitude in range when crossing the antimeridian', () => {
    const near = { lat: 0, lng: 179.99 };
    const moved = offset(near, 5000, 90);
    expect(moved.lng).toBeGreaterThanOrEqual(-180);
    expect(moved.lng).toBeLessThanOrEqual(180);
  });
});

describe('compassFrom', () => {
  it('names the eight directions', () => {
    const cases: [number, string][] = [
      [0, 'north'],
      [45, 'north-east'],
      [90, 'east'],
      [135, 'south-east'],
      [180, 'south'],
      [225, 'south-west'],
      [270, 'west'],
      [315, 'north-west'],
    ];
    for (const [bearing, expected] of cases) {
      expect(compassFrom(TIMES_SQUARE, offset(TIMES_SQUARE, 2000, bearing))).toBe(expected);
    }
  });

  it('wraps 360 back round to north', () => {
    expect(compassFrom(TIMES_SQUARE, offset(TIMES_SQUARE, 2000, 359))).toBe('north');
  });
});

describe('centroid', () => {
  it('averages a set of points', () => {
    expect(centroid([{ lat: 0, lng: 0 }, { lat: 2, lng: 4 }])).toEqual({ lat: 1, lng: 2 });
  });

  it('returns the point itself for a single point', () => {
    expect(centroid([TIMES_SQUARE])).toEqual(TIMES_SQUARE);
  });
});

describe('routeLength', () => {
  it('is zero for an empty or single-point route', () => {
    expect(routeLength([])).toBe(0);
    expect(routeLength([TIMES_SQUARE])).toBe(0);
  });

  it('sums the legs', () => {
    const a = TIMES_SQUARE;
    const b = offset(a, 100, 0);
    const c = offset(b, 100, 90);
    expect(routeLength([a, b, c])).toBeCloseTo(200, 2);
  });
});

describe('formatDistance', () => {
  it('rounds metres to the nearest ten below a kilometre', () => {
    expect(formatDistance(0)).toBe('0 m');
    expect(formatDistance(4)).toBe('0 m');
    expect(formatDistance(86)).toBe('90 m');
    expect(formatDistance(999)).toBe('1000 m');
  });

  it('switches to kilometres at a kilometre', () => {
    expect(formatDistance(1000)).toBe('1.0 km');
    expect(formatDistance(2540)).toBe('2.5 km');
  });

  it('drops the decimal past ten kilometres', () => {
    expect(formatDistance(12_400)).toBe('12 km');
  });
});

describe('formatAgo', () => {
  const now = 1_760_000_000_000;

  it('describes recent, minute, hour and day scales', () => {
    expect(formatAgo(now, now)).toBe('just now');
    expect(formatAgo(now - 30_000, now)).toBe('just now');
    expect(formatAgo(now - 5 * 60_000, now)).toBe('5 min ago');
    expect(formatAgo(now - 3 * 3_600_000, now)).toBe('3 hr ago');
    expect(formatAgo(now - 26 * 3_600_000, now)).toBe('yesterday');
    expect(formatAgo(now - 72 * 3_600_000, now)).toBe('3 days ago');
  });

  it('does not report negative ages for clock skew', () => {
    expect(formatAgo(now + 60_000, now)).toBe('just now');
  });
});

describe('localDayKey', () => {
  it('follows the local calendar rather than UTC', () => {
    const at = new Date(2026, 7, 17, 22, 30);
    expect(localDayKey(at.getTime())).toBe('2026-08-17');
  });

  it('rolls over at local midnight', () => {
    const before = new Date(2026, 7, 17, 23, 59).getTime();
    const after = new Date(2026, 7, 18, 0, 1).getTime();
    expect(localDayKey(before)).toBe('2026-08-17');
    expect(localDayKey(after)).toBe('2026-08-18');
  });

  it('zero-pads months and days', () => {
    expect(localDayKey(new Date(2026, 0, 5, 12).getTime())).toBe('2026-01-05');
  });
});

describe('dayKey', () => {
  it('is stable within a UTC day and changes across midnight', () => {
    const morning = Date.UTC(2026, 7, 17, 1);
    const evening = Date.UTC(2026, 7, 17, 23);
    const next = Date.UTC(2026, 7, 18, 0, 1);

    expect(dayKey(morning)).toBe(dayKey(evening));
    expect(dayKey(next)).not.toBe(dayKey(evening));
    expect(dayKey(morning)).toBe('2026-08-17');
  });
});
