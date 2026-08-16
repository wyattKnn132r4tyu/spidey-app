import { useMemo } from 'react';
import { useStore } from '../store/useStore';
import { buildBugle } from '../lib/bugle';
import { formatAgo } from '../lib/geo';

export function BugleFeed() {
  const { sightings, home, clock, select, setTab } = useStore();
  const stories = useMemo(() => buildBugle(sightings, home, clock), [sightings, home, clock]);

  return (
    <div className="panel">
      <header className="bugle__masthead">
        <h1>THE DAILY BUGLE</h1>
        <p>Late city edition · {new Date(clock).toLocaleDateString()}</p>
      </header>

      {stories.length === 0 && <p className="empty">Slow news day. Nothing on the wire.</p>}

      {stories.map((story) => (
        <article
          key={story.id}
          className={`story story--${story.tone}`}
          onClick={() => {
            select(story.sightingIds[0]);
            setTab('map');
          }}
        >
          <h2 className="story__headline">{story.headline}</h2>
          <p className="story__standfirst">{story.standfirst}</p>
          <p className="story__byline">Filed {formatAgo(story.at, clock)}</p>
        </article>
      ))}
    </div>
  );
}
