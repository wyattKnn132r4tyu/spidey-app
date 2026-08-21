/**
 * Entry point bundled into the Scriptable widget by `npm run build:widget`.
 *
 * The widget must be a single self-contained file on the phone, but the seeding
 * and confidence maths have to stay identical to the app or the two disagree
 * about what is hot. Bundling the real modules keeps one source of truth
 * instead of a hand-copy that drifts.
 */
export { seedSightings } from '../../src/lib/seed';
export { confidenceOf, heatOf, heatFromConfidence, msUntilHeat, HEAT_LABEL } from '../../src/lib/confidence';
export { distanceM, compassFrom, formatDistance, formatAgo } from '../../src/lib/geo';
export { clusterSightings } from '../../src/lib/bugle';
export { TAG_BY_ID } from '../../src/types';
