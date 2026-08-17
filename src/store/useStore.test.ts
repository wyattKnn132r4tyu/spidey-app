// @vitest-environment jsdom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { DEFAULT_HOME, useStore } from './useStore';
import { dayKey, distanceM, offset } from '../lib/geo';
import { makeProfile } from '../lib/storage';

const KEY = 'spidey-tracker:v1';
const HOME = { lat: 40.7484, lng: -73.9857 };

/** Replaces navigator.geolocation with one that returns a fixed fix, or fails. */
function stubGeolocation(fix: { lat: number; lng: number } | null) {
  Object.defineProperty(navigator, 'geolocation', {
    configurable: true,
    value: {
      getCurrentPosition: (ok: PositionCallback, fail?: PositionErrorCallback) => {
        if (fix) ok({ coords: { latitude: fix.lat, longitude: fix.lng } } as GeolocationPosition);
        else fail?.({ code: 1, message: 'denied' } as GeolocationPositionError);
      },
      watchPosition: () => 1,
      clearWatch: () => {},
    },
  });
}

const stored = () => JSON.parse(localStorage.getItem(KEY)!);

const freshStore = () => {
  useStore.setState({
    ready: false,
    home: DEFAULT_HOME,
    position: null,
    locationDenied: false,
    sightings: [],
    patrols: [],
    activePatrol: null,
    profile: makeProfile(),
    tab: 'map',
    selectedId: null,
    reporting: false,
    showHeat: true,
  });
};

beforeEach(() => {
  localStorage.clear();
  stubGeolocation(HOME);
  freshStore();
});

afterEach(() => {
  vi.useRealTimers();
});

describe('init', () => {
  it('seeds the city and persists it on a first run', async () => {
    await useStore.getState().init();

    expect(useStore.getState().sightings.length).toBeGreaterThan(0);
    expect(useStore.getState().ready).toBe(true);
    expect(stored().sightings.length).toBe(useStore.getState().sightings.length);
    expect(stored().seededDay).toBe(dayKey(Date.now()));
  });

  it('centres on the real fix when location is granted', async () => {
    await useStore.getState().init();
    expect(useStore.getState().home).toEqual(HOME);
    expect(useStore.getState().locationDenied).toBe(false);
  });

  it('falls back to midtown and flags the denial when location is refused', async () => {
    stubGeolocation(null);
    await useStore.getState().init();

    expect(useStore.getState().home).toEqual(DEFAULT_HOME);
    expect(useStore.getState().locationDenied).toBe(true);
    expect(useStore.getState().sightings.length).toBeGreaterThan(0);
  });

  it('reuses the stored city on a second run the same day', async () => {
    await useStore.getState().init();
    const first = useStore.getState().sightings.map((s) => s.id);

    freshStore();
    await useStore.getState().init();

    expect(useStore.getState().sightings.map((s) => s.id)).toEqual(first);
  });

  it('reseeds on a new day and keeps pins the user dropped', async () => {
    await useStore.getState().init();
    useStore.getState().report('swinging', 'mine');
    const mine = useStore.getState().sightings.find((s) => !s.id.startsWith('seed-'))!;

    // Age the stored state by a day.
    const state = stored();
    state.seededDay = '2020-01-01';
    localStorage.setItem(KEY, JSON.stringify(state));

    freshStore();
    await useStore.getState().init();

    const after = useStore.getState().sightings;
    expect(after.find((s) => s.id === mine.id)?.note).toBe('mine');
    expect(after.filter((s) => s.id.startsWith('seed-')).length).toBeGreaterThan(0);
    expect(new Set(after.map((s) => s.id)).size).toBe(after.length);
  });

  it('reseeds around the user after they move to another city', async () => {
    await useStore.getState().init();

    const faraway = offset(HOME, 400_000, 90);
    stubGeolocation(faraway);
    freshStore();
    await useStore.getState().init();

    for (const sighting of useStore.getState().sightings) {
      expect(distanceM(faraway, sighting)).toBeLessThan(10_000);
    }
  });

  it('survives corrupt stored state instead of failing to open', async () => {
    localStorage.setItem(KEY, '{not json');
    await useStore.getState().init();
    expect(useStore.getState().sightings.length).toBeGreaterThan(0);
  });
});

