import { useState } from 'react';
import { useStore } from '../store/useStore';
import { TAGS, type SightingTag } from '../types';

export function ReportSheet() {
  const { reporting, setReporting, report, position, locationDenied, activePatrol, mapCentre } =
    useStore();
  const [tag, setTag] = useState<SightingTag>('swinging');
  const [note, setNote] = useState('');

  if (!reporting) return null;

  const submit = () => {
    report(tag, note);
    setNote('');
    setTag('swinging');
  };

  return (
    <div className="sheet-backdrop" onClick={() => setReporting(false)}>
      <div className="sheet" onClick={(e) => e.stopPropagation()}>
        <div className="sheet__grip" />
        <h2 className="sheet__title">What did you see?</h2>

        <div className="tag-grid">
          {TAGS.map((t) => (
            <button
              key={t.id}
              className={`tag ${t.id === tag ? 'tag--on' : ''}`}
              onClick={() => setTag(t.id)}
            >
              <span className="tag__icon">{t.icon}</span>
              <span className="tag__label">{t.label}</span>
            </button>
          ))}
        </div>

        <textarea
          className="note"
          placeholder="Add a detail (optional)"
          value={note}
          maxLength={180}
          onChange={(e) => setNote(e.target.value)}
        />

        <p className="sheet__meta">
          {position
            ? `Pinned at your location${activePatrol ? ' · on patrol, counts for more' : ''}`
            : locationDenied
              ? `No location permission — this will pin at the ${mapCentre ? 'map centre' : 'default location'}`
              : 'Finding your location…'}
        </p>

        <div className="sheet__actions">
          <button className="btn btn--ghost" onClick={() => setReporting(false)}>
            Cancel
          </button>
          <button className="btn btn--primary" onClick={submit}>
            Drop pin
          </button>
        </div>
      </div>
    </div>
  );
}
