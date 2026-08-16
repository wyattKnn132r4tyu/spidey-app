import { useStore } from '../store/useStore';
import { formatDistance } from '../lib/geo';
import { formatDuration, rankFor, totalDistance } from '../lib/rank';

export function PatrolPanel() {
  const { patrols, activePatrol, startPatrol, stopPatrol, profile, locationDenied, clock, reset } =
    useStore();

  const total = totalDistance(patrols) + (activePatrol?.distanceM ?? 0);
  const { current, next, progress } = rankFor(total);

  return (
    <div className="panel">
      <header className="panel__head">
        <h1>Patrol</h1>
        <p>@{profile.handle}</p>
      </header>

      <section className="rank">
        <p className="rank__title">{current.title}</p>
        <div className="rank__bar">
          <div className="rank__fill" style={{ width: `${Math.round(progress * 100)}%` }} />
        </div>
        <p className="rank__meta">
          {formatDistance(total)} covered
          {next && ` · ${formatDistance(next.at - total)} to ${next.title}`}
        </p>
        <p className="rank__meta">
          {profile.streakDays} day streak · {patrols.length} patrol
          {patrols.length === 1 ? '' : 's'} logged
        </p>
      </section>

      {locationDenied && (
        <p className="warn">
          Location is off, so routes will not record. Patrols only track while the app is open and
          on screen.
        </p>
      )}

      {activePatrol ? (
        <button className="btn btn--primary btn--block" onClick={stopPatrol}>
          End patrol · {formatDistance(activePatrol.distanceM)}
        </button>
      ) : (
        <button className="btn btn--primary btn--block" onClick={startPatrol}>
          Start patrol
        </button>
      )}

      <h2 className="panel__sub">History</h2>
      {patrols.length === 0 && <p className="empty">No patrols yet. The city is not going to watch itself.</p>}

      <ul className="patrol-list">
        {patrols.map((patrol) => (
          <li key={patrol.id} className="patrol-row">
            <div>
              <strong>{formatDistance(patrol.distanceM)}</strong>
              <span className="patrol-row__meta">
                {new Date(patrol.startedAt).toLocaleDateString()} ·{' '}
                {formatDuration((patrol.endedAt ?? clock) - patrol.startedAt)}
              </span>
            </div>
            <span className="patrol-row__count">{patrol.sightingIds.length} logged</span>
          </li>
        ))}
      </ul>

      <button className="btn btn--ghost btn--block btn--danger" onClick={reset}>
        Reset local data
      </button>
    </div>
  );
}
