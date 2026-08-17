#!/usr/bin/env node
/**
 * Bundles the app's seeding + confidence code into the Scriptable widget.
 *
 * Scriptable needs one self-contained file on the phone, but the widget has to
 * agree with the app about what is hot — so rather than hand-copying the maths,
 * the real modules are bundled to an IIFE exposed as `SpideyCore` and prepended
 * to widgets/ios/shell.js.
 *
 * Run: npm run build:widget
 */
import { build } from 'esbuild';
import { readFile, writeFile } from 'node:fs/promises';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const out = resolve(root, 'widgets/ios/SpideyWidget.js');

const bundle = await build({
  entryPoints: [resolve(root, 'widgets/ios/core-entry.ts')],
  bundle: true,
  write: false,
  format: 'iife',
  globalName: 'SpideyCore',
  // Scriptable runs JavaScriptCore on a modern iOS; es2020 is comfortably safe.
  target: 'es2020',
  platform: 'neutral',
  legalComments: 'none',
});

const core = bundle.outputFiles[0].text;
const shell = await readFile(resolve(root, 'widgets/ios/shell.js'), 'utf8');

// Scriptable requires its directives on the first lines, so the shell's header
// comes first and the bundle is spliced in after it.
const marker = '// icon-color: red; icon-glyph: spider;';
const at = shell.indexOf(marker);
if (at === -1) throw new Error('shell.js is missing the Scriptable header directives');

const head = shell.slice(0, at + marker.length);
const body = shell.slice(at + marker.length);

await writeFile(
  out,
  `${head}

// ---------------------------------------------------------------------------
// GENERATED — do not edit. Built from src/lib by scripts/build-widget.mjs.
// Edit widgets/ios/shell.js and run: npm run build:widget
// ---------------------------------------------------------------------------
${core}
${body}`,
  'utf8',
);

console.log(`built ${out} (${Math.round(core.length / 1024)} kB of shared core)`);
