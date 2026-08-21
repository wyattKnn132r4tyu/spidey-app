import { useEffect } from 'react';
import { useStore } from '../store/useStore';

/** GeolocationPositionError.PERMISSION_DENIED, without needing the global. */
const PERMISSION_DENIED = 1;

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

    let id: number | null = null;

    const start = () => {
      if (id !== null) return;
      id = navigator.geolocation.watchPosition(
        (pos) => {
          const at = { lat: pos.coords.latitude, lng: pos.coords.longitude };
          useStore.getState().setPosition(at, pos.coords.accuracy);
          useStore.getState().runSense(at);
        },
        (error) => {
          // Only a refusal means refused. A timeout or a temporarily unavailable
          // fix is not permission, and telling the user their location is off when
          // it isn't sends them to Settings for nothing.
          if (error.code === PERMISSION_DENIED) useStore.setState({ locationDenied: true });
        },
        { enableHighAccuracy: patrolling, maximumAge: patrolling ? 5_000 : 30_000, timeout: 20_000 },
      );
    };

    const stop = () => {
      if (id === null) return;
      navigator.geolocation.clearWatch(id);
      id = null;
    };

    // Foreground-only, and not just on iOS. Android keeps a backgrounded watch
    // alive, so leaving it running drains the battery and quietly banks distance
    // as patrol progress the user did not walk while looking at the app.
    const onVisibilityChange = () => (document.hidden ? stop() : start());

    if (!document.hidden) start();
    document.addEventListener('visibilitychange', onVisibilityChange);

    return () => {
      stop();
      document.removeEventListener('visibilitychange', onVisibilityChange);
    };
  }, [patrolling]);

  // Drives live decay: heat badges, countdowns and the heat layer all read `clock`.
  // Paused while the app is hidden — nobody is reading a backgrounded widget-less
  // tab, and a timer that keeps firing there costs battery for nothing.
  useEffect(() => {
    let interval = 0;

    const start = () => {
      if (interval) return;
      interval = window.setInterval(() => useStore.getState().tick(), 15_000);
    };

    const stop = () => {
      window.clearInterval(interval);
      interval = 0;
    };

    const onVisibilityChange = () => {
      if (document.hidden) {
        stop();
      } else {
        // Catch up immediately: everything on screen decayed while we were away.
        useStore.getState().tick();
        start();
      }
    };

    if (!document.hidden) start();
    document.addEventListener('visibilitychange', onVisibilityChange);

    return () => {
      stop();
      document.removeEventListener('visibilitychange', onVisibilityChange);
    };
  }, []);
}
