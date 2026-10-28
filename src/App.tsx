import React, { useMemo, useState } from "react";

const GITHUB = "https://github.com/dedsec-1337/MANHNT";
const RELEASES = "https://github.com/dedsec-1337/MANHNT/releases";
const CHANGELOG = "https://github.com/dedsec-1337/MANHNT/blob/main/CHANGELOG.md";

const FEATURES = [
  {
    tag: "01",
    title: "Timed reveals",
    body: "Runners aren’t live-tracked every second. Every 2–5 minutes, seekers get a frozen snapshot pin until the next ping.",
  },
  {
    tag: "02",
    title: "Code + QR tags",
    body: "Tag is face-to-face proof. Enter the 4-digit code — or flash the QR on the runner’s screen. No “too far” gate.",
  },
  {
    tag: "03",
    title: "BLE nearby assist",
    body: "Optional Bluetooth LE hint when someone’s close. Assist only — never required to catch.",
  },
  {
    tag: "04",
    title: "Jail · stealth · zones",
    body: "Head start, safe zones, shrinking play area, stealth vanish, jailbreak holds. Full outdoor manhunt rules.",
  },
  {
    tag: "05",
    title: "Offline-stable pins",
    body: "Once a reveal lands, the pin freezes locally so network glitches don’t make the map jump.",
  },
  {
    tag: "06",
    title: "Free forever",
    body: "No ads. No subscriptions. No account walls. Apache 2.0 — fork it, ship it, hunt with friends.",
  },
];

const STEPS = [
  "GPS + data on (Bluetooth optional)",
  "Host creates a room · shares 6-letter code",
  "Join · assign Seeker / Runner roles",
  "Head start ends · hunt begins",
  "Auto-ping freezes runner pins on the map",
  "Tag with code or QR · jail / jailbreak if enabled",
  "All caught → seekers · timer ends free → runners",
];

function Marquee() {
  const items = [
    "NO ADS",
    "NO SUBS",
    "OPEN SOURCE",
    "GPS MANHUNT",
    "Y2K ENERGY",
    "CODE TAGS",
    "FROZEN PINS",
    "ANDROID NATIVE",
  ];
  const row = [...items, ...items];
  return (
    <div className="overflow-hidden border-y-2 border-[#ff2bd6] bg-black/70 py-2">
      <div className="marquee-track font-pixel text-[10px] sm:text-xs text-[#00f0ff]">
        {row.map((t, i) => (
          <span key={`${t}-${i}`} className="inline-flex items-center gap-3">
            <span className="text-[#ff2bd6]">★</span> {t}
          </span>
        ))}
      </div>
    </div>
  );
}

function VisitorCounter() {
  const n = useMemo(() => {
    const base = 128847;
    const day = Math.floor(Date.now() / 86400000) % 900;
    return (base + day * 17).toLocaleString("en-US");
  }, []);
  return (
    <div className="font-pixel text-[9px] sm:text-[10px] text-[#b8ff3c] tracking-wider">
      VISITORS: <span className="text-white">{n}</span>
      <span className="blink text-[#ff2bd6]">_</span>
    </div>
  );
}

