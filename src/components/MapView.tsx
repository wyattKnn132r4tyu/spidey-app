import { useEffect, useRef } from 'react';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import { useStore } from '../store/useStore';
import { confidenceOf, heatOf } from '../lib/confidence';
import { TAG_BY_ID } from '../types';
import { HeatLayer } from './HeatLayer';

export function MapView() {
  const containerRef = useRef<HTMLDivElement>(null);
  const mapRef = useRef<L.Map>(null);
  const pinsRef = useRef<L.LayerGroup>(null);
  const heatRef = useRef<HeatLayer>(null);
  const routeRef = useRef<L.Polyline>(null);
  const meRef = useRef<L.CircleMarker>(null);

  const centredRef = useRef(false);

  const { position, sightings, activePatrol, showHeat, selectedId, clock, select } = useStore();

  // Create the map once. `home` is already resolved by the time this mounts —
  // App renders the boot screen until the store is ready — so it is read from
  // the store rather than taken as a dependency that would rebuild the map.
  useEffect(() => {
    if (!containerRef.current || mapRef.current) return;
    const home = useStore.getState().home;

    const map = L.map(containerRef.current, {
      center: [home.lat, home.lng],
      zoom: 14,
      zoomControl: false,
      attributionControl: true,
    });

    L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
      maxZoom: 19,
      attribution: '&copy; OpenStreetMap &copy; CARTO',
    }).addTo(map);

    L.control.zoom({ position: 'topright' }).addTo(map);

    mapRef.current = map;
    pinsRef.current = L.layerGroup().addTo(map);
    heatRef.current = new HeatLayer();

    return () => {
      map.remove();
      mapRef.current = null;
    };
  }, []);

  // Recentre on the first real fix only — after that the view is the user's.
  useEffect(() => {
    if (!mapRef.current || !position || centredRef.current) return;
    centredRef.current = true;
    mapRef.current.setView([position.lat, position.lng], 14);
  }, [position]);

  // Heat layer on/off and its data.
  useEffect(() => {
    const map = mapRef.current;
    const heat = heatRef.current;
    if (!map || !heat) return;

    if (showHeat && !map.hasLayer(heat)) heat.addTo(map);
    if (!showHeat && map.hasLayer(heat)) map.removeLayer(heat);

    if (showHeat) {
      heat.setPoints(
        sightings.map((s) => ({
          lat: s.lat,
          lng: s.lng,
          intensity: confidenceOf(s, clock),
        })),
      );
    }
  }, [showHeat, sightings, clock]);

  // Pins.
  useEffect(() => {
    const pins = pinsRef.current;
    if (!pins) return;
    pins.clearLayers();

    for (const sighting of sightings) {
      const heat = heatOf(sighting, clock);
      const meta = TAG_BY_ID[sighting.tag];
      const selected = sighting.id === selectedId;

      const icon = L.divIcon({
        className: '',
        html: `<div class="pin pin--${heat}${selected ? ' pin--selected' : ''}">
                 <span class="pin__glyph">${meta?.icon ?? '🕷️'}</span>
               </div>`,
        iconSize: [34, 34],
        iconAnchor: [17, 17],
      });

      L.marker([sighting.lat, sighting.lng], { icon, riseOnHover: true })
        .on('click', () => select(sighting.id))
        .addTo(pins);
    }
  }, [sightings, selectedId, clock, select]);

  // Patrol route.
  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;

    routeRef.current?.remove();
    routeRef.current = null;

    if (activePatrol && activePatrol.route.length > 1) {
      routeRef.current = L.polyline(
        activePatrol.route.map((p) => [p.lat, p.lng] as L.LatLngTuple),
        { className: 'patrol-line', color: '#8be9fd', weight: 3, dashArray: '1 7', opacity: 0.9 },
      ).addTo(map);
    }
  }, [activePatrol]);

  // Where you are. Moved rather than recreated, so it does not flicker on every fix.
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !position) return;

    if (meRef.current) {
      meRef.current.setLatLng([position.lat, position.lng]);
      return;
    }

    meRef.current = L.circleMarker([position.lat, position.lng], {
      radius: 6,
      color: '#8be9fd',
      weight: 2,
      fillColor: '#8be9fd',
      fillOpacity: 0.9,
    }).addTo(map);
  }, [position]);

  return <div className="map" ref={containerRef} />;
}
