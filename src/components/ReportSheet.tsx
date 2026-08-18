import { useState } from 'react';
import { useStore } from '../store/useStore';
import { PixelSpider } from './Pixels';
import { TAGS, type SightingTag } from '../types';

export function ReportSheet({ onPhoto }: { onPhoto: () => void }) {
  const { setReporting, report, position, mapCentre, activePatrol, pendingPhoto, setPendingPhoto } =
    useStore();
  const [tag, setTag] = useState<SightingTag>('swinging');
  const [note, setNote] = useState('');

  const submit = () => {
    report(tag, note);
    setNote('');
    setTag('swinging');
  };

  return (
    <div className="sheet-backdrop" onClick={() => setReporting(false)}>
      <div className="sheet" onClick={(e) => e.stopPropagation()}>
        <div className="sheet__inner">
          <h2>WHAT DID YOU SEE?</h2>

          <div className="tag-grid">
            {TAGS.map((t) => (
              <button
                key={t.id}
                className={`tag ${t.id === tag ? 'tag--on' : ''}`}
                onClick={() => setTag(t.id)}
                aria-pressed={t.id === tag}
              >
                <PixelSpider size={14} body="currentColor" />
                <span>{t.label.toUpperCase()}</span>
              </button>
            ))}
          </div>

          <div className="photo-row">
            {pendingPhoto && <img src={pendingPhoto} alt="Photo to attach" />}
            <button className="btn-panel" onClick={onPhoto}>
              {pendingPhoto ? 'RETAKE' : 'ADD PHOTO'}
            </button>
            {pendingPhoto && (
              <button className="btn-panel btn-panel--danger" onClick={() => setPendingPhoto(null)}>
                DROP
              </button>
            )}
          </div>

          <textarea
            className="note"
            placeholder="ADD A DETAIL (OPTIONAL)"
            value={note}
            maxLength={140}
            rows={3}
            onChange={(e) => setNote(e.target.value)}
          />

          <p className="sheet__meta">
            {position
              ? `PINNED AT YOUR LOCATION${activePatrol ? ' · ON PATROL, COUNTS FOR MORE' : ''}`
              : mapCentre
                ? 'NO LOCATION — PINS AT THE MAP CENTRE'
                : 'NO LOCATION — PINS AT THE DEFAULT SPOT'}
          </p>

          <div className="sheet__actions">
            <button className="btn-panel" onClick={() => setReporting(false)}>
              CANCEL
            </button>
            <button className="btn-amber" onClick={submit}>
              DROP PIN
            </button>
          </div>
        </div>
      </div>
    </div>
  );
}
