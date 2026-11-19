/**
 * 轻量提示音：用 Web Audio 合成两声短音，不依赖额外音频文件。
 * 浏览器要求先有用户交互才能播放，AppShell 会在首次点击/按键时解锁。
 */
let audioContext: AudioContext | null = null;

function getContext(): AudioContext | null {
  if (typeof window === "undefined") return null;
  const Ctor =
    window.AudioContext ||
    (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
  if (!Ctor) return null;
  if (!audioContext) {
    audioContext = new Ctor();
  }
  return audioContext;
}

export function primeNotificationSound(): void {
  const context = getContext();
  if (context && context.state === "suspended") {
    void context.resume().catch(() => undefined);
  }
}

export function playNotificationSound(): void {
  const context = getContext();
  if (!context) return;

  const play = () => {
    const now = context.currentTime;
    const notes = [
      { frequency: 880, start: 0, duration: 0.12 },
      { frequency: 1174.66, start: 0.12, duration: 0.16 }
    ];
    notes.forEach(({ frequency, start, duration }) => {
      const oscillator = context.createOscillator();
      const gain = context.createGain();
      oscillator.type = "sine";
      oscillator.frequency.value = frequency;
      gain.gain.setValueAtTime(0.0001, now + start);
      gain.gain.exponentialRampToValueAtTime(0.16, now + start + 0.02);
      gain.gain.exponentialRampToValueAtTime(0.0001, now + start + duration);
      oscillator.connect(gain);
      gain.connect(context.destination);
      oscillator.start(now + start);
      oscillator.stop(now + start + duration + 0.02);
    });
  };

  if (context.state === "suspended") {
    void context.resume().then(play).catch(() => undefined);
    return;
  }
  play();
}
