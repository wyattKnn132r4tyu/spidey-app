import type { Heat, Sighting, Vote } from '../types';
import { TAG_BY_ID } from '../types';

/**
 * Confidence model.
 *
 * Every contribution to a sighting — the original report and each vote — decays
 * on the same half-life from its own timestamp. Because they share the half-life,
 * the total score is itself a clean exponential going forward in time, which lets
 * us solve exactly for when a pin will go cold (see `msUntilHeat`).
 *
 * Three rules do all the work:
 *   - time decays everything, so the map is never stale
 *   - closer voters count for more, so you cannot confirm a pin from another borough
 *   - people out on patrol count for more, so being there beats tapping from the couch
 */

/** Time for any contribution to lose half its weight. */
export const HALF_LIFE_MS = 90 * 60 * 1000;

/** Saturation constant: how much raw score is needed before confidence approaches 1. */
const SATURATION = 2.5;

/** Denials count slightly harder than confirmations so bad pins sink faster than good ones rise. */
const DENY_MULTIPLIER = 1.1;

const HOT_THRESHOLD = 0.66;
const WARM_THRESHOLD = 0.3;

const decayFrom = (timestamp: number, now: number) =>
  Math.pow(0.5, Math.max(0, now - timestamp) / HALF_LIFE_MS);

/** 1.0 on top of the sighting, ~0.5 at 400 m, ~0.17 at 2 km. */
const proximityWeight = (distanceM: number) => 1 / (1 + Math.max(0, distanceM) / 400);

const voteWeight = (vote: Vote) => proximityWeight(vote.distanceM) * (vote.onPatrol ? 1.5 : 1);

/**
 * Raw score, unbounded and >= 0. Exported mainly so `msUntilHeat` can reason
 * about it; the UI should use `confidenceOf` and `heatOf`.
 */
export function scoreOf(sighting: Sighting, now = Date.now()): number {
  const tag = TAG_BY_ID[sighting.tag];
  const base =
    (tag?.baseWeight ?? 1) *
    (sighting.reportedOnPatrol ? 1.3 : 1) *
    decayFrom(sighting.createdAt, now);

  let score = base;
  for (const vote of sighting.confirms) score += voteWeight(vote) * decayFrom(vote.createdAt, now);
  for (const vote of sighting.denies)
    score -= voteWeight(vote) * DENY_MULTIPLIER * decayFrom(vote.createdAt, now);

  return Math.max(0, score);
}

/** 0..1. Monotonic in score, so it preserves ordering. */
export function confidenceOf(sighting: Sighting, now = Date.now()): number {
  const score = scoreOf(sighting, now);
  return score / (score + SATURATION);
}

export function heatFromConfidence(confidence: number): Heat {
  if (confidence >= HOT_THRESHOLD) return 'hot';
  if (confidence >= WARM_THRESHOLD) return 'warm';
  return 'cold';
}

export function heatOf(sighting: Sighting, now = Date.now()): Heat {
  return heatFromConfidence(confidenceOf(sighting, now));
}

const scoreForConfidence = (confidence: number) => (SATURATION * confidence) / (1 - confidence);

/**
 * How long until this sighting drops to the given heat level, in ms.
 * Returns null if it is already there or below.
 *
 * Valid because every contribution shares one half-life: score(t) collapses to
 * score(now) * 2^(-(t - now) / HALF_LIFE), whatever mix of votes produced it.
 */
export function msUntilHeat(sighting: Sighting, target: Exclude<Heat, 'hot'>, now = Date.now()) {
  const score = scoreOf(sighting, now);
  // Dropping *to* cold means falling under the warm threshold, and vice versa.
  const targetScore = scoreForConfidence(target === 'cold' ? WARM_THRESHOLD : HOT_THRESHOLD);
  if (score <= targetScore) return null;
  return (Math.log2(score / targetScore) * HALF_LIFE_MS) | 0;
}

/** Sightings still worth drawing. Below this they are noise. */
export function isLive(sighting: Sighting, now = Date.now()): boolean {
  return confidenceOf(sighting, now) > 0.05;
}

export const HEAT_LABEL: Record<Heat, string> = {
  hot: 'HOT',
  warm: 'WARM',
  cold: 'COLD',
};
