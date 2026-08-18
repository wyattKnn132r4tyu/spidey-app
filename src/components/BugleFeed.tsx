import { useMemo } from 'react';
import { useStore } from '../store/useStore';
import { buildBugle } from '../lib/bugle';
import { isLive } from '../lib/confidence';
import { formatAgo } from '../lib/geo';

export function BugleFeed() {
  const { sightings, home, clock, select, setTab } = useStore();

  // Dead pins are not news, and they are not on the map either.
  const stories = useMemo(
    () => buildBugle(sightings.filter((s) => isLive(s, clock)), home, clock),
    [sightings, home, clock],
  );

  return (
    <div className="panel">
      <header className="masthead">
        <h1>THE DAILY BUGLE</h1>
        <p>LATE CITY EDITION</p>
        <p>{new Date(clock).toLocaleDateString()}</p>
      </header>

      {stories.length === 0 && <p className="empty">SLOW NEWS DAY.<br />NOTHING ON THE WIRE.</p>}

      {stories.map((story) => (
        <button
          key={story.id}
          className={`story story--${story.tone}`}
          onClick={() => {
            select(story.sightingIds[0]);
            setTab('map');
          }}
        >
          <h2>{story.headline}</h2>
          <p>{story.standfirst.toUpperCase()}</p>
          <p className="story__filed">FILED {formatAgo(story.at, clock).toUpperCase()}</p>
        </button>
      ))}

      <p className="empty" style={{ textAlign: 'center', marginTop: 24 }}>
        FOUR YEARS AND THE CITY STILL CANNOT NAME HIM.
        <br />
        IT CAN ONLY SAY WHERE HE WAS.
      </p>
    </div>
  );
}
