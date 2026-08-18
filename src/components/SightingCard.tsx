import { useStore } from '../store/useStore';
import { HEAT_LABEL, confidenceOf, heatOf, msUntilHeat } from '../lib/confidence';
import { distanceM, formatDistance } from '../lib/geo';
import { TAG_BY_ID, type Sighting } from '../types';

const minutes = (ms: number) => {
  const total = Math.round(ms / 60_000);
  return total < 60 ? `${total}M` : `${Math.floor(total / 60)}H ${total % 60}M`;
};

export function SightingCard({ sighting }: { sighting: Sighting }) {
  const { position, home, profile, clock, select, vote } = useStore();

  const heat = heatOf(sighting, clock);
  const meta = TAG_BY_ID[sighting.tag];
  const away = distanceM(position ?? home, sighting);
  const fade = msUntilHeat(sighting, heat === 'hot' ? 'warm' : 'cold', clock);
  const confidence = Math.round(confidenceOf(sighting, clock) * 100);

  const myVote = sighting.confirms.some((v) => v.userId === profile.id)
    ? 'confirm'
    : sighting.denies.some((v) => v.userId === profile.id)
      ? 'deny'
      : null;

  return (
    <div className="card-wrap">
      <div className="card">
        <div className="card__head">
          <span className={`heat heat--${heat}`}>{HEAT_LABEL[heat]}</span>
          <span className="card__title">{meta?.label.toUpperCase()}</span>
          <button className="card__close" onClick={() => select(null)} aria-label="Close">
            X
          </button>
        </div>

        {sighting.photo && (
          <img className="card__photo" src={sighting.photo} alt="Reported sighting" />
        )}

        {sighting.note && <p className="card__line">{sighting.note}</p>}

        <p className="card__line">
          @{sighting.reporterHandle} · {formatDistance(away).toUpperCase()} AWAY · {confidence}%
        </p>

        <p className="card__line">
          {sighting.confirms.length} CONFIRMED · {sighting.denies.length} DISPUTED
          {fade !== null && (heat === 'hot' ? ` · COOLS ${minutes(fade)}` : ` · COLD IN ${minutes(fade)}`)}
        </p>

        <div className="card__actions">
          <button
            className={`btn-panel ${myVote === 'confirm' ? 'btn-panel--yes' : ''}`}
            onClick={() => vote(sighting.id, 'confirm')}
          >
            I SAW IT TOO
          </button>
          <button
            className={`btn-panel ${myVote === 'deny' ? 'btn-panel--no' : ''}`}
            onClick={() => vote(sighting.id, 'deny')}
          >
            NOTHING HERE
          </button>
        </div>
      </div>
    </div>
  );
}
