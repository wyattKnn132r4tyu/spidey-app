import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './index.css';
import App from './App.tsx';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);

/**
 * The native build loads from the app bundle, so there is nothing to cache and a
 * service worker would only sit between the app and its own files — worse, it
 * could serve stale assets after an app update.
 *
 * Checked two ways: Capacitor's bridge, and the scheme it serves under. The
 * bridge is injected before this runs, but the scheme holds even if it is not.
 */
const nativeShell =
  Boolean(
    (globalThis as unknown as { Capacitor?: { isNativePlatform?: () => boolean } }).Capacitor
      ?.isNativePlatform?.(),
  ) || !/^https?:$/.test(window.location.protocol);

if ('serviceWorker' in navigator && import.meta.env.PROD && !nativeShell) {
  window.addEventListener('load', () => {
    // BASE_URL keeps this correct under subpath hosting (GitHub Pages).
    const base = import.meta.env.BASE_URL;
    navigator.serviceWorker.register(`${base}sw.js`, { scope: base }).catch(() => {
      // An unavailable service worker only costs offline support.
    });
  });
}
