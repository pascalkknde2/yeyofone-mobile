export type RingbackTone = { start: () => void; stop: () => void };

export function createRingbackTone(): RingbackTone {
  const context = new AudioContext();
  const gain = context.createGain();
  const first = context.createOscillator();
  const second = context.createOscillator();
  gain.gain.value = 0;
  first.frequency.value = 440;
  second.frequency.value = 480;
  first.connect(gain);
  second.connect(gain);
  gain.connect(context.destination);
  first.start();
  second.start();
  void context.resume();

  let timer: number | undefined;
  let stopped = false;
  const pulse = () => {
    const now = context.currentTime;
    gain.gain.cancelScheduledValues(now);
    gain.gain.setValueAtTime(0, now);
    gain.gain.linearRampToValueAtTime(0.075, now + 0.04);
    gain.gain.setValueAtTime(0.075, now + 1.45);
    gain.gain.linearRampToValueAtTime(0, now + 1.5);
  };
  return {
    start() {
      if (stopped || timer !== undefined) return;
      pulse();
      timer = window.setInterval(pulse, 2000);
    },
    stop() {
      if (stopped) return;
      stopped = true;
      if (timer !== undefined) window.clearInterval(timer);
      timer = undefined;
      const now = context.currentTime;
      gain.gain.cancelScheduledValues(now);
      gain.gain.setTargetAtTime(0, now, 0.025);
      window.setTimeout(() => {
        first.stop();
        second.stop();
        void context.close();
      }, 120);
    },
  };
}
