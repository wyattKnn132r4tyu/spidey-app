/**
 * Vibration, by whatever route the platform allows.
 *
 * Safari has no Vibration API, so on the installed web app spidey-sense is a
 * banner and a blip. The native iOS build carries Capacitor's Haptics plugin,
 * which reaches the Taptic Engine — so the same code buzzes there. Android's
 * browser supports navigator.vibrate directly.
 */

type HapticsPlugin = {
  impact: (options: { style: string }) => Promise<void>;
  vibrate: (options: { duration: number }) => Promise<void>;
};

type CapacitorGlobal = {
  isNativePlatform?: () => boolean;
  Plugins?: { Haptics?: HapticsPlugin };
};

const capacitor = (): CapacitorGlobal | undefined =>
  (globalThis as unknown as { Capacitor?: CapacitorGlobal }).Capacitor;

/** True inside the native shell, false in a browser or an installed web app. */
export function isNative(): boolean {
  return Boolean(capacitor()?.isNativePlatform?.());
}

/** The spidey-sense tingle: two short pulses, not an alarm. */
export function tingle(): void {
  const haptics = capacitor()?.Plugins?.Haptics;

  if (haptics) {
    // Two taps, spaced, rather than one long buzz.
    void haptics.impact({ style: 'MEDIUM' }).catch(() => {});
    setTimeout(() => void haptics.impact({ style: 'MEDIUM' }).catch(() => {}), 150);
    return;
  }

  if (typeof navigator !== 'undefined' && typeof navigator.vibrate === 'function') {
    navigator.vibrate([60, 90, 60]);
  }
}
