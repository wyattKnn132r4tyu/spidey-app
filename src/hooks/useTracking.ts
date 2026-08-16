import { useEffect } from 'react';
import { useStore } from '../store/useStore';

/**
 * Position watching and the decay clock.
 *
 * Accuracy is raised only while a patrol is running — a background-quality fix
 * is plenty for "where am I on the map", and high accuracy is expensive.
 *
 * Note: this is foreground-only by design. A PWA cannot keep a location watch
 * alive once the app is backgrounded on iOS, so patrols record while the app is
 * on screen and pause otherwise.
 */
export function useTracking() {
  const patrolling = useStore((s) => Boolean(s.activePatrol));

  useEffect(() => {
    if (!('geolocation' in navigator)) return;

    const id = navigator.geolocation.watchPosition(
      (pos) => useStore.getState().setPosition({ lat: pos.coords.latitude, lng: pos.coords.longitude }),
      () => useStore.setState({ locationDenied: true }),
      { enableHighAccuracy: patrolling, maximumAge: patrolling ? 5_000 : 30_000, timeout: 20_000 },
    );

    return () => navigator.geolocation.clearWatch(id);
  }, [patrolling]);

  // Drives live decay: heat badges, countdowns and the heat layer all read `clock`.
  useEffect(() => {
    const interval = window.setInterval(() => useStore.getState().tick(), 15_000);
    return () => window.clearInterval(interval);
  }, []);
}
