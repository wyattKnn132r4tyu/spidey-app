import { useEffect, useState } from 'react';
import { useStore } from '../store/useStore';
import { formatDistance } from '../lib/geo';
import { formatDuration, rankFor, totalDistance } from '../lib/rank';

/** How long the reset button stays armed before it forgets it was pressed. */
const ARMED_MS = 5_000;

export function PatrolPanel() {
  const {
    patrols,
    activePatrol,
    startPatrol,
    stopPatrol,
    profile,
    locationDenied,
    senseOn,
    toggleSense,
    clock,
    reset,
  } = useStore();

  const total = totalDistance(patrols) + (activePatrol?.distanceM ?? 0);
  const { current, next, progress } = rankFor(total);
  const filled = Math.round(progress * 20);

  // Reset throws away every pin, patrol and streak the user has, and it sits one
  // tap away at the bottom of a panel people scroll through. It asks first.
  const [armed, setArmed] = useState(false);
  useEffect(() => {
    if (!armed) return;
    const timer = window.setTimeout(() => setArmed(false), ARMED_MS);
    return () => window.clearTimeout(timer);
  }, [armed]);

  return (
    <div className="panel">
      <p style={{ margin: 0, fontSize: 8 }}>@{profile.handle}</p>
      <p className="rank__line" style={{ marginBottom: 14 }}>FIELD RECORD</p>

      <section className="rank">
        <p className="rank__title">{current.title.toUpperCase()}</p>
        <div className="meter">
          {Array.from({ length: 20 }, (_, i) => (
            <span key={i} className={i < filled ? 'on' : ''} />
          ))}
        </div>
        <p className="rank__line">{formatDistance(total).toUpperCase()} COVERED</p>
        {next && <p className="rank__line">NEXT: {next.title.toUpperCase()}</p>}
        <p className="rank__line">
          {profile.streakDays} DAY STREAK · {patrols.length} LOGGED
        </p>
      </section>

      {locationDenied && (
        <p className="warn">
          LOCATION OFF. ROUTES WILL NOT RECORD.
          <br />
          PATROLS TRACK WHILE THE APP IS ON SCREEN.
        </p>
      )}

      <section className="rank" style={{ marginTop: 14 }}>
        <p className="rank__title">SPIDEY-SENSE</p>
        <p className="rank__line" style={{ marginTop: 8 }}>
          ALERTS WHEN A HOT SIGHTING IS WITHIN 400M.
          <br />
          ONLY WHILE THE APP IS ON SCREEN.
        </p>
        <div style={{ display: 'flex', marginTop: 10 }}>
          <button
            className={`btn-panel ${senseOn ? 'btn-panel--yes' : ''}`}
            onClick={toggleSense}
            aria-pressed={senseOn}
          >
            {senseOn ? 'ON' : 'OFF'}
          </button>
        </div>
      </section>

      <div style={{ display: 'flex', marginTop: 14 }}>
        <button className="btn-amber" onClick={() => (activePatrol ? stopPatrol() : startPatrol())}>
          {activePatrol
            ? `END PATROL · ${formatDistance(activePatrol.distanceM).toUpperCase()}`
            : 'START PATROL'}
        </button>
      </div>

      <p className="section">HISTORY</p>

      {patrols.length === 0 && (
        <p className="empty">
          NO PATROLS YET.
          <br />
          THE CITY IS NOT GOING TO WATCH ITSELF.
        </p>
      )}

      {patrols.map((patrol) => (
        <div key={patrol.id} className="row">
          <div>
            <strong>{formatDistance(patrol.distanceM).toUpperCase()}</strong>
            <p>
              {new Date(patrol.startedAt).toLocaleDateString()} ·{' '}
              {formatDuration((patrol.endedAt ?? clock) - patrol.startedAt).toUpperCase()}
            </p>
          </div>
          <span>{patrol.sightingIds.length} LOGGED</span>
        </div>
      ))}

      <div style={{ display: 'flex', marginTop: 24 }}>
        <button
          className="btn-panel btn-panel--danger"
          onClick={() => {
            if (!armed) return setArmed(true);
            setArmed(false);
            reset();
          }}
        >
          {armed ? 'TAP AGAIN TO WIPE EVERYTHING' : 'RESET LOCAL DATA'}
        </button>
      </div>
    </div>
  );
}
