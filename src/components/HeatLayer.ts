import L from 'leaflet';

export interface HeatPoint {
  lat: number;
  lng: number;
  /** 0..1 */
  intensity: number;
}

/**
 * Minimal canvas heat layer.
 *
 * Leaflet has no heatmap of its own and the usual plugin is one more dependency
 * for ~50 lines of canvas, so this draws the glow directly: one radial gradient
 * per point, blended additively so overlapping sightings build into a hot zone.
 */
export class HeatLayer extends L.Layer {
  private canvas?: HTMLCanvasElement;
  private points: HeatPoint[];
  private radiusPx: number;

  constructor(points: HeatPoint[] = [], radiusPx = 55) {
    super();
    this.points = points;
    this.radiusPx = radiusPx;
  }

  setPoints(points: HeatPoint[]) {
    this.points = points;
    this.draw();
    return this;
  }

  onAdd(map: L.Map): this {
    const canvas = L.DomUtil.create('canvas', 'spidey-heat leaflet-zoom-hide');
    canvas.style.pointerEvents = 'none';
    this.canvas = canvas;

    map.getPanes().overlayPane.appendChild(canvas);
    map.on('moveend zoomend resize', this.reset, this);
    this.reset();
    return this;
  }

  onRemove(map: L.Map): this {
    if (this.canvas) map.getPanes().overlayPane.removeChild(this.canvas);
    map.off('moveend zoomend resize', this.reset, this);
    this.canvas = undefined;
    return this;
  }

  private reset() {
    const map = this._map;
    if (!map || !this.canvas) return;

    const size = map.getSize();
    const ratio = window.devicePixelRatio || 1;

    this.canvas.width = size.x * ratio;
    this.canvas.height = size.y * ratio;
    this.canvas.style.width = `${size.x}px`;
    this.canvas.style.height = `${size.y}px`;

    // Counteract the pane's transform so the canvas sits over the viewport.
    L.DomUtil.setPosition(this.canvas, map.containerPointToLayerPoint([0, 0]));
    this.draw();
  }

  private draw() {
    const map = this._map;
    if (!map || !this.canvas) return;

    const ctx = this.canvas.getContext('2d');
    if (!ctx) return;

    const ratio = window.devicePixelRatio || 1;
    ctx.setTransform(ratio, 0, 0, ratio, 0, 0);
    ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
    ctx.globalCompositeOperation = 'lighter';

    // Zoomed-out views should not turn the whole city into one blob.
    const radius = this.radiusPx * Math.max(0.45, Math.min(1.6, map.getZoom() / 14));

    for (const point of this.points) {
      const { x, y } = map.latLngToContainerPoint([point.lat, point.lng]);
      const alpha = 0.12 + point.intensity * 0.5;

      const gradient = ctx.createRadialGradient(x, y, 0, x, y, radius);
      gradient.addColorStop(0, `rgba(255, 92, 60, ${alpha})`);
      gradient.addColorStop(0.45, `rgba(220, 40, 90, ${alpha * 0.5})`);
      gradient.addColorStop(1, 'rgba(120, 20, 90, 0)');

      ctx.fillStyle = gradient;
      ctx.beginPath();
      ctx.arc(x, y, radius, 0, Math.PI * 2);
      ctx.fill();
    }

    ctx.globalCompositeOperation = 'source-over';
  }
}
