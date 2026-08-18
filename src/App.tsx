import { useCallback, useEffect, useMemo, useRef } from 'react';
import { useStore } from './store/useStore';
import { useTracking } from './hooks/useTracking';
import { MapView } from './components/MapView';
import { ReportSheet } from './components/ReportSheet';
import { SightingCard } from './components/SightingCard';
import { BugleFeed } from './components/BugleFeed';
import { PatrolPanel } from './components/PatrolPanel';
import { MaskIcon, PixelSpider, SpeakerIcon, WatcherSprite, WebCompass, Crosshair } from './components/Pixels';
import { heatOf, isLive } from './lib/confidence';
import { downscale } from './lib/sound';
import type { Heat } from './types';

const BANDS: { heat: Heat; className: string }[] = [
  { heat: 'warm', className: '' },
  { heat: 'hot', className: 'tab--hot' },
  { heat: 'cold', className: 'tab--cold' },
];

export default function App() {
  const state = useStore();
  useTracking();

  const fileRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    void state.init();
    // Run once; the store guards its own re-entry.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const live = useMemo(
    () => state.sightings.filter((s) => isLive(s, state.clock)),
    [state.sightings, state.clock],
  );

  const visible = useMemo(
    () => live.filter((s) => !state.hiddenHeats.includes(heatOf(s, state.clock))),
    [live, state.hiddenHeats, state.clock],
  );

  const counts = useMemo(() => {
    const tally: Record<Heat, number> = { hot: 0, warm: 0, cold: 0 };
    for (const sighting of live) tally[heatOf(sighting, state.clock)] += 1;
    return tally;
  }, [live, state.clock]);

  const unexplored = live.filter(
    (s) =>
      !s.confirms.some((v) => v.userId === state.profile.id) &&
      !s.denies.some((v) => v.userId === state.profile.id),
  ).length;

  const onPickPhoto = useCallback(async (file: File | undefined) => {
    if (!file) return;
    try {
      useStore.getState().setPendingPhoto(await downscale(file));
    } catch {
      // A photo that will not decode is not worth interrupting the report for.
    }
  }, []);

  if (!state.ready) {
    return (
      <div className="boot">
        <PixelSpider size={48} body="#e23b3b" legs="#6bb8dc" />
        <p>SCANNING THE CITY</p>
        <p style={{ color: 'var(--muted)' }}>STAND BY</p>
      </div>
    );
  }

  const selected = state.sightings.find((s) => s.id === state.selectedId) ?? null;

  return (
    <div className="app">
      <div className="hardware">
        <button
          className="key-round"
          onClick={() => state.setTab(state.tab === 'patrol' ? 'map' : 'patrol')}
          aria-label="Patrol"
        >
          <span className="key-round__bars">
            <span />
            <span />
            <span />
          </span>
        </button>

        <div className="plate">
          <span>SPIDEY</span>
          <MaskIcon size={15} />
          <span>TRACKER</span>
        </div>

        <button
          className="key-square"
          onClick={() => state.setTab(state.tab === 'bugle' ? 'map' : 'bugle')}
          aria-label="The Bugle"
        >
          <PixelSpider size={20} body="#071734" />
        </button>
      </div>

      <div className="screen">
        <div className="ruler ruler--top" />
        <div className="ruler ruler--bottom" />
        <div className="ruler ruler--left" />
        <div className="ruler ruler--right" />

        <div className="screen__inner">
          <MapView sightings={visible} />

          {state.tab === 'map' && (
            <>
              {state.selectedId === null && (
                <div className="readouts">
                  <div className="counter">
                    <span className="counter__plate">{plate(live.length)}</span>
                    <MaskIcon size={22} />
                    <span className="counter__plate">{plate(unexplored)}</span>
                  </div>
                  {unexplored > 0 && (
                    <div className="callout">
                      {unexplored} UNEXPLORED
                      <br />
                      SIGHTINGS
                    </div>
                  )}
                </div>
              )}

              <SenseBanner />

              {state.position && (
                <button className="recenter" onClick={state.requestRecenter} aria-label="Recenter">
                  <Crosshair size={18} />
                </button>
              )}

              <WebCompass size={92} />
              {selected && <SightingCard sighting={selected} />}
            </>
          )}

          {state.tab === 'bugle' && <BugleFeed />}
          {state.tab === 'patrol' && <PatrolPanel />}
        </div>

        {state.tab === 'map' && (
          <div className="tabs">
            {BANDS.map(({ heat, className }) => {
              const off = state.hiddenHeats.includes(heat);
              return (
                <button
                  key={heat}
                  className={`tab ${className} ${off ? 'tab--off' : ''}`}
                  onClick={() => state.toggleHeatFilter(heat)}
                  aria-label={`${off ? 'Show' : 'Hide'} ${heat} sightings`}
                  aria-pressed={!off}
                >
                  <PixelSpider size={13} body="currentColor" />
                  <span>{counts[heat]}</span>
                </button>
              );
            })}
          </div>
        )}
      </div>

      <div className="sharebar">
        <WatcherSprite size={40} />

        <button className="share" onClick={() => state.setReporting(true)}>
          <span className="share__dot" />
          SHARE YOUR SPIDEY SIGHTING
        </button>

        <button
          className={`key-square ${state.soundOn ? '' : 'key-square--muted'}`}
          onClick={state.toggleSound}
          aria-label={state.soundOn ? 'Mute sound' : 'Unmute sound'}
        >
          <SpeakerIcon size={20} muted={!state.soundOn} />
        </button>
      </div>

      <div className="bottom-keys">
        <button className="btn-amber" onClick={() => state.setTab('bugle')}>
          THE BUGLE
        </button>
        <button
          className="btn-amber"
          onClick={() => (state.activePatrol ? state.stopPatrol() : state.startPatrol())}
        >
          {state.activePatrol ? 'END PATROL' : 'START PATROL'}
        </button>
      </div>

      {/* iOS opens the camera straight from this when `capture` is set. */}
      <input
        ref={fileRef}
        type="file"
        accept="image/*"
        capture="environment"
        hidden
        onChange={(e) => {
          void onPickPhoto(e.target.files?.[0]);
          e.target.value = '';
        }}
      />

      {state.reporting && <ReportSheet onPhoto={() => fileRef.current?.click()} />}
    </div>
  );
}

/** The film's readouts are "00.00"; ours carry real counts in the same shape. */
const plate = (value: number) =>
  `${String(Math.floor(value / 100)).padStart(2, '0')}.${String(value % 100).padStart(2, '0')}`;

function SenseBanner() {
  const { lastSense, sightings, select } = useStore();
  const sighting = sightings.find((s) => s.id === lastSense);
  if (!sighting) return null;

  return (
    <button className="sense" onClick={() => select(sighting.id)}>
      SPIDEY-SENSE · SOMETHING HOT IS CLOSE
    </button>
  );
}
