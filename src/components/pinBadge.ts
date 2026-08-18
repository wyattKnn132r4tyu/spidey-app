/**
 * The pin badge, built as an SVG string because Leaflet's divIcon takes markup
 * rather than a React node. Same pixel disc the Android overlay draws.
 */

const SPIDER = [
  'X.....X',
  '.X.X.X.',
  '..XXX..',
  'XXXXXXX',
  '..XXX..',
  '.X.X.X.',
  'X.....X',
];

const STAR = [
  '...X...',
  '...X...',
  'XXXXXXX',
  '.XXXXX.',
  '..XXX..',
  '.XX.XX.',
  'X.....X',
];

/** The badge drawn at each pin: a pixel disc with a sprite stamped in it. */
export function pinSvg(fill: string, outline: string, kind: 'spider' | 'star'): string {
  const widths = [3, 5, 6, 6, 7, 7, 7, 7, 7, 7, 6, 6, 5, 3];
  const grid = kind === 'star' ? STAR : SPIDER;
  const unit = 2;
  const size = 14 * unit;
  const centre = size / 2;

  const disc = widths
    .map((half, row) => {
      const y = row * unit;
      const outer = `<rect x="${centre - half * unit}" y="${y}" width="${half * 2 * unit}" height="${unit}" fill="${outline}"/>`;
      if (row === 0 || row === widths.length - 1) return outer;
      const innerHalf = half - 1;
      const inner = `<rect x="${centre - innerHalf * unit}" y="${y}" width="${innerHalf * 2 * unit}" height="${unit}" fill="${fill}"/>`;
      return outer + inner;
    })
    .join('');

  const spriteCell = unit;
  const originX = centre - (grid[0].length / 2) * spriteCell;
  const originY = centre - (grid.length / 2) * spriteCell;
  const sprite = grid
    .map((row, y) =>
      [...row]
        .map((char, x) =>
          char === '.'
            ? ''
            : `<rect x="${originX + x * spriteCell}" y="${originY + y * spriteCell}" width="${spriteCell}" height="${spriteCell}" fill="${outline}"/>`,
        )
        .join(''),
    )
    .join('');

  return `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 ${size} ${size}" shape-rendering="crispEdges">${disc}${sprite}</svg>`;
}

