/**
 * The pixel sprites, drawn as inline SVG from the same grids the Android build
 * uses. Rendering them as `<rect>` per pixel keeps the edges hard at any size
 * and means no image assets ship with the app.
 */

type Grid = { rows: string[]; colors: Record<string, string> };

const SPIDER: Grid = {
  rows: [
    'L.....L',
    '.L.L.L.',
    '..BBB..',
    'LBBBBBL',
    '..BBB..',
    '.L.L.L.',
    'L.....L',
  ],
  colors: {},
};

const MASK: Grid = {
  rows: [
    '.DDDDD.',
    'DRRRRRD',
    'REERREE',
    'REERREE',
    'DRRRRRD',
    '.DRRRD.',
    '..DDD..',
  ],
  colors: { R: '#e23b3b', D: '#6b1414', E: '#f2faff' },
};

const CROSSHAIR: Grid = {
  rows: [
    '...B...',
    '..BBB..',
    '.B...B.',
    'BB.B.BB',
    '.B...B.',
    '..BBB..',
    '...B...',
  ],
  colors: {},
};

function PixelGrid({
  grid,
  size,
  body,
  legs,
  className,
}: {
  grid: Grid;
  size: number;
  body?: string;
  legs?: string;
  className?: string;
}) {
  const cells = grid.rows.length;
  const rects: React.ReactElement[] = [];

  grid.rows.forEach((row, y) => {
    [...row].forEach((char, x) => {
      if (char === '.') return;
      const fill =
        grid.colors[char] ?? (char === 'L' ? (legs ?? body ?? '#071734') : (body ?? '#071734'));
      rects.push(<rect key={`${x}-${y}`} x={x} y={y} width={1.02} height={1.02} fill={fill} />);
    });
  });

  return (
    <svg
      width={size}
      height={size}
      viewBox={`0 0 ${cells} ${cells}`}
      shapeRendering="crispEdges"
      className={className}
      aria-hidden="true"
    >
      {rects}
    </svg>
  );
}

export const PixelSpider = (props: { size: number; body?: string; legs?: string }) => (
  <PixelGrid grid={SPIDER} {...props} />
);

export const MaskIcon = ({ size }: { size: number }) => <PixelGrid grid={MASK} size={size} />;

export const Crosshair = ({ size = 18, body = '#071734' }: { size?: number; body?: string }) => (
  <PixelGrid grid={CROSSHAIR} size={size} body={body} />
);

/** The web compass in the corner: radial threads with angular rings. */
export function WebCompass({ size = 92 }: { size?: number }) {
  const centre = { x: size * 0.46, y: size * 0.54 };
  const radius = size * 0.42;
  const spokes = 8;
  const thread = '#6bb8dc';

  const lines: React.ReactElement[] = [];
  for (let i = 0; i < spokes; i++) {
    const angle = (i * 2 * Math.PI) / spokes;
    lines.push(
      <line
        key={`spoke-${i}`}
        x1={centre.x}
        y1={centre.y}
        x2={centre.x + Math.cos(angle) * radius}
        y2={centre.y + Math.sin(angle) * radius}
        stroke={thread}
        strokeWidth={1.5}
        opacity={0.85}
      />,
    );
  }

  // Straight chords between spokes: that angular look is what reads as a web.
  for (let ring = 1; ring <= 3; ring++) {
    const r = (radius * ring) / 3;
    for (let i = 0; i < spokes; i++) {
      const a = (i * 2 * Math.PI) / spokes;
      const b = ((i + 1) * 2 * Math.PI) / spokes;
      lines.push(
        <line
          key={`ring-${ring}-${i}`}
          x1={centre.x + Math.cos(a) * r}
          y1={centre.y + Math.sin(a) * r}
          x2={centre.x + Math.cos(b) * r}
          y2={centre.y + Math.sin(b) * r}
          stroke={thread}
          strokeWidth={1}
          opacity={0.55}
        />,
      );
    }
  }

  const globe = { x: centre.x + radius * 0.92, y: centre.y - radius * 0.42 };
  const target = { x: centre.x + radius * 0.5, y: centre.y + radius * 0.9 };

  return (
    <svg width={size} height={size} className="compass" aria-hidden="true">
      {lines}
      <circle cx={centre.x} cy={centre.y} r={3.5} fill={thread} />
      <circle cx={globe.x} cy={globe.y} r={8} fill="#071734" stroke={thread} strokeWidth={1.5} />
      <line x1={globe.x - 8} y1={globe.y} x2={globe.x + 8} y2={globe.y} stroke={thread} />
      <line x1={globe.x} y1={globe.y - 8} x2={globe.x} y2={globe.y + 8} stroke={thread} />
      <circle cx={target.x} cy={target.y} r={7} fill="#071734" stroke={thread} strokeWidth={1.5} />
      <circle cx={target.x} cy={target.y} r={2} fill={thread} />
    </svg>
  );
}

/** The masked watcher perched by the share bar. Original character, not a likeness. */
export function WatcherSprite({ size = 40 }: { size?: number }) {
  const rows = [
    '....DDDD....',
    '...DRRRRD...',
    '..DRRRRRRD..',
    '..REERREER..',
    '..DRRRRRRD..',
    '...DRRRRD...',
    '..DRRRRRRD..',
    '.DRRRRRRRRD.',
    '.DR.RRRR.RD.',
    '....NNNN....',
    '...NN..NN...',
    '..DD....DD..',
  ];
  const colors: Record<string, string> = {
    R: '#e23b3b',
    D: '#6b1414',
    N: '#0b1e3b',
    E: '#f2faff',
  };

  return (
    <svg width={size} height={size} viewBox="0 0 12 12" shapeRendering="crispEdges" aria-hidden="true">
      {rows.flatMap((row, y) =>
        [...row].map((char, x) =>
          colors[char] ? (
            <rect key={`${x}-${y}`} x={x} y={y} width={1.02} height={1.02} fill={colors[char]} />
          ) : null,
        ),
      )}
    </svg>
  );
}

/** Speaker glyph for the sound key; crossed out when muted. */
export function SpeakerIcon({ size = 20, muted = false }: { size?: number; muted?: boolean }) {
  return (
    <svg width={size} height={size} viewBox="0 0 9 9" shapeRendering="crispEdges" aria-hidden="true">
      <path d="M0 3 h2 L4 1 v7 L2 6 H0 Z" fill="#071734" />
      {muted ? (
        <>
          <rect x={5.5} y={2.5} width={3} height={0.9} fill="#071734" transform="rotate(45 7 4)" />
          <rect x={5.5} y={2.5} width={3} height={0.9} fill="#071734" transform="rotate(-45 7 4)" />
        </>
      ) : (
        <>
          <rect x={5.2} y={3} width={0.8} height={3} fill="#071734" />
          <rect x={6.6} y={2} width={0.8} height={5} fill="#071734" />
        </>
      )}
    </svg>
  );
}