export default function App() {
  const [copied, setCopied] = useState(false);

  const cloneCmd = "git clone https://github.com/dedsec-1337/MANHNT.git";

  const copyClone = async () => {
    try {
      await navigator.clipboard.writeText(cloneCmd);
      setCopied(true);
      setTimeout(() => setCopied(false), 1600);
    } catch {
      setCopied(false);
    }
  };

  return (
    <div className="relative min-h-screen">
      <div className="starfield" aria-hidden />

      <header className="relative z-10 border-b-2 border-[#00f0ff]/bg-black/50 backdrop-blur-sm">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3 px-4 py-3">
          <a href="#top" className="font-display text-lg text-white no-underline glitter">
            <span className="text-[#ff2bd6]">◆</span> MANHNT
          </a>
          <nav className="flex flex-wrap items-center gap-3 text-xs sm:text-sm font-tech">
            <a href="#features" className="no-underline hover:underline">
              features
            </a>
            <a href="#play" className="no-underline hover:underline">
              play
            </a>
            <a href="#build" className="no-underline hover:underline">
              build
            </a>
            <a href={GITHUB} target="_blank" rel="noreferrer" className="no-underline hover:underline">
              github ↗
            </a>
          </nav>
          <VisitorCounter />
        </div>
      </header>

      <Marquee />

      <main id="top" className="relative z-10 mx-auto max-w-6xl px-4 pb-24 pt-10 sm:pt-16">
        <section className="grid-y2k rounded-none px-2 pb-16 pt-6 sm:pt-10">
          <div className="status-pill mb-6">
            <span className="inline-block h-2 w-2 animate-pulse rounded-full bg-[#b8ff3c]" />
            SIGNAL LIVE · v1.1.0
          </div>

          <p className="font-pixel text-[10px] sm:text-xs text-[#00f0ff] mb-4">
            // outdoor protocol loaded
          </p>

          <h1 className="font-display text-5xl sm:text-7xl md:text-8xl leading-none chrome-text mb-4">
            MANHNT
          </h1>

          <p className="max-w-xl text-base sm:text-lg text-[#f5e9ff]/90 mb-2">
            Real-life <span className="neon-pink font-bold">GPS manhunt</span> for Android.
            Seekers hunt. Runners hide.{" "}
            <span className="neon-cyan">Your phone is the referee.</span>
          </p>

          <p className="font-tech text-sm text-[#c9b8e8] mb-8 max-w-lg">
            Timed map reveals · code &amp; QR tags · optional Bluetooth nearby · jail, stealth,
            zones · zero paywalls.
          </p>

          <div className="flex flex-wrap gap-3 mb-10">
            <a className="y2k-btn" href={GITHUB} target="_blank" rel="noreferrer">
              ★ Open on GitHub
            </a>
            <a className="y2k-btn y2k-btn-ghost" href={RELEASES} target="_blank" rel="noreferrer">
              Releases
            </a>
            <a className="y2k-btn y2k-btn-ghost" href="#build">
              Build APK
            </a>
          </div>

          <div className="y2k-panel max-w-2xl">
            <div className="flex items-center gap-2 border-b border-white/20 bg-gradient-to-r from-[#ff2bd6]/30 to-[#00f0ff]/20 px-3 py-2">
              <span className="h-3 w-3 rounded-full bg-[#ff5f57]" />
              <span className="h-3 w-3 rounded-full bg-[#febc2e]" />
              <span className="h-3 w-3 rounded-full bg-[#28c840]" />
              <span className="ml-2 font-pixel text-[9px] text-white/80">hunt_session.exe</span>
            </div>
            <div className="space-y-2 p-4 font-tech text-sm text-[#d8c8ff]">
              <p>
                <span className="text-[#b8ff3c]">&gt;</span> role ={" "}
                <span className="text-[#ff2bd6]">SEEKER</span>
              </p>
              <p>
                <span className="text-[#b8ff3c]">&gt;</span> next_reveal_in ={" "}
                <span className="text-[#00f0ff]">02:14</span>
              </p>
              <p>
                <span className="text-[#b8ff3c]">&gt;</span> frozen_pins ={" "}
                <span className="text-[#ffe566]">3</span>
              </p>
              <p>
                <span className="text-[#b8ff3c]">&gt;</span> tag_mode ={" "}
                <span className="text-white">CODE + QR</span>{" "}
                <span className="text-[#888]">(ble assist optional)</span>
              </p>
              <p className="text-[#00f0ff]">
                <span className="text-[#b8ff3c]">&gt;</span> status = READY
                <span className="blink">▌</span>
              </p>
            </div>
          </div>
        </section>

        <section id="features" className="mt-20">
          <h2 className="font-display text-3xl sm:text-4xl chrome-text mb-2">FEATURES</h2>
          <p className="font-pixel text-[10px] text-[#ff2bd6] mb-8">select a module · no microtransactions detected</p>

          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {FEATURES.map((f) => (
              <article key={f.tag} className="y2k-panel p-4 transition hover:-translate-y-0.5">
                <div className="mb-3 flex items-center justify-between">
                  <span className="font-pixel text-[10px] text-[#00f0ff]">{f.tag}</span>
                  <span className="text-[#ff2bd6]">◆</span>
                </div>
                <h3 className="font-display text-lg text-white mb-2">{f.title}</h3>
                <p className="text-sm text-[#c9b8e8] leading-relaxed">{f.body}</p>
              </article>
            ))}
          </div>
        </section>

        <section id="play" className="mt-20">
          <h2 className="font-display text-3xl sm:text-4xl chrome-text mb-2">HOW TO PLAY</h2>
          <p className="font-pixel text-[10px] text-[#00f0ff] mb-8">outdoor protocol · 7 steps</p>

          <ol className="space-y-3">
            {STEPS.map((s, i) => (
              <li key={s} className="y2k-panel flex gap-4 p-4 items-start">
                <span className="font-display text-2xl neon-pink leading-none w-10 shrink-0">
                  {String(i + 1).padStart(2, "0")}
                </span>
                <span className="text-[#f0e6ff] pt-1">{s}</span>
              </li>
            ))}
          </ol>

          <p className="mt-6 text-sm text-[#a894c8] border-l-4 border-[#ffe566] pl-4">
            Stay off roads. Respect private property. Don’t be reckless.
          </p>
        </section>

        <section id="build" className="mt-20">
          <h2 className="font-display text-3xl sm:text-4xl chrome-text mb-2">BUILD</h2>
          <p className="font-pixel text-[10px] text-[#b8ff3c] mb-8">JDK 17 · SDK 34 · Android Studio</p>

          <div className="y2k-panel p-4 sm:p-6">
            <p className="text-sm text-[#c9b8e8] mb-4">
              Open the <code className="text-[#00f0ff]">android/</code> folder in Android Studio →
              Sync → Run. Or ship a signed release APK.
            </p>

            <div className="bevel bg-black/80 p-3 sm:p-4 font-tech text-sm text-[#b8ff3c] overflow-x-auto flex flex-wrap items-center justify-between gap-3">
              <code>{cloneCmd}</code>
              <button type="button" className="y2k-btn !py-2 !text-xs" onClick={copyClone}>
                {copied ? "Copied ✓" : "Copy"}
              </button>
            </div>

            <ul className="mt-6 space-y-2 text-sm text-[#d8c8ff]">
              <li>
                <span className="text-[#ff2bd6]">→</span> Enable Firebase{" "}
                <strong className="text-white">Anonymous Auth</strong>
              </li>
              <li>
                <span className="text-[#ff2bd6]">→</span> Set RTDB URL in{" "}
                <code className="text-[#00f0ff]">Constants.kt</code>
              </li>
              <li>
                <span className="text-[#ff2bd6]">→</span> Use secure rules — never world-open read/write
              </li>
              <li>
                <span className="text-[#ff2bd6]">→</span> BLE / QR / frozen pins need{" "}
                <strong className="text-white">no new rules</strong>
              </li>
            </ul>

            <div className="mt-6 flex flex-wrap gap-3">
              <a className="y2k-btn" href={GITHUB} target="_blank" rel="noreferrer">
                Source
              </a>
              <a className="y2k-btn y2k-btn-ghost" href={CHANGELOG} target="_blank" rel="noreferrer">
                Changelog
              </a>
            </div>
          </div>
        </section>

        <section className="mt-16 text-center">
          <p className="font-pixel text-[10px] sm:text-xs text-[#ffe566] leading-relaxed">
            ★ BEST VIEWED WITH GPS OUTSIDE ★
            <br />
            <span className="text-[#ff2bd6]">THIS SITE IS ALWAYS UNDER CONSTRUCTION</span>
            <span className="blink"> ★</span>
          </p>
        </section>
      </main>

      <footer className="relative z-10 border-t-2 border-[#ff2bd6] bg-black/70 py-8 text-center">
        <p className="font-display text-xl chrome-text mb-2">MANHNT</p>
        <p className="text-xs text-[#a894c8] mb-3">
          Apache 2.0 ·{" "}
          <a href={GITHUB} target="_blank" rel="noreferrer">
            github.com/dedsec-1337/MANHNT
          </a>
        </p>
        <p className="font-pixel text-[9px] text-[#666]">
          play outside · stay off roads · don’t be an idiot
        </p>
      </footer>
    </div>
  );
}
