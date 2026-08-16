import { useStore } from '../store/useStore';
import { HEAT_LABEL, confidenceOf, heatOf, msUntilHeat } from '../lib/confidence';
import { distanceM, formatAgo, formatDistance } from '../lib/geo';
import { TAG_BY_ID } from '../types';

const formatCountdown = (ms: number) => {
  const minutes = Math.round(ms / 60_000);
  if (minutes < 60) return `${minutes} min`;
  const hours = Math.floor(minutes / 60);
  return `${hours} hr ${minutes % 60} min`;
};

export function SightingDetail() {
  const { sightings, selectedId, select, vote, position, home, profile, clock } = useStore();
  const sighting = sightings.find((s) => s.id === selectedId);
  if (!sighting) return null;

  const heat = heatOf(sighting, clock);
  const confidence = confidenceOf(sighting, clock);
  const meta = TAG_BY_ID[sighting.tag];
  const away = distanceM(position ?? home, sighting);

  // Time to the *next* band down is the useful number — telling someone a hot pin
  // goes cold in four hours says nothing about when it stops being worth chasing.
  const fade =
    heat === 'hot'
      ? { label: 'cools in', ms: msUntilHeat(sighting, 'warm', clock) }
      : heat === 'warm'
        ? { label: 'goes cold in', ms: msUntilHeat(sighting, 'cold', clock) }
        : { label: '', ms: null };

  const myVote = sighting.confirms.some((v) => v.userId === profile.id)
    ? 'confirm'
    : sighting.denies.some((v) => v.userId === profile.id)
      ? 'deny'
      : null;

  return (
    <div className="detail">
      <button className="detail__close" onClick={() => select(null)} aria-label="Close">
        ×
      </button>

      <div className="detail__head">
        <span className={`heat heat--${heat}`}>{HEAT_LABEL[heat]}</span>
        <span className="detail__tag">
          {meta?.icon} {meta?.label}
        </span>
      </div>

      {sighting.note && <p className="detail__note">“{sighting.note}”</p>}

      <p className="detail__meta">
        @{sighting.reporterHandle} · {formatAgo(sighting.createdAt, clock)} ·{' '}
        {formatDistance(away)} away
        {sighting.reportedOnPatrol && ' · reported on patrol'}
      </p>

      <div className="confidence">
        <div className={`confidence__bar confidence__bar--${heat}`} style={{ width: `${Math.round(confidence * 100)}%` }} />
      </div>

      <p className="detail__decay">
        {sighting.confirms.length} confirmed · {sighting.denies.length} disputed
        {fade.ms !== null && ` · ${fade.label} ${formatCountdown(fade.ms)}`}
      </p>

      <div className="detail__actions">
        <button
          className={`btn btn--confirm ${myVote === 'confirm' ? 'btn--on' : ''}`}
          onClick={() => vote(sighting.id, 'confirm')}
        >
          I saw it too
        </button>
        <button
          className={`btn btn--deny ${myVote === 'deny' ? 'btn--on' : ''}`}
          onClick={() => vote(sighting.id, 'deny')}
        >
          Nothing here
        </button>
      </div>
    </div>
  );
}
