import type { CapacitorConfig } from '@capacitor/cli';

/**
 * The native iOS wrapper.
 *
 * Capacitor compiles the same app into a real iOS binary: its own icon, its own
 * process, no browser anywhere in the interface. The web build is loaded from
 * the app bundle rather than a server, which is why the iOS build uses a
 * relative base path (`npm run build:ios`).
 */
const config: CapacitorConfig = {
  appId: 'com.spidey.tracker',
  appName: 'Spidey Tracker',
  webDir: 'dist',
  ios: {
    // The arcade bezel runs to the edges; the app draws its own safe areas.
    contentInset: 'never',
    backgroundColor: '#3a87b5',
  },
  server: {
    // Local files only. Nothing is fetched from a site at runtime.
    androidScheme: 'https',
    iosScheme: 'capacitor',
  },
};

export default config;
