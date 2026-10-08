/** Maximum amplitude per time interval, across every audio channel. */
export function recordingPeaks(
  channels: readonly Float32Array[],
  bars = 64,
): number[] {
  const length = channels[0]?.length ?? 0;
  if (!length || bars <= 0) return [];
  const peaks = Array.from({ length: bars }, (_, index) => {
    const start = Math.floor((index * length) / bars);
    const end = Math.min(
      length,
      Math.max(start + 1, Math.floor(((index + 1) * length) / bars)),
    );
    let peak = 0;
    for (const channel of channels) {
      for (let sample = start; sample < end; sample++)
        peak = Math.max(peak, Math.abs(channel[sample] ?? 0));
    }
    return peak;
  });
  const maximum = Math.max(...peaks);
  return maximum > 0 ? peaks.map((peak) => peak / maximum) : peaks;
}
