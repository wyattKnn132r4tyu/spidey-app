// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest';

/**
 * A fake Web Audio context that records every oscillator started. One blip makes
 * exactly one, so the count is "how many sounds came out".
 *
 * The module caches its AudioContext for the life of the page, which is right in
 * a browser and wrong in a test file — a context built under one test's stub
 * keeps reporting into that test's array. So each test gets the module fresh.
 */
async function freshSound() {
  const played: string[] = [];
  const chain = () => ({ connect: (next: unknown) => next });

  class FakeContext {
    state = 'running';
    currentTime = 0;
    destination = {};
    resume = () => Promise.resolve();
    createOscillator() {
      return {
        ...chain(),
        type: '',
        frequency: { value: 0 },
        start: () => played.push('on'),
        stop: () => {},
      };
    }
    createGain() {
      return {
        ...chain(),
        gain: { setValueAtTime: () => {}, exponentialRampToValueAtTime: () => {} },
      };
    }
  }

  vi.stubGlobal('AudioContext', FakeContext);
  vi.resetModules();
  return { played, ...(await import('./sound')) };
}

describe('the mute gate', () => {
  beforeEach(() => vi.resetModules());

  it('plays a blip when sound is on', async () => {
    const { played, blip } = await freshSound();
    blip('tap');
    expect(played).toHaveLength(1);
  });

  it('plays nothing at all once muted', async () => {
    const { played, blip, setMuted } = await freshSound();
    setMuted(true);
    // Every kind, because the gate lives inside blip() rather than at the call
    // sites — which is the point. It used to be at neither, so the speaker key
    // changed its own glyph and left all of these sounding.
    for (const name of ['tap', 'drop', 'confirm', 'deny', 'sense'] as const) blip(name);
    expect(played).toHaveLength(0);
  });

  it('comes back when unmuted', async () => {
    const { played, blip, setMuted } = await freshSound();
    setMuted(true);
    blip('tap');
    expect(played).toHaveLength(0);

    setMuted(false);
    blip('tap');
    expect(played).toHaveLength(1);
  });
});
