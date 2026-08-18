// Variables used by Scriptable.
// These must be at the very top of the file. Do not edit.
// icon-color: red; icon-glyph: spider;

/**
 * Spidey Tracker — iOS home screen / lock screen widget.
 *
 * Runs in Scriptable (free, App Store). See widgets/ios/README.md for setup.
 *
 * Everything above this comment block is generated: it is the app's own seeding
 * and confidence code, bundled by `npm run build:widget`, so the widget and the
 * app always agree about what is hot. Edit widgets/ios/shell.js, not this file.
 */

// Where tapping the widget takes you. Replace if you host it elsewhere.
const APP_URL = 'https://wyattknn132r4tyu.github.io/spidey-app/';

// The arcade palette, matching the app and the Android widget.
const COLOR = {
  bg: new Color('#071734'),
  card: new Color('#0b1e3b'),
  red: new Color('#e23b3b'),
  hot: new Color('#e23b3b'),
  warm: new Color('#6abe4f'),
  cold: new Color('#c9d4dc'),
  text: new Color('#cfebff'),
  muted: new Color('#7fa6c8'),
  amber: new Color('#f2a93b'),
};

const HEAT_COLOR = { hot: COLOR.hot, warm: COLOR.warm, cold: COLOR.cold };

/** Midtown, used only if there is no fix and nothing cached. */
const FALLBACK = { lat: 40.7484, lng: -73.9857 };
const LOCATION_KEY = 'spidey-widget-last-location';

/**
 * A widget gets a short window to run and location can fail outright in the
 * background, so the last good fix is kept in the keychain and reused.
 */
async function resolveLocation() {
  try {
    Location.setAccuracyToHundredMeters();
    const fix = await Location.current();
    const value = { lat: fix.latitude, lng: fix.longitude };
    Keychain.set(LOCATION_KEY, JSON.stringify(value));
    return { ...value, source: 'live' };
  } catch {
    if (Keychain.contains(LOCATION_KEY)) {
      try {
        return { ...JSON.parse(Keychain.get(LOCATION_KEY)), source: 'cached' };
      } catch {
        // Fall through to the default below.
      }
    }
    return { ...FALLBACK, source: 'fallback' };
  }
}

function summarise(home) {
  const now = Date.now();
  const sightings = SpideyCore.seedSightings(home, now);

  const counts = { hot: 0, warm: 0, cold: 0 };
  for (const sighting of sightings) counts[SpideyCore.heatOf(sighting, now)] += 1;

  const ranked = sightings
    .map((sighting) => ({
      sighting,
      heat: SpideyCore.heatOf(sighting, now),
      confidence: SpideyCore.confidenceOf(sighting, now),
      away: SpideyCore.distanceM(home, sighting),
      where: SpideyCore.compassFrom(home, sighting),
    }))
    // Hottest first, then nearest — what you would actually chase.
    .sort((a, b) => b.confidence - a.confidence || a.away - b.away);

  const top = ranked[0];
  const fade = top
    ? SpideyCore.msUntilHeat(top.sighting, top.heat === 'hot' ? 'warm' : 'cold', now)
    : null;

  const clusters = SpideyCore.clusterSightings(sightings, now)
    .map((cluster) => ({
      count: cluster.sightings.length,
      confidence: cluster.confidence,
      away: SpideyCore.distanceM(home, cluster.centre),
      where: SpideyCore.compassFrom(home, cluster.centre),
      heat: cluster.confidence >= 0.66 ? 'hot' : cluster.confidence >= 0.3 ? 'warm' : 'cold',
    }))
    .sort((a, b) => b.confidence - a.confidence);

  return { counts, top, fade, clusters, now };
}

const minutes = (ms) => {
  const total = Math.round(ms / 60000);
  return total < 60 ? `${total}m` : `${Math.floor(total / 60)}h ${total % 60}m`;
};

function header(stack, subtitle) {
  const row = stack.addStack();
  row.centerAlignContent();

  const mark = row.addText('🕸');
  mark.font = Font.systemFont(11);

  row.addSpacer(4);
  const title = row.addText('SPIDEY TRACKER');
  title.font = Font.boldSystemFont(9);
  title.textColor = COLOR.amber;

  if (subtitle) {
    row.addSpacer();
    const note = row.addText(subtitle);
    note.font = Font.systemFont(9);
    note.textColor = COLOR.muted;
  }
}

