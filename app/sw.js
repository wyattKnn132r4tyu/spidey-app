// App-shell cache.
//
// All URLs are derived from the registration scope so this works unchanged
// whether the app is served from a domain root or a project subpath such as
// /spidey-app/ on GitHub Pages.
//
// Map tiles are deliberately left to the network — stale tiles are worse than
// no tiles, and caching a whole city is not polite to the tile provider.

const CACHE = 'spidey-shell-v2';
const scope = self.registration.scope;
const url = (path) => new URL(path, scope).toString();

const SHELL = [
  url('./'),
  url('./index.html'),
  url('./manifest.webmanifest'),
  url('./favicon.svg'),
  url('./icon-192.png'),
  url('./icon-512.png'),
  url('./apple-touch-icon.png'),
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches
      .open(CACHE)
      // One missing file should not fail the whole install.
      .then((cache) => Promise.allSettled(SHELL.map((href) => cache.add(href)))),
  );
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)))),
  );
  self.clients.claim();
});

self.addEventListener('fetch', (event) => {
  const request = event.request;
  if (request.method !== 'GET') return;
  if (new URL(request.url).origin !== self.location.origin) return;

  // Navigations: network first, falling back to the cached shell so a cold
  // launch from the home screen works with no signal.
  if (request.mode === 'navigate') {
    event.respondWith(
      fetch(request)
        .then((response) => {
          // Only a real page becomes the shell. A 404 or a captive-portal login
          // answers a fetch perfectly happily, and caching one of those means
          // every offline launch from then on opens an error page instead of
          // the app — a failure that outlives the network problem that caused it.
          if (response.ok && response.type === 'basic') {
            const copy = response.clone();
            caches.open(CACHE).then((cache) => cache.put(url('./index.html'), copy));
          }
          return response;
        })
        .catch(() => caches.match(url('./index.html')).then((hit) => hit ?? caches.match(url('./')))),
    );
    return;
  }

  // Hashed build assets never change under a given name, so cache first.
  event.respondWith(
    caches.match(request).then((hit) => {
      if (hit) return hit;
      return fetch(request).then((response) => {
        if (response.ok && response.type === 'basic') {
          const copy = response.clone();
          caches.open(CACHE).then((cache) => cache.put(request, copy));
        }
        return response;
      });
    }),
  );
});
