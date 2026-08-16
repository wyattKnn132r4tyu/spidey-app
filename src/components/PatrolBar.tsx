import { useStore } from '../store/useStore';
import { formatDistance } from '../lib/geo';
import { formatDuration } from '../lib/rank';

/** Floating status strip shown over the map while a patrol is running. */
export function PatrolBar() {
  const { activePatrol, stopPatrol, clock } = useStore();
  if (!activePatrol) return null;

  return (
    <div className="patrol-bar">
      <span className="patrol-bar__dot" />
      <div className="patrol-bar__stats">
        <strong>ON PATROL</strong>
        <span>
          {formatDuration(clock - activePatrol.startedAt)} · {formatDistance(activePatrol.distanceM)}{' '}
          · {activePatrol.sightingIds.length} logged
        </span>
      </div>
      <button className="btn btn--ghost btn--small" onClick={stopPatrol}>
        End
      </button>
    </div>
  );
}
