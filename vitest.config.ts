import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    include: ['src/**/*.test.ts'],
    env: {
      // Deliberately not UTC. Streaks and the seed key deliberately use different
      // notions of "day", and under UTC a bug in either is invisible.
      TZ: 'America/New_York',
    },
  },
});
