// @vitest-environment node
import { beforeEach, describe, expect, it, vi } from 'vitest';
// The worker's real source, verbatim — not a copy that can drift from it.
import swSource from '../../public/sw.js?raw';

/**
 * The service worker is plain JS served as-is, so it is exercised the way the
 * browser runs it: evaluated against a fake global scope that records the
 * listeners it registers, then driven by firing events at them.
 */
interface FetchEvent {
  request: { url: string; method: string; mode: string };
  respondWith: (response: Promise<Response>) => void;
}

function loadWorker() {
  const listeners: Record<string, (event: unknown) => void> = {};
  const cache = new Map<string, Response>();

  const scope = {
    registration: { scope: 'https://example.test/spidey-app/app/' },
    location: { origin: 'https://example.test' },
    addEventListener: (name: string, fn: (event: unknown) => void) => {
      listeners[name] = fn;
    },
    skipWaiting: () => {},
    clients: { claim: () => {} },
    caches: {
      open: () =>
        Promise.resolve({
          add: () => Promise.resolve(),
          put: (key: string, value: Response) => {
            cache.set(key, value);
            return Promise.resolve();
          },
        }),
      keys: () => Promise.resolve([]),
      match: (key: string) => Promise.resolve(cache.get(key)),
    },
  };

  new Function('self', 'caches', swSource).call(scope, scope, scope.caches);
  return { listeners, cache };
}

const navigation = (url = 'https://example.test/spidey-app/app/') => ({
  url,
  method: 'GET',
  mode: 'navigate',
});

/** Fires a fetch event and resolves with whatever the worker answered. */
async function navigate(
  listeners: Record<string, (event: unknown) => void>,
): Promise<Response | null> {
  let answered: Promise<Response> | undefined;
  const event: FetchEvent = {
    request: navigation(),
    respondWith: (response) => {
      answered = response;
    },
  };
  listeners.fetch(event);
  return answered ? await answered : null;
}

const SHELL_KEY = 'https://example.test/spidey-app/app/index.html';

describe('the app shell cache', () => {
  beforeEach(() => vi.unstubAllGlobals());

  it('caches a real page as the shell', async () => {
    const { listeners, cache } = loadWorker();
    const page = new Response('<!doctype html>app', { status: 200 });
    Object.defineProperty(page, 'type', { value: 'basic' });
    vi.stubGlobal('fetch', () => Promise.resolve(page));

    await navigate(listeners);
    await Promise.resolve();
    expect(cache.has(SHELL_KEY)).toBe(true);
  });

  it('does not cache an error page as the shell', async () => {
    // A 404 or a captive-portal login answers a fetch perfectly happily. Cached
    // as the shell, it becomes what every later offline launch opens — a failure
    // that outlives the network problem that caused it.
    const { listeners, cache } = loadWorker();
    const notFound = new Response('not found', { status: 404 });
    Object.defineProperty(notFound, 'type', { value: 'basic' });
    vi.stubGlobal('fetch', () => Promise.resolve(notFound));

    await navigate(listeners);
    await Promise.resolve();
    expect(cache.has(SHELL_KEY)).toBe(false);
  });

  it('serves the cached shell when the network is gone', async () => {
    const { listeners, cache } = loadWorker();
    const page = new Response('<!doctype html>app', { status: 200 });
    Object.defineProperty(page, 'type', { value: 'basic' });
    vi.stubGlobal('fetch', () => Promise.resolve(page));
    await navigate(listeners);
    await Promise.resolve();

    vi.stubGlobal('fetch', () => Promise.reject(new Error('offline')));
    const offline = await navigate(listeners);
    expect(await offline!.text()).toContain('app');
    expect(cache.size).toBe(1);
  });
});
