// Variables used by Scriptable.
// These must be at the very top of the file. Do not edit.
// icon-color: red; icon-glyph: spider;

// ---------------------------------------------------------------------------
// GENERATED — do not edit. Built from src/lib by scripts/build-widget.mjs.
// Edit widgets/ios/shell.js and run: npm run build:widget
// ---------------------------------------------------------------------------
var SpideyCore = (() => {
  var __defProp = Object.defineProperty;
  var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
  var __getOwnPropNames = Object.getOwnPropertyNames;
  var __hasOwnProp = Object.prototype.hasOwnProperty;
  var __export = (target, all) => {
    for (var name in all)
      __defProp(target, name, { get: all[name], enumerable: true });
  };
  var __copyProps = (to, from, except, desc) => {
    if (from && typeof from === "object" || typeof from === "function") {
      for (let key of __getOwnPropNames(from))
        if (!__hasOwnProp.call(to, key) && key !== except)
          __defProp(to, key, { get: () => from[key], enumerable: !(desc = __getOwnPropDesc(from, key)) || desc.enumerable });
    }
    return to;
  };
  var __toCommonJS = (mod) => __copyProps(__defProp({}, "__esModule", { value: true }), mod);

  // widgets/ios/core-entry.ts
  var core_entry_exports = {};
  __export(core_entry_exports, {
    HEAT_LABEL: () => HEAT_LABEL,
    TAG_BY_ID: () => TAG_BY_ID,
    clusterSightings: () => clusterSightings,
    compassFrom: () => compassFrom,
    confidenceOf: () => confidenceOf,
    distanceM: () => distanceM,
    formatAgo: () => formatAgo,
    formatDistance: () => formatDistance,
    heatOf: () => heatOf,
    msUntilHeat: () => msUntilHeat,
    seedSightings: () => seedSightings
  });

  // src/lib/geo.ts
  var EARTH_RADIUS_M = 6371e3;
  var toRad = (deg) => deg * Math.PI / 180;
  function distanceM(a, b) {
    const dLat = toRad(b.lat - a.lat);
    const dLng = toRad(b.lng - a.lng);
    const lat1 = toRad(a.lat);
    const lat2 = toRad(b.lat);
    const h = Math.sin(dLat / 2) ** 2 + Math.sin(dLng / 2) ** 2 * Math.cos(lat1) * Math.cos(lat2);
    return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(h));
  }
  function offset(origin, metres, bearingDeg) {
    const angular = metres / EARTH_RADIUS_M;
    const bearing = toRad(bearingDeg);
    const lat1 = toRad(origin.lat);
    const lng1 = toRad(origin.lng);
    const lat2 = Math.asin(
      Math.sin(lat1) * Math.cos(angular) + Math.cos(lat1) * Math.sin(angular) * Math.cos(bearing)
    );
    const lng2 = lng1 + Math.atan2(
      Math.sin(bearing) * Math.sin(angular) * Math.cos(lat1),
      Math.cos(angular) - Math.sin(lat1) * Math.sin(lat2)
    );
    return { lat: lat2 * 180 / Math.PI, lng: (lng2 * 180 / Math.PI + 540) % 360 - 180 };
  }
  var COMPASS = ["north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"];
  function compassFrom(from, to) {
    const dLng = toRad(to.lng - from.lng);
    const lat1 = toRad(from.lat);
    const lat2 = toRad(to.lat);
    const y = Math.sin(dLng) * Math.cos(lat2);
    const x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLng);
    const deg = (Math.atan2(y, x) * 180 / Math.PI + 360) % 360;
    return COMPASS[Math.round(deg / 45) % 8];
  }
  function centroid(points) {
    const sum = points.reduce(
      (acc, p) => ({ lat: acc.lat + p.lat, lng: acc.lng + p.lng }),
      { lat: 0, lng: 0 }
    );
    return { lat: sum.lat / points.length, lng: sum.lng / points.length };
  }
  function formatDistance(metres) {
    if (metres < 1e3) return `${Math.round(metres / 10) * 10} m`;
    const km = metres / 1e3;
    return km < 10 ? `${km.toFixed(1)} km` : `${Math.round(km)} km`;
  }
  function formatAgo(timestamp, now = Date.now()) {
    const seconds = Math.max(0, Math.round((now - timestamp) / 1e3));
    if (seconds < 60) return "just now";
    const minutes = Math.round(seconds / 60);
    if (minutes < 60) return `${minutes} min ago`;
    const hours = Math.round(minutes / 60);
    if (hours < 24) return `${hours} hr ago`;
    const days = Math.round(hours / 24);
    return days === 1 ? "yesterday" : `${days} days ago`;
  }

  // src/lib/seed.ts
  var HANDLES = [
    "foresthills_frank",
    "f_train_ghost",
    "bodega_cat_92",
    "nightshiftnurse",
    "chelsea_walkup",
    "delivery_dave",
    "astoria_ana",
    "rooftop_gardener",
    "lateshift_leo",
    "bridge_and_tunnel",
    "midtown_myra",
    "flatiron_finch",
    "yellowcab_yuri",
    "harlem_hana",
    "brooklyn_bram",
    "notjjjameson"
  ];
  var NOTES = [
    "came off the roof of the parking garage and just kept going",
    "heard the thwip before I saw anything",
    "two blocks up, moving north, fast",
    "whole street stopped and looked up",
    "webbing on the streetlight is still there if you want to check",
    "gone before I got my phone out, obviously",
    "nobody round here knows his name. we just know he turns up",
    "landed on the fire escape, waved, left",
    "kid outside the bodega called it before the rest of us looked up",
    ""
  ];
  var TAGS = [
    "swinging",
    "swinging",
    "red-blur",
    "rooftop",
    "stopped-something",
    "acting-strange"
  ];
  function makeRandom(seed) {
    let state = seed >>> 0;
    return () => {
      state = state * 1664525 + 1013904223 >>> 0;
      return state / 4294967296;
    };
  }
  var MINUTE = 6e4;
  var CLUSTERS = [
    { ageMin: 8, count: 5, confirms: 9, radiusM: 220, distanceM: 700 },
    { ageMin: 35, count: 3, confirms: 5, radiusM: 260, distanceM: 1500 },
    { ageMin: 95, count: 4, confirms: 4, radiusM: 300, distanceM: 2400 },
    { ageMin: 180, count: 2, confirms: 3, radiusM: 180, distanceM: 3100 },
    { ageMin: 260, count: 3, confirms: 2, radiusM: 340, distanceM: 1900 }
  ];
  function seedSightings(home, now = Date.now()) {
    const random = makeRandom(Math.floor(now / 864e5) * 7919 + 13);
    const sightings = [];
    CLUSTERS.forEach((cluster, clusterIndex) => {
      const centre = offset(home, cluster.distanceM * (0.7 + random() * 0.6), random() * 360);
      for (let i = 0; i < cluster.count; i++) {
        const at = offset(centre, random() * cluster.radiusM, random() * 360);
        const ageMinutes = Math.max(1, cluster.ageMin + random() * 20 - 10);
        const createdAt = now - ageMinutes * MINUTE;
        const confirmCount = Math.max(0, Math.round(cluster.confirms * (0.5 + random())));
        const denyCount = random() < 0.35 ? Math.round(random() * 2) : 0;
        const makeVote = () => ({
          userId: `seed-${Math.floor(random() * 9999)}`,
          distanceM: random() * 900,
          onPatrol: random() < 0.25,
          // Votes trickle in after the report rather than landing all at once.
          createdAt: Math.min(now, createdAt + random() * (now - createdAt))
        });
        const handle = HANDLES[Math.floor(random() * HANDLES.length)];
        sightings.push({
          id: `seed-${clusterIndex}-${i}`,
          lat: at.lat,
          lng: at.lng,
          createdAt,
          tag: TAGS[Math.floor(random() * TAGS.length)],
          note: NOTES[Math.floor(random() * NOTES.length)] || void 0,
          reporterId: `seed-user-${handle}`,
          reporterHandle: handle,
          reportedOnPatrol: random() < 0.3,
          confirms: Array.from({ length: confirmCount }, makeVote),
          denies: Array.from({ length: denyCount }, makeVote)
        });
      }
    });
    return sightings.sort((a, b) => b.createdAt - a.createdAt);
  }

  // src/types.ts
  var TAGS2 = [
    { id: "swinging", label: "Swinging through", icon: "\u{1F578}\uFE0F", baseWeight: 1 },
    { id: "stopped-something", label: "Stopped a mugging", icon: "\u{1F6A8}", baseWeight: 1.4 },
    { id: "red-blur", label: "Just a red blur", icon: "\u{1F4A8}", baseWeight: 0.6 },
    { id: "rooftop", label: "Rooftop landing", icon: "\u{1F3D9}\uFE0F", baseWeight: 1.1 },
    { id: "webbing", label: "Fresh webbing", icon: "\u{1F9F5}", baseWeight: 0.9 },
    { id: "acting-strange", label: "People acting strange", icon: "\u{1F300}", baseWeight: 1.2 },
    { id: "heavy", label: "Something big fighting back", icon: "\u26A1", baseWeight: 1.3 }
  ];
  var TAG_BY_ID = Object.fromEntries(
    TAGS2.map((t) => [t.id, t])
  );

  // src/lib/confidence.ts
  var HALF_LIFE_MS = 90 * 60 * 1e3;
  var SATURATION = 2.5;
  var DENY_MULTIPLIER = 1.1;
  var HOT_THRESHOLD = 0.66;
  var WARM_THRESHOLD = 0.3;
  var decayFrom = (timestamp, now) => Math.pow(0.5, Math.max(0, now - timestamp) / HALF_LIFE_MS);
  var proximityWeight = (distanceM2) => 1 / (1 + Math.max(0, distanceM2) / 400);
  var voteWeight = (vote) => proximityWeight(vote.distanceM) * (vote.onPatrol ? 1.5 : 1);
  function scoreOf(sighting, now = Date.now()) {
    const tag = TAG_BY_ID[sighting.tag];
    const base = (tag?.baseWeight ?? 1) * (sighting.reportedOnPatrol ? 1.3 : 1) * decayFrom(sighting.createdAt, now);
    let score = base;
    for (const vote of sighting.confirms) score += voteWeight(vote) * decayFrom(vote.createdAt, now);
    for (const vote of sighting.denies)
      score -= voteWeight(vote) * DENY_MULTIPLIER * decayFrom(vote.createdAt, now);
    return Math.max(0, score);
  }
  function confidenceOf(sighting, now = Date.now()) {
    const score = scoreOf(sighting, now);
    return score / (score + SATURATION);
  }
  function heatFromConfidence(confidence) {
    if (confidence >= HOT_THRESHOLD) return "hot";
    if (confidence >= WARM_THRESHOLD) return "warm";
    return "cold";
  }
  function heatOf(sighting, now = Date.now()) {
    return heatFromConfidence(confidenceOf(sighting, now));
  }
  var scoreForConfidence = (confidence) => SATURATION * confidence / (1 - confidence);
  function msUntilHeat(sighting, target, now = Date.now()) {
    const score = scoreOf(sighting, now);
    const targetScore = scoreForConfidence(target === "cold" ? WARM_THRESHOLD : HOT_THRESHOLD);
    if (score <= targetScore) return null;
    return Math.log2(score / targetScore) * HALF_LIFE_MS | 0;
  }
  var HEAT_LABEL = {
    hot: "HOT",
    warm: "WARM",
    cold: "COLD"
  };

  // src/lib/bugle.ts
  var CLUSTER_RADIUS_M = 600;
  var CLUSTER_WINDOW_MS = 75 * 60 * 1e3;
  function clusterSightings(sightings, now = Date.now()) {
    const remaining = [...sightings].sort((a, b) => b.createdAt - a.createdAt);
    const clusters = [];
    while (remaining.length > 0) {
      const head = remaining.shift();
      const members = [head];
      for (let i = remaining.length - 1; i >= 0; i--) {
        const candidate = remaining[i];
        const closeInSpace = distanceM(head, candidate) <= CLUSTER_RADIUS_M;
        const closeInTime = Math.abs(head.createdAt - candidate.createdAt) <= CLUSTER_WINDOW_MS;
        if (closeInSpace && closeInTime) {
          members.push(candidate);
          remaining.splice(i, 1);
        }
      }
      clusters.push({
        sightings: members,
        centre: centroid(members),
        latestAt: Math.max(...members.map((s) => s.createdAt)),
        // A cluster is as strong as its strongest pin, not the average.
        confidence: Math.max(...members.map((s) => confidenceOf(s, now)))
      });
    }
    return clusters.sort((a, b) => b.latestAt - a.latestAt);
  }
  return __toCommonJS(core_entry_exports);
})();



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

const COLOR = {
  bg: new Color('#07070c'),
  card: new Color('#12131d'),
  red: new Color('#e8354a'),
  hot: new Color('#ff5c3c'),
  warm: new Color('#ffb03a'),
  cold: new Color('#6a7290'),
  text: new Color('#eef0f8'),
  muted: new Color('#8d93ad'),
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
  const title = row.addText('SPIDEY');
  title.font = Font.boldSystemFont(10);
  title.textColor = COLOR.red;

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
