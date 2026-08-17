import { useEffect, useMemo } from 'react';
import { useStore } from './store/useStore';
import { useTracking } from './hooks/useTracking';
import { MapView } from './components/MapView';
import { ReportSheet } from './components/ReportSheet';
import { SightingDetail } from './components/SightingDetail';
import { BugleFeed } from './components/BugleFeed';
import { PatrolPanel } from './components/PatrolPanel';
import { PatrolBar } from './components/PatrolBar';
import { heatOf } from './lib/confidence';

function HotCount() {
  const { sightings, clock } = useStore();

  // One pass, one heat computation per sighting.
  const { hot, warm } = useMemo(() => {
    let hot = 0;
    let warm = 0;
    for (const sighting of sightings) {
      const heat = heatOf(sighting, clock);
      if (heat === 'hot') hot += 1;
      else if (heat === 'warm') warm += 1;
    }
    return { hot, warm };
  }, [sightings, clock]);

  return (
    <div className="ticker">
      <span className="ticker__hot">{hot} hot</span>
      <span className="ticker__warm">{warm} warm</span>
    </div>
  );
}

export default function App() {
  const { ready, init, tab, setTab, setReporting, showHeat, toggleHeat, activePatrol } = useStore();
  useTracking();

  useEffect(() => {
    void init();
  }, [init]);

  if (!ready) {
    return (
      <div className="boot">
        <div className="boot__web">🕸️</div>
        <p>Finding your corner of the city…</p>
      </div>
    );
  }

  return (
    <div className="app">
      <header className="topbar">
        <h1 className="topbar__title">SPIDEY TRACKER</h1>
        <HotCount />
      </header>

      <main className="stage">
        {/* The map stays mounted so Leaflet never has to re-initialise. */}
        <div className={`stage__map ${tab === 'map' ? '' : 'stage__map--hidden'}`}>
          <MapView />
          <PatrolBar />
          <SightingDetail />

          <div className="map-controls">
            <button
              className={`chip ${showHeat ? 'chip--on' : ''}`}
              onClick={toggleHeat}
            >
              Heat
            </button>
            {!activePatrol && (
              <button className="chip" onClick={() => useStore.getState().startPatrol()}>
                Patrol
              </button>
            )}
          </div>

          <button className="fab" onClick={() => setReporting(true)}>
            Report
          </button>
        </div>

        {tab === 'bugle' && <BugleFeed />}
        {tab === 'patrol' && <PatrolPanel />}
      </main>

      <nav className="tabbar">
        {(['map', 'bugle', 'patrol'] as const).map((id) => (
          <button
            key={id}
            className={`tabbar__btn ${tab === id ? 'tabbar__btn--on' : ''}`}
            onClick={() => setTab(id)}
          >
            {id === 'map' ? 'Map' : id === 'bugle' ? 'Bugle' : 'Patrol'}
          </button>
        ))}
      </nav>

      <ReportSheet />
    </div>
  );
}
