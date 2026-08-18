/**
 * Square-wave blips, synthesised in the browser.
 *
 * Same tones as the Android build, generated rather than shipped, so no audio
 * files ride along in the bundle.
 *
 * iOS will not let audio start without a user gesture, so the context is created
 * lazily on the first blip — which always follows a tap — and resumed if Safari
 * has suspended it in the background.
 */

export type BlipName = 'tap' | 'drop' | 'confirm' | 'deny' | 'sense';

const TONES: Record<BlipName, { hz: number; ms: number }> = {
  tap: { hz: 880, ms: 45 },
  drop: { hz: 1320, ms: 90 },
  confirm: { hz: 1760, ms: 70 },
  deny: { hz: 220, ms: 110 },
  sense: { hz: 1046, ms: 160 },
};

type AudioContextCtor = typeof AudioContext;

let context: AudioContext | null = null;

function audio(): AudioContext | null {
  if (typeof window === 'undefined') return null;

  const Ctor: AudioContextCtor | undefined =
    window.AudioContext ?? (window as unknown as { webkitAudioContext?: AudioContextCtor }).webkitAudioContext;
  if (!Ctor) return null;

  if (!context) context = new Ctor();
  // Safari suspends the context when the tab loses focus.
  if (context.state === 'suspended') void context.resume();
  return context;
}

export function blip(name: BlipName): void {
  try {
    const ctx = audio();
    if (!ctx) return;

    const { hz, ms } = TONES[name];
    const oscillator = ctx.createOscillator();
    const gain = ctx.createGain();

    oscillator.type = 'square';
    oscillator.frequency.value = hz;

    // Ramp down rather than cutting, so it stops without a click.
    gain.gain.setValueAtTime(0.16, ctx.currentTime);
    gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + ms / 1000);

    oscillator.connect(gain).connect(ctx.destination);
    oscillator.start();
    oscillator.stop(ctx.currentTime + ms / 1000);
  } catch {
    // Audio is a nicety; never let it break an interaction.
  }
}

/** Shrinks a captured photo so it fits comfortably in localStorage. */
export function downscale(file: File, maxEdge = 320, quality = 0.7): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(new Error('could not read the photo'));
    reader.onload = () => {
      const image = new Image();
      image.onerror = () => reject(new Error('could not decode the photo'));
      image.onload = () => {
        const scale = Math.min(1, maxEdge / Math.max(image.width, image.height));
        const canvas = document.createElement('canvas');
        canvas.width = Math.round(image.width * scale);
        canvas.height = Math.round(image.height * scale);

        const ctx = canvas.getContext('2d');
        if (!ctx) return reject(new Error('no canvas context'));
        ctx.drawImage(image, 0, 0, canvas.width, canvas.height);
        resolve(canvas.toDataURL('image/jpeg', quality));
      };
      image.src = String(reader.result);
    };
    reader.readAsDataURL(file);
  });
}