describe('report', () => {
  it('drops a pin at the current position and selects it', async () => {
    await useStore.getState().init();
    useStore.setState({ position: HOME });

    useStore.getState().report('stopped-something', '  something happened  ');
    const pin = useStore.getState().sightings[0];

    expect(pin.tag).toBe('stopped-something');
    expect(pin.note).toBe('something happened');
    expect(pin.lat).toBe(HOME.lat);
    expect(useStore.getState().selectedId).toBe(pin.id);
    expect(useStore.getState().reporting).toBe(false);
  });

  it('leaves the note off entirely when it is blank', async () => {
    await useStore.getState().init();
    useStore.getState().report('swinging', '   ');
    expect(useStore.getState().sightings[0].note).toBeUndefined();
  });

  it('marks the pin as reported on patrol and logs it against the patrol', async () => {
    await useStore.getState().init();
    useStore.getState().startPatrol();
    useStore.getState().report('swinging', '');

    const pin = useStore.getState().sightings[0];
    expect(pin.reportedOnPatrol).toBe(true);
    expect(useStore.getState().activePatrol!.sightingIds).toContain(pin.id);
  });

  it('persists the new pin', async () => {
    await useStore.getState().init();
    useStore.getState().report('swinging', 'saved');
    expect(stored().sightings[0].note).toBe('saved');
  });

  it('drops the pin where the map is looking when there is no fix', async () => {
    stubGeolocation(null);
    await useStore.getState().init();

    const looking = offset(DEFAULT_HOME, 3000, 90);
    useStore.getState().setMapCentre(looking);
    useStore.getState().report('swinging', '');

    const pin = useStore.getState().sightings[0];
    expect(distanceM(looking, pin)).toBeLessThan(1);
  });

  it('prefers the real fix over the map centre', async () => {
    await useStore.getState().init();
    useStore.setState({ position: HOME });
    useStore.getState().setMapCentre(offset(HOME, 5000, 90));

    useStore.getState().report('swinging', '');
    expect(distanceM(HOME, useStore.getState().sightings[0])).toBeLessThan(1);
  });
});

describe('vote', () => {
  it('records a confirmation with the distance it was cast from', async () => {
    await useStore.getState().init();
    const target = useStore.getState().sightings[0];
    useStore.setState({ position: HOME });

    useStore.getState().vote(target.id, 'confirm');
    const after = useStore.getState().sightings.find((s) => s.id === target.id)!;

    // The seed already gave this pin votes; ours is the one carrying our id.
    expect(after.confirms).toHaveLength(target.confirms.length + 1);
    const mine = after.confirms.find((v) => v.userId === useStore.getState().profile.id)!;
    expect(mine.distanceM).toBeCloseTo(distanceM(HOME, target), 6);
  });

  it('counts one vote per user however many times they tap', async () => {
    await useStore.getState().init();
    const target = useStore.getState().sightings[0];
    const me = useStore.getState().profile.id;

    useStore.getState().vote(target.id, 'confirm');
    useStore.getState().vote(target.id, 'confirm');
    useStore.getState().vote(target.id, 'confirm');

    const after = useStore.getState().sightings.find((s) => s.id === target.id)!;
    expect(after.confirms.filter((v) => v.userId === me)).toHaveLength(1);
  });

  it('moves the vote rather than stacking one when the user changes their mind', async () => {
    await useStore.getState().init();
    const target = useStore.getState().sightings[0];
    const me = useStore.getState().profile.id;

    useStore.getState().vote(target.id, 'confirm');
    useStore.getState().vote(target.id, 'deny');

    const after = useStore.getState().sightings.find((s) => s.id === target.id)!;
    expect(after.confirms.filter((v) => v.userId === me)).toHaveLength(0);
    expect(after.denies.filter((v) => v.userId === me)).toHaveLength(1);
  });

  it('leaves other sightings untouched', async () => {
    await useStore.getState().init();
    const [first, second] = useStore.getState().sightings;
    const before = second.confirms.length;

    useStore.getState().vote(first.id, 'confirm');

    const after = useStore.getState().sightings.find((s) => s.id === second.id)!;
    expect(after.confirms).toHaveLength(before);
  });

  it('ignores a vote for a sighting that does not exist', async () => {
    await useStore.getState().init();
    const before = useStore.getState().sightings;
    useStore.getState().vote('nope', 'confirm');
    expect(useStore.getState().sightings).toEqual(before);
  });
});

