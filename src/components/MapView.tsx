import { useEffect, useMemo, useRef } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { useStore } from '../store/useStore';
import { heatOf } from '../lib/confidence';
import { pinSvg } from './pinBadge';
import type { Sighting } from '../types';

const BADGE: Record<string, { fill: string; outline: string }> = {
  hot: { fill: '#e23b3b', outline: '#6b1414' },
  warm: { fill: '#6abe4f', outline: '#1e4620' },
  cold: { fill: '#c9d4dc', outline: '#4a5a66' },
  mine: { fill: '#4ea9e8', outline: '#123a5c' },
};

export function MapView({ sightings }: { sightings: Sighting[] }) {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<L.Map>(null);
  const pinsRef = useRef<L.LayerGroup>(null);
  const routeRef = useRef<L.Polyline>(null);
  const meRef = useRef<L.CircleMarker>(null);
  const centredRef = useRef(false);

  const { position, activePatrol, selectedId, clock, recenterAt, select, setMapCentre, recenterHandled } =
    useStore();

  /**
   * Markers are only rebuilt when something visible changed — the set of pins,
   * a heat band, or the selection. The clock ticks every 15 seconds and tearing
   * the layer down that often cancels gestures mid-tap.
   */
  const signature = useMemo(
    () => `${sightings.map((s) => `${s.id}:${heatOf(s, clock)}`).join('|')}#${selectedId ?? ''}`,
    [sightings, clock, selectedId],
  );

  useEffect(() => {
    if (!containerRef.current || mapRef.current) return;
    const home = useStore.getState().home;

    const map = L.map(containerRef.current, {
      center: [home.lat, home.lng],
      zoom: 14,
      zoomControl: false,
      // Bottom-left, so it does not sit under the web compass.
      attributionControl: false,
    });

    L.control.attribution({ position: 'bottomleft', prefix: false }).addTo(map);

    L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
      maxZoom: 19,
      attribution: '&copy; OpenStreetMap &copy; CARTO',
    }).addTo(map);

    const publishCentre = () => {
      const centre = map.getCenter();
      useStore.getState().setMapCentre({ lat: centre.lat, lng: centre.lng });
    };
    map.on('moveend', publishCentre);
    publishCentre();

    mapRef.current = map;
    pinsRef.current = L.layerGroup().addTo(map);

    return () => {
      map.off('moveend', publishCentre);
      map.remove();
      mapRef.current = null;
      pinsRef.current = null;
      routeRef.current = null;
      meRef.current = null;
    };
  }, [setMapCentre]);

  // The map lives behind the other panels, so it can miss a resize while hidden.
  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;
    const observer = new ResizeObserver(() => map.invalidateSize());
    if (containerRef.current) observer.observe(containerRef.current);
    return () => observer.disconnect();
  }, []);

  // Recentre on the first real fix only; after that the view is the user's.
  useEffect(() => {
    if (!mapRef.current || !position || centredRef.current) return;
    centredRef.current = true;
    mapRef.current.setView([position.lat, position.lng], 15);
  }, [position]);

  useEffect(() => {
    if (!mapRef.current || !recenterAt) return;
    mapRef.current.flyTo([recenterAt.lat, recenterAt.lng], 16, { duration: 0.6 });
    recenterHandled();
  }, [recenterAt, recenterHandled]);

  useEffect(() => {
    const pins = pinsRef.current;
    if (!pins) return;
    pins.clearLayers();

    for (const sighting of sightings) {
      const heat = heatOf(sighting, clock);
      const mine = !sighting.id.startsWith('seed-');
      const { fill, outline } = BADGE[mine ? 'mine' : heat];
      const selected = sighting.id === selectedId;

      const icon = L.divIcon({
        className: '',
        html: `<div class="pin ${selected ? 'pin--selected' : ''}">${pinSvg(fill, outline, mine ? 'star' : 'spider')}</div>`,
        iconSize: [28, 28],
        iconAnchor: [14, 14],
      });

      // Stacked by importance so the hot pin wins the tap in a dense cluster.
      const zIndexOffset = (selected ? 3000 : 0) + { hot: 2000, warm: 1000, cold: 0 }[heat];

      L.marker([sighting.lat, sighting.lng], { icon, zIndexOffset, riseOnHover: true })
        .on('click', () => select(sighting.id))
        .addTo(pins);
    }
    // The signature decides when a redraw is needed.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [signature, select]);

  // Patrol route: moved rather than recreated, since it changes on every fix.
  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;

    const points = activePatrol?.route.map((p) => [p.lat, p.lng] as L.LatLngTuple) ?? [];
    if (points.length < 2) {
      routeRef.current?.remove();
      routeRef.current = null;
      return;
    }

    if (routeRef.current) routeRef.current.setLatLngs(points);
    else {
      routeRef.current = L.polyline(points, { color: '#4ea9e8', weight: 5, opacity: 0.95 }).addTo(map);
    }
  }, [activePatrol]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !position) return;

    if (meRef.current) {
      meRef.current.setLatLng([position.lat, position.lng]);
      return;
    }
    meRef.current = L.circleMarker([position.lat, position.lng], {
      radius: 6,
      color: '#f2faff',
      weight: 2,
      fillColor: '#4ea9e8',
      fillOpacity: 1,
    }).addTo(map);
  }, [position]);

  return <div className="map" ref={containerRef} />;
}
