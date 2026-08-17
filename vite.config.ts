import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Served from https://<user>.github.io/spidey-app/, so assets need that prefix.
// Override with BASE_PATH=/ when hosting at a domain root.
const base = process.env.BASE_PATH ?? '/spidey-app/';

export default defineConfig({
  base,
  plugins: [react()],
});