describe('patrol', () => {
  it('accumulates distance as the user moves', async () => {
    await useStore.getState().init();
    useStore.setState({ position: HOME });
    useStore.getState().startPatrol();

    let point = HOME;
    for (let i = 0; i < 5; i++) {
      point = offset(point, 100, 0);
      useStore.getState().setPosition(point);
    }

    expect(useStore.getState().activePatrol!.distanceM).toBeCloseTo(500, 0);
    expect(useStore.getState().activePatrol!.route).toHaveLength(6);
  });

  it('ignores GPS jitter below the minimum step', async () => {
    await useStore.getState().init();
    useStore.setState({ position: HOME });
    useStore.getState().startPatrol();

    for (let i = 0; i < 10; i++) useStore.getState().setPosition(offset(HOME, 2, i * 36));

    expect(useStore.getState().activePatrol!.distanceM).toBe(0);
    expect(useStore.getState().activePatrol!.route).toHaveLength(1);
  });

  it('discards low-accuracy fixes instead of banking them as distance', async () => {
    await useStore.getState().init();
    useStore.setState({ position: HOME });
    useStore.getState().startPatrol();

    // A cell-tower fix half a kilometre away: the user did not walk that.
    useStore.getState().setPosition(offset(HOME, 500, 0), 1200);

    expect(useStore.getState().activePatrol!.distanceM).toBe(0);
    expect(useStore.getState().activePatrol!.route).toHaveLength(1);
  });

  it('accepts fixes that report good accuracy', async () => {
    await useStore.getState().init();
    useStore.setState({ position: HOME });
    useStore.getState().startPatrol();

    useStore.getState().setPosition(offset(HOME, 200, 0), 12);

    expect(useStore.getState().activePatrol!.distanceM).toBeCloseTo(200, 0);
  });

  it('measures a winding route by its legs, not end to end', async () => {
    await useStore.getState().init();
    useStore.setState({ position: HOME });
    useStore.getState().startPatrol();

    // There and back: 400 m covered, finishing where it started.
    const out = offset(HOME, 200, 0);
    useStore.getState().setPosition(out);
    useStore.getState().setPosition(HOME);

    expect(useStore.getState().activePatrol!.distanceM).toBeCloseTo(400, 0);
  });

  it('does not record a route when no patrol is running', async () => {
    await useStore.getState().init();
    useStore.getState().setPosition(offset(HOME, 500, 0));
    expect(useStore.getState().activePatrol).toBeNull();
  });

  it('files the patrol into history when stopped', async () => {
    await useStore.getState().init();
    useStore.setState({ position: HOME });
    useStore.getState().startPatrol();
    useStore.getState().setPosition(offset(HOME, 300, 0));
    useStore.getState().stopPatrol();

    const { patrols, activePatrol } = useStore.getState();
    expect(activePatrol).toBeNull();
    expect(patrols).toHaveLength(1);
    expect(patrols[0].endedAt).toBeDefined();
    expect(patrols[0].distanceM).toBeCloseTo(300, 0);
    expect(stored().patrols).toHaveLength(1);
  });

  it('does nothing when stopping with no patrol running', async () => {
    await useStore.getState().init();
    useStore.getState().stopPatrol();
    expect(useStore.getState().patrols).toHaveLength(0);
  });

  it('starts a streak at one', async () => {
    await useStore.getState().init();
    useStore.getState().startPatrol();
    useStore.getState().stopPatrol();
    expect(useStore.getState().profile.streakDays).toBe(1);
  });

  it('does not double-count two patrols on the same day', async () => {
    await useStore.getState().init();
    for (let i = 0; i < 3; i++) {
      useStore.getState().startPatrol();
      useStore.getState().stopPatrol();
    }
    expect(useStore.getState().profile.streakDays).toBe(1);
  });

  it('extends the streak on consecutive days', async () => {
    await useStore.getState().init();
    vi.useFakeTimers();

    vi.setSystemTime(new Date('2026-08-17T12:00:00Z'));
    useStore.getState().startPatrol();
    useStore.getState().stopPatrol();

    vi.setSystemTime(new Date('2026-08-18T12:00:00Z'));
    useStore.getState().startPatrol();
    useStore.getState().stopPatrol();

    expect(useStore.getState().profile.streakDays).toBe(2);
  });

  it('counts an evening that crosses UTC midnight as one local day', async () => {
    // The suite runs in America/New_York: both of these are the evening of the
    // 17th locally, but different days in UTC. On UTC days this awards a second
    // streak day for one evening's patrolling.
    await useStore.getState().init();
    vi.useFakeTimers();

    vi.setSystemTime(new Date('2026-08-17T22:00:00Z')); // 18:00 local, the 17th
    useStore.getState().startPatrol();
    useStore.getState().stopPatrol();

    vi.setSystemTime(new Date('2026-08-18T02:00:00Z')); // 22:00 local, still the 17th
    useStore.getState().startPatrol();
    useStore.getState().stopPatrol();

    expect(useStore.getState().profile.streakDays).toBe(1);
  });

  it('resets the streak after a missed day', async () => {
    await useStore.getState().init();
    vi.useFakeTimers();

    vi.setSystemTime(new Date('2026-08-17T12:00:00Z'));
    useStore.getState().startPatrol();
    useStore.getState().stopPatrol();

    vi.setSystemTime(new Date('2026-08-20T12:00:00Z'));
    useStore.getState().startPatrol();
    useStore.getState().stopPatrol();

    expect(useStore.getState().profile.streakDays).toBe(1);
  });
});

describe('reset', () => {
  it('clears history and reseeds', async () => {
    await useStore.getState().init();
    useStore.getState().report('swinging', 'mine');
    useStore.getState().startPatrol();
    useStore.getState().stopPatrol();

    useStore.getState().reset();

    const state = useStore.getState();
    expect(state.patrols).toHaveLength(0);
    expect(state.activePatrol).toBeNull();
    expect(state.selectedId).toBeNull();
    expect(state.sightings.every((s) => s.id.startsWith('seed-'))).toBe(true);
    expect(state.sightings.length).toBeGreaterThan(0);
  });

  it('clears the streak too, since the patrols behind it are gone', async () => {
    await useStore.getState().init();
    useStore.getState().startPatrol();
    useStore.getState().stopPatrol();
    expect(useStore.getState().profile.streakDays).toBe(1);

    useStore.getState().reset();
    expect(useStore.getState().profile.streakDays).toBe(0);
    expect(useStore.getState().profile.lastPatrolDay).toBeUndefined();
  });
});