function buildSmall(widget, data) {
  header(widget, null);
  widget.addSpacer(6);

  const count = widget.addText(`${data.counts.hot}`);
  count.font = Font.boldSystemFont(38);
  count.textColor = data.counts.hot > 0 ? COLOR.hot : COLOR.muted;

  const label = widget.addText(data.counts.hot === 1 ? 'HOT SIGHTING' : 'HOT SIGHTINGS');
  label.font = Font.boldSystemFont(9);
  label.textColor = COLOR.muted;

  widget.addSpacer(6);

  const warm = widget.addText(`${data.counts.warm} warm · ${data.counts.cold} cold`);
  warm.font = Font.systemFont(10);
  warm.textColor = COLOR.warm;

  if (data.top) {
    const nearest = widget.addText(
      `nearest ${SpideyCore.formatDistance(data.top.away)} ${data.top.where}`,
    );
    nearest.font = Font.systemFont(10);
    nearest.textColor = COLOR.muted;
    nearest.lineLimit = 1;

    if (data.fade !== null) {
      const fade = widget.addText(
        `${data.top.heat === 'hot' ? 'cools' : 'cold'} in ${minutes(data.fade)}`,
      );
      fade.font = Font.systemFont(10);
      fade.textColor = HEAT_COLOR[data.top.heat];
    }
  }
}

function buildMedium(widget, data) {
  const row = widget.addStack();

  const left = row.addStack();
  left.layoutVertically();
  left.size = new Size(96, 0);
  header(left, null);
  left.addSpacer(4);

  const count = left.addText(`${data.counts.hot}`);
  count.font = Font.boldSystemFont(36);
  count.textColor = data.counts.hot > 0 ? COLOR.hot : COLOR.muted;

  const label = left.addText('HOT');
  label.font = Font.boldSystemFont(9);
  label.textColor = COLOR.muted;

  left.addSpacer(4);
  const warm = left.addText(`${data.counts.warm} warm`);
  warm.font = Font.systemFont(10);
  warm.textColor = COLOR.warm;

  row.addSpacer(10);

  const right = row.addStack();
  right.layoutVertically();

  const heading = right.addText('ACTIVE ZONES');
  heading.font = Font.boldSystemFont(9);
  heading.textColor = COLOR.muted;
  right.addSpacer(4);

  const zones = data.clusters.slice(0, 3);
  if (zones.length === 0) {
    const quiet = right.addText('Nothing on the wire.');
    quiet.font = Font.systemFont(11);
    quiet.textColor = COLOR.muted;
  }

  for (const zone of zones) {
    const line = right.addStack();
    line.centerAlignContent();

    const dot = line.addText('●');
    dot.font = Font.systemFont(9);
    dot.textColor = HEAT_COLOR[zone.heat];

    line.addSpacer(5);

    const text = line.addText(
      `${zone.count} pin${zone.count === 1 ? '' : 's'} ${zone.where} · ${SpideyCore.formatDistance(zone.away)}`,
    );
    text.font = Font.systemFont(11);
    text.textColor = COLOR.text;
    text.lineLimit = 1;

    right.addSpacer(3);
  }

  if (data.top && data.fade !== null) {
    right.addSpacer(2);
    const fade = right.addText(
      `hottest ${data.top.heat === 'hot' ? 'cools' : 'goes cold'} in ${minutes(data.fade)}`,
    );
    fade.font = Font.systemFont(9);
    fade.textColor = COLOR.muted;
  }
}

/**
 * Lock screen accessory widgets are monochrome and tiny. Inline in particular
 * renders a single line of text, so it gets one string rather than a stack.
 */
function buildAccessory(widget, data, inline) {
  const summary = `${data.counts.hot} hot · ${data.counts.warm} warm`;

  if (inline) {
    widget.addText(`🕸 ${summary}`);
    return;
  }

  const line = widget.addStack();
  line.centerAlignContent();
  const mark = line.addText('🕸');
  mark.font = Font.systemFont(12);
  line.addSpacer(4);
  const text = line.addText(summary);
  text.font = Font.boldSystemFont(12);
}

async function build() {
  const home = await resolveLocation();
  const data = summarise(home);

  const widget = new ListWidget();
  widget.url = APP_URL;

  const family = config.widgetFamily ?? 'medium';
  const accessory = family === 'accessoryInline' || family === 'accessoryRectangular';

  if (accessory) {
    // The lock screen paints its own backdrop; anything opaque here looks wrong,
    // and its padding budget is far tighter than a home screen widget's.
    widget.backgroundColor = new Color('#000000', 0);
    widget.setPadding(0, 0, 0, 0);
    buildAccessory(widget, data, family === 'accessoryInline');
  } else {
    widget.backgroundColor = COLOR.bg;
    widget.setPadding(12, 12, 12, 12);
    if (family === 'small') buildSmall(widget, data);
    else buildMedium(widget, data);
  }

  if (home.source !== 'live' && !accessory) {
    widget.addSpacer(2);
    const stale = widget.addText(
      home.source === 'cached' ? 'last known location' : 'location unavailable',
    );
    stale.font = Font.systemFont(8);
    stale.textColor = COLOR.muted;
  }

  // iOS treats this as a hint, not a promise; it typically refreshes near this.
  widget.refreshAfterDate = new Date(Date.now() + 15 * 60 * 1000);
  return widget;
}

const widget = await build();

if (config.runsInWidget) {
  Script.setWidget(widget);
} else {
  // Tapping the script in the app previews it.
  await widget.presentMedium();
}

Script.complete();
