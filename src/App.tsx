import React, { useState, useEffect, useRef } from 'react';
import {
  Download,
  Smartphone,
  Code2,
  BookOpen,
  CheckCircle2,
  Copy,
  Check,
  Compass,
  MapPin,
  Play,
  Pause,
  AlertTriangle,
  RotateCcw,
  Radio,
  Layers,
  ChevronRight,
  Shield,
  Wifi,
  ExternalLink
} from 'lucide-react';
import confetti from 'canvas-confetti';
import { ANDROID_FILES, ProjectFile } from './data/androidFiles';
import { calculateBearing, calculateDistanceMeters, isOutsidePlayZone } from './logic/gameRules';

export default function App() {
  const [activeTab, setActiveTab] = useState<'simulator' | 'code' | 'guide' | 'checklist'>('simulator');
  const [selectedFile, setSelectedFile] = useState<ProjectFile>(ANDROID_FILES[0]);
  const [copied, setCopied] = useState(false);
  const [isDownloading, setIsDownloading] = useState(false);

  // Simulator State
  const [simRole, setSimRole] = useState<'SEEKER' | 'HIDER'>('HIDER');
  const [simScreen, setSimScreen] = useState<'HOME' | 'CREATE' | 'LOBBY' | 'GAME' | 'RESULTS'>('GAME');
  const [remainingTime, setRemainingTime] = useState(1364); // 22:44
  const [isPaused, setIsPaused] = useState(false);
  const [zoneRadius, setZoneRadius] = useState(500); // meters
  const [hiderCatchCode, setHiderCatchCode] = useState('4829');
  const [isCatchSheetOpen, setIsCatchSheetOpen] = useState(false);
  const [catchInputPin, setCatchInputPin] = useState('');
  const [catchError, setCatchError] = useState<string | null>(null);

  // Simulated Coordinates (Central Park)
  const [centerCoord] = useState({ latitude: 40.7829, longitude: -73.9654 });
  const [playerCoord, setPlayerCoord] = useState({ latitude: 40.7832, longitude: -73.9658 });
  const [targetSeekerCoord, setTargetSeekerCoord] = useState({ latitude: 40.7839, longitude: -73.9652 });
  const [phoneHeading, setPhoneHeading] = useState(45); // degrees

  // Derived calculations
  const distanceToSeeker = Math.round(calculateDistanceMeters(playerCoord, targetSeekerCoord));
  const bearingToSeeker = Math.round(calculateBearing(playerCoord, targetSeekerCoord));
  const relativeAngle = ((bearingToSeeker - phoneHeading + 540) % 360) - 180;
  const isOutOfZone = isOutsidePlayZone(playerCoord, centerCoord, zoneRadius);
  const canCatch = distanceToSeeker <= 10;

  // Countdown timer in simulator
  useEffect(() => {
    if (simScreen !== 'GAME' || isPaused) return;
    const interval = setInterval(() => {
      setRemainingTime((t) => {
        if (t <= 1) {
          setSimScreen('RESULTS');
          return 0;
        }
        return t - 1;
      });
    }, 1000);
    return () => clearInterval(interval);
  }, [simScreen, isPaused]);

  // Handle Download Project ZIP
  const handleDownloadZip = () => {
    setIsDownloading(true);
    const link = document.createElement('a');
    link.href = '/manhunt-android-app.zip';
    link.download = 'manhunt-android-app.zip';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    setTimeout(() => setIsDownloading(false), 800);
  };

  const handleCopyCode = () => {
    navigator.clipboard.writeText(selectedFile.content);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleConfirmCatch = () => {
    if (catchInputPin === hiderCatchCode) {
      confetti({ particleCount: 70, spread: 60, origin: { y: 0.7 } });
      setIsCatchSheetOpen(false);
      setCatchInputPin('');
      setCatchError(null);
      setSimScreen('RESULTS');
    } else {
      setCatchError('Incorrect code! Ask the hider for their 4-digit PIN.');
    }
  };

  const formatTimer = (totalSec: number) => {
    const mins = Math.floor(totalSec / 60);
    const secs = totalSec % 60;
    return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
  };

  return (
    <div className="min-h-screen bg-[#F2F2F7] text-[#1C1C1E] flex flex-col font-sans selection:bg-[#007AFF]/20">
      {/* Top Navigation Bar */}
      <header className="bg-white/80 backdrop-blur-md border-b border-[#E5E5EA] sticky top-0 z-50 px-6 py-3.5 flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-[#007AFF] text-white flex items-center justify-center font-black shadow-sm tracking-tighter">
            MH
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="font-bold text-lg tracking-tight text-[#1C1C1E]">Manhunt</h1>
              <span className="text-[11px] font-semibold bg-[#E5E5EA] text-[#636366] px-2 py-0.5 rounded-full uppercase tracking-wider">
                Native Android & Jetpack Compose
              </span>
            </div>
            <p className="text-xs text-[#8E8E93]">
              Offline GPS Chase Game • OSMDroid • Room SQLite • Local P2P Wi-Fi/Hotspot Sockets
            </p>
          </div>
        </div>

        {/* Center Mode Tabs */}
        <div className="flex items-center bg-[#F2F2F7] p-1 rounded-xl gap-1 border border-[#E5E5EA]">
          <button
            onClick={() => setActiveTab('simulator')}
            className={`flex items-center gap-2 px-3.5 py-1.5 text-xs font-semibold rounded-lg transition-all ${
              activeTab === 'simulator'
                ? 'bg-white text-[#007AFF] shadow-sm'
                : 'text-[#636366] hover:text-[#1C1C1E]'
            }`}
          >
            <Smartphone className="w-3.5 h-3.5" />
            Live Pixel Simulator
          </button>
          <button
            onClick={() => setActiveTab('code')}
            className={`flex items-center gap-2 px-3.5 py-1.5 text-xs font-semibold rounded-lg transition-all ${
              activeTab === 'code'
                ? 'bg-white text-[#007AFF] shadow-sm'
                : 'text-[#636366] hover:text-[#1C1C1E]'
            }`}
          >
            <Code2 className="w-3.5 h-3.5" />
            Android Codebase ({ANDROID_FILES.length})
          </button>
          <button
            onClick={() => setActiveTab('guide')}
            className={`flex items-center gap-2 px-3.5 py-1.5 text-xs font-semibold rounded-lg transition-all ${
              activeTab === 'guide'
                ? 'bg-white text-[#007AFF] shadow-sm'
                : 'text-[#636366] hover:text-[#1C1C1E]'
            }`}
          >
            <BookOpen className="w-3.5 h-3.5" />
            Android Studio & APK Guide
          </button>
          <button
            onClick={() => setActiveTab('checklist')}
            className={`flex items-center gap-2 px-3.5 py-1.5 text-xs font-semibold rounded-lg transition-all ${
              activeTab === 'checklist'
                ? 'bg-white text-[#007AFF] shadow-sm'
                : 'text-[#636366] hover:text-[#1C1C1E]'
            }`}
          >
            <CheckCircle2 className="w-3.5 h-3.5" />
            Deliverable Checklist
          </button>
        </div>

        {/* Export / Download Project ZIP */}
        <button
          onClick={handleDownloadZip}
          disabled={isDownloading}
          className="flex items-center gap-2 bg-[#007AFF] hover:bg-[#0066D6] active:scale-95 text-white font-medium text-xs px-4 py-2 rounded-xl shadow-sm transition-all"
        >
          <Download className="w-4 h-4" />
          <span>{isDownloading ? 'Downloading...' : 'Export Android Project (.ZIP)'}</span>
          <span className="bg-white/20 text-[10px] px-1.5 py-0.5 rounded font-mono">48 KB</span>
        </button>
      </header>

      {/* Main Content Area */}
      <main className="flex-1 max-w-7xl mx-auto w-full p-6">
        {activeTab === 'simulator' && (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
            {/* Left Controls & Inspection Panel */}
            <div className="lg:col-span-5 space-y-5">
              {/* Project Card */}
              <div className="bg-white rounded-2xl p-5 border border-[#E5E5EA] shadow-sm space-y-4">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-[#8E8E93] uppercase tracking-wider">
                    Interactive Android Preview
                  </span>
                  <div className="flex items-center gap-1.5 text-xs text-[#34C759] font-medium bg-[#34C759]/10 px-2.5 py-0.5 rounded-full">
                    <span className="w-1.5 h-1.5 rounded-full bg-[#34C759] animate-pulse"></span>
                    Hotspot P2P Active
                  </div>
                </div>

                <h2 className="text-xl font-bold tracking-tight text-[#1C1C1E]">
                  Test Asymmetric Roles
                </h2>
                <p className="text-xs text-[#636366] leading-relaxed">
                  Switch between <strong>Seeker</strong> (sees prey markers on map, tag button activates under 10m) and <strong>Hider</strong> (stealth map, Apple-style rotating compass strip points toward nearest seeker with real-time distance).
                </p>

                {/* Role Switcher */}
                <div className="grid grid-cols-2 gap-2 bg-[#F2F2F7] p-1.5 rounded-xl">
                  <button
                    onClick={() => setSimRole('HIDER')}
                    className={`py-2 px-3 rounded-lg text-xs font-bold transition-all flex items-center justify-center gap-2 ${
                      simRole === 'HIDER'
                        ? 'bg-white text-[#34C759] shadow-sm'
                        : 'text-[#8E8E93] hover:text-[#1C1C1E]'
                    }`}
                  >
                    <span>🏃</span> Hider View
                  </button>
                  <button
                    onClick={() => setSimRole('SEEKER')}
                    className={`py-2 px-3 rounded-lg text-xs font-bold transition-all flex items-center justify-center gap-2 ${
                      simRole === 'SEEKER'
                        ? 'bg-white text-[#FF9500] shadow-sm'
                        : 'text-[#8E8E93] hover:text-[#1C1C1E]'
                    }`}
                  >
                    <span>🎯</span> Seeker View
                  </button>
                </div>

                {/* Screen Navigation Selector */}
                <div>
                  <label className="text-xs font-semibold text-[#8E8E93] block mb-2">
                    Simulate Screen State:
                  </label>
                  <div className="grid grid-cols-4 gap-1.5">
                    {(['HOME', 'CREATE', 'LOBBY', 'GAME'] as const).map((screen) => (
                      <button
                        key={screen}
                        onClick={() => setSimScreen(screen)}
                        className={`py-1.5 text-xs font-semibold rounded-lg border transition-all ${
                          simScreen === screen
                            ? 'bg-[#007AFF] text-white border-[#007AFF]'
                            : 'bg-white text-[#636366] border-[#E5E5EA] hover:bg-[#F2F2F7]'
                        }`}
                      >
                        {screen}
                      </button>
                    ))}
                  </div>
                </div>

                {/* Dynamic Proximity Simulator */}
                <div className="pt-3 border-t border-[#E5E5EA] space-y-3">
                  <div className="flex items-center justify-between text-xs">
                    <span className="font-semibold text-[#1C1C1E]">GPS Distance to Seeker:</span>
                    <span className="font-mono font-bold text-[#007AFF]">{distanceToSeeker} meters</span>
                  </div>
                  <input
                    type="range"
                    min="3"
                    max="150"
                    value={distanceToSeeker}
                    onChange={(e) => {
                      const newDist = Number(e.target.value);
                      // Adjust seeker coord relative to player coord along latitude
                      const deltaLat = (newDist / 111000);
                      setTargetSeekerCoord({
                        latitude: playerCoord.latitude + deltaLat,
                        longitude: playerCoord.longitude
                      });
                    }}
                    className="w-full accent-[#007AFF]"
                  />
                  <div className="flex justify-between text-[10px] text-[#8E8E93]">
                    <span>3m (Catch Trigger &lt;10m)</span>
                    <span>50m (Close)</span>
                    <span>150m (Safe)</span>
                  </div>
                </div>

                {/* Heading / Compass Simulator */}
                <div className="space-y-2">
                  <div className="flex items-center justify-between text-xs">
                    <span className="font-semibold text-[#1C1C1E]">Device Heading (Sensor):</span>
                    <span className="font-mono font-bold text-[#636366]">{phoneHeading}°</span>
                  </div>
                  <input
                    type="range"
                    min="0"
                    max="359"
                    value={phoneHeading}
                    onChange={(e) => setPhoneHeading(Number(e.target.value))}
                    className="w-full accent-[#007AFF]"
                  />
                </div>

                {/* Quick actions */}
                <div className="flex items-center gap-2 pt-2">
                  <button
                    onClick={() => {
                      // Trigger close range
                      setTargetSeekerCoord({
                        latitude: playerCoord.latitude + (6 / 111000),
                        longitude: playerCoord.longitude
                      });
                    }}
                    className="flex-1 py-1.5 text-xs font-semibold bg-[#FF9500]/10 text-[#FF9500] hover:bg-[#FF9500]/20 rounded-lg transition-colors text-center"
                  >
                    Simulate &lt;10m Catch Range
                  </button>
                  <button
                    onClick={() => {
                      // Trigger out of bounds
                      setPlayerCoord({
                        latitude: centerCoord.latitude + (750 / 111000),
                        longitude: centerCoord.longitude
                      });
                    }}
                    className="flex-1 py-1.5 text-xs font-semibold bg-[#FF3B30]/10 text-[#FF3B30] hover:bg-[#FF3B30]/20 rounded-lg transition-colors text-center"
                  >
                    Trigger Out-of-Bounds
                  </button>
                </div>
              </div>

              {/* Technical Specifications Callout */}
              <div className="bg-white rounded-2xl p-4 border border-[#E5E5EA] shadow-sm text-xs space-y-2.5">
                <div className="flex items-center gap-2 font-bold text-[#1C1C1E]">
                  <Shield className="w-4 h-4 text-[#007AFF]" />
                  <span>Verified Native Android Architecture</span>
                </div>
                <ul className="space-y-1.5 text-[#636366]">
                  <li className="flex items-start gap-2">
                    <Check className="w-3.5 h-3.5 text-[#34C759] shrink-0 mt-0.5" />
                    <span><strong>OSMDroid MapView</strong> wrapped inside Compose <code>AndroidView</code> with zero XML layouts.</span>
                  </li>
                  <li className="flex items-start gap-2">
                    <Check className="w-3.5 h-3.5 text-[#34C759] shrink-0 mt-0.5" />
                    <span><strong>UDP Multicast (Port 8888)</strong> for decentralized zero-config room discovery on local Hotspot.</span>
                  </li>
                  <li className="flex items-start gap-2">
                    <Check className="w-3.5 h-3.5 text-[#34C759] shrink-0 mt-0.5" />
                    <span><strong>TCP Socket Relay (Port 9876)</strong> with coroutines streaming coordinates every 5 seconds.</span>
                  </li>
                  <li className="flex items-start gap-2">
                    <Check className="w-3.5 h-3.5 text-[#34C759] shrink-0 mt-0.5" />
                    <span><strong>Foreground Service</strong> with <code>FOREGROUND_SERVICE_LOCATION</code> and ongoing countdown notification.</span>
                  </li>
                  <li className="flex items-start gap-2">
                    <Check className="w-3.5 h-3.5 text-[#34C759] shrink-0 mt-0.5" />
                    <span><strong>Room Database (SQLite)</strong> local persistence for device UUID, profile, and match history.</span>
                  </li>
                </ul>
              </div>
            </div>

            {/* Right: Phone Frame with Pixel Aspect Ratio & Apple-on-Android Compose UI */}
            <div className="lg:col-span-7 flex justify-center">
              <div className="w-[350px] h-[680px] bg-[#1C1C1E] rounded-[44px] p-2.5 shadow-2xl border-4 border-[#3A3A3C] relative overflow-hidden flex flex-col">
                {/* Speaker & Camera Pill */}
                <div className="absolute top-3.5 left-1/2 -translate-x-1/2 w-24 h-3.5 bg-black rounded-full z-50 flex items-center justify-center">
                  <div className="w-2.5 h-2.5 rounded-full bg-[#1C1C1E] mr-2"></div>
                  <div className="w-1.5 h-1.5 rounded-full bg-[#0A84FF]/40"></div>
                </div>

                {/* Android Screen Canvas */}
                <div className="w-full h-full bg-[#F8F9FA] rounded-[34px] overflow-hidden flex flex-col relative select-none">
                  {/* Android Status Bar */}
                  <div className="pt-2 px-5 pb-0.5 flex justify-between items-center text-[10px] font-bold text-[#1C1C1E] z-40 bg-transparent">
                    <span>9:41</span>
                    <div className="flex items-center gap-1.5 text-[#1C1C1E]">
                      <Wifi className="w-3 h-3" />
                      <div className="w-4 h-2 border border-[#1C1C1E] rounded-sm p-[1px] flex items-center">
                        <div className="w-full h-full bg-[#1C1C1E] rounded-xs"></div>
                      </div>
                    </div>
                  </div>

                  {/* SCREEN: HOME */}
                  {simScreen === 'HOME' && (
                    <div className="flex-1 p-5 flex flex-col justify-between overflow-y-auto">
                      <div className="space-y-4">
                        <div className="flex items-center justify-between pt-2">
                          <div>
                            <h2 className="text-2xl font-black tracking-tight text-[#1C1C1E]">MANHUNT</h2>
                            <p className="text-[12px] text-[#8E8E93]">Local GPS Chase Game</p>
                          </div>
                          <div className="flex items-center gap-1.5 bg-white px-3 py-1.5 rounded-full shadow-sm border border-[#E5E5EA]">
                            <span className="text-base">🏃</span>
                            <span className="text-xs font-semibold text-[#1C1C1E]">Runner</span>
                          </div>
                        </div>

                        {/* Join with Code Card */}
                        <div className="bg-white rounded-2xl p-4 shadow-sm border border-[#E5E5EA] space-y-3">
                          <h3 className="text-sm font-bold text-[#1C1C1E]">Join with Game Code</h3>
                          <p className="text-xs text-[#8E8E93]">Enter the 6-character code from the organizer.</p>
                          <div className="bg-[#F8F9FA] border border-[#E5E5EA] rounded-xl px-3 py-2 text-sm font-mono tracking-widest text-center text-[#007AFF] font-bold">
                            HUNT42
                          </div>
                          <button
                            onClick={() => setSimScreen('LOBBY')}
                            className="w-full py-2.5 bg-[#007AFF] text-white rounded-xl text-xs font-semibold shadow-sm"
                          >
                            Join Chase
                          </button>
                        </div>

                        {/* Organize Card */}
                        <div className="bg-white rounded-2xl p-4 shadow-sm border border-[#E5E5EA] space-y-3">
                          <h3 className="text-sm font-bold text-[#1C1C1E]">Organize New Chase</h3>
                          <p className="text-xs text-[#8E8E93]">Set boundary radius, duration, and invite nearby players.</p>
                          <button
                            onClick={() => setSimScreen('CREATE')}
                            className="w-full py-2.5 bg-[#1C1C1E] text-white rounded-xl text-xs font-semibold shadow-sm"
                          >
                            Create Room
                          </button>
                        </div>

                        {/* Nearby Rooms */}
                        <div className="space-y-2">
                          <div className="flex items-center justify-between text-xs">
                            <span className="font-bold text-[#1C1C1E]">Nearby Rooms (Local Wi-Fi)</span>
                            <span className="text-[#8E8E93]">1 found</span>
                          </div>
                          <div
                            onClick={() => setSimScreen('LOBBY')}
                            className="bg-white rounded-2xl p-3 border border-[#E5E5EA] flex items-center justify-between cursor-pointer hover:border-[#007AFF] transition-colors"
                          >
                            <div>
                              <p className="text-xs font-bold text-[#1C1C1E]">Sunset Park Chase</p>
                              <p className="text-[11px] text-[#8E8E93]">Code: HUNT42 • 500m zone</p>
                            </div>
                            <span className="text-xs font-bold text-[#007AFF] bg-[#007AFF]/10 px-2 py-1 rounded-lg">
                              Join
                            </span>
                          </div>
                        </div>
                      </div>
                    </div>
                  )}

                  {/* SCREEN: CREATE ROOM */}
                  {simScreen === 'CREATE' && (
                    <div className="flex-1 p-5 flex flex-col justify-between overflow-y-auto">
                      <div className="space-y-4">
                        <div className="flex items-center gap-2">
                          <button onClick={() => setSimScreen('HOME')} className="text-xs text-[#007AFF] font-semibold">
                            ← Back
                          </button>
                          <h2 className="text-lg font-bold text-[#1C1C1E]">New Match</h2>
                        </div>

                        <div className="bg-white rounded-2xl p-4 border border-[#E5E5EA] space-y-2">
                          <label className="text-xs font-semibold text-[#1C1C1E]">Room Name</label>
                          <div className="bg-[#F8F9FA] border border-[#E5E5EA] rounded-xl px-3 py-2 text-xs text-[#1C1C1E]">
                            Sunset Park Chase
                          </div>
                        </div>

                        <div className="bg-white rounded-2xl p-4 border border-[#E5E5EA] space-y-2">
                          <div className="flex justify-between items-center text-xs font-semibold">
                            <span>Play Zone Boundary</span>
                            <span className="text-[#007AFF]">{zoneRadius}m radius</span>
                          </div>
                          <input
                            type="range"
                            min="200"
                            max="2000"
                            step="100"
                            value={zoneRadius}
                            onChange={(e) => setZoneRadius(Number(e.target.value))}
                            className="w-full accent-[#007AFF]"
                          />
                          <p className="text-[11px] text-[#8E8E93]">Circular zone centered at your GPS location.</p>
                        </div>

                        <div className="bg-white rounded-2xl p-4 border border-[#E5E5EA] space-y-2">
                          <span className="text-xs font-semibold">Match Duration</span>
                          <div className="grid grid-cols-4 gap-2">
                            {[15, 30, 45, 60].map((m) => (
                              <button
                                key={m}
                                className={`py-1.5 text-xs font-bold rounded-lg ${
                                  m === 30 ? 'bg-[#007AFF] text-white' : 'bg-[#F2F2F7] text-[#1C1C1E]'
                                }`}
                              >
                                {m}m
                              </button>
                            ))}
                          </div>
                        </div>

                        <div className="bg-white rounded-2xl p-4 border border-[#E5E5EA] space-y-2">
                          <span className="text-xs font-semibold">Role Ratio</span>
                          <div className="bg-[#007AFF] text-white p-2 rounded-xl text-xs font-semibold text-center">
                            1 Hider : 3 Seekers (Standard)
                          </div>
                        </div>
                      </div>

                      <button
                        onClick={() => setSimScreen('LOBBY')}
                        className="w-full py-3 bg-[#007AFF] text-white rounded-xl text-xs font-bold shadow-sm"
                      >
                        Create Room & Open Lobby
                      </button>
                    </div>
                  )}

                  {/* SCREEN: LOBBY */}
                  {simScreen === 'LOBBY' && (
                    <div className="flex-1 p-5 flex flex-col justify-between overflow-y-auto">
                      <div className="space-y-4">
                        <div className="flex items-center justify-between">
                          <h2 className="text-base font-bold text-[#1C1C1E]">Sunset Park Chase</h2>
                          <button onClick={() => setSimScreen('HOME')} className="text-xs text-[#8E8E93]">
                            Leave
                          </button>
                        </div>

                        {/* Join Code Card */}
                        <div className="bg-white rounded-2xl p-4 text-center border border-[#E5E5EA] shadow-sm space-y-1">
                          <span className="text-[10px] font-bold text-[#8E8E93] tracking-widest uppercase">
                            JOIN CODE
                          </span>
                          <p className="text-3xl font-black text-[#007AFF] tracking-widest">HUNT42</p>
                          <p className="text-[11px] text-[#8E8E93]">Share with players on the same Wi-Fi / Hotspot</p>
                        </div>

                        {/* Players */}
                        <div className="space-y-2">
                          <div className="flex justify-between text-xs font-bold text-[#1C1C1E]">
                            <span>Players (4)</span>
                            <span className="text-[#8E8E93]">3 Seekers • 1 Hider</span>
                          </div>

                          <div className="space-y-1.5">
                            <div className="bg-white rounded-xl p-3 border border-[#E5E5EA] flex items-center justify-between">
                              <div className="flex items-center gap-2">
                                <span className="text-lg">🏃</span>
                                <div>
                                  <p className="text-xs font-bold text-[#1C1C1E]">Agent Alex (You)</p>
                                  <p className="text-[10px] text-[#007AFF]">Organizer</p>
                                </div>
                              </div>
                              <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-[#34C759]/15 text-[#34C759]">
                                HIDER
                              </span>
                            </div>

                            <div className="bg-white rounded-xl p-3 border border-[#E5E5EA] flex items-center justify-between">
                              <div className="flex items-center gap-2">
                                <span className="text-lg">🎯</span>
                                <p className="text-xs font-bold text-[#1C1C1E]">Marcus</p>
                              </div>
                              <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-[#FF9500]/15 text-[#FF9500]">
                                SEEKER
                              </span>
                            </div>

                            <div className="bg-white rounded-xl p-3 border border-[#E5E5EA] flex items-center justify-between">
                              <div className="flex items-center gap-2">
                                <span className="text-lg">⚡</span>
                                <p className="text-xs font-bold text-[#1C1C1E]">Elena</p>
                              </div>
                              <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-[#FF9500]/15 text-[#FF9500]">
                                SEEKER
                              </span>
                            </div>

                            <div className="bg-white rounded-xl p-3 border border-[#E5E5EA] flex items-center justify-between">
                              <div className="flex items-center gap-2">
                                <span className="text-lg">🐺</span>
                                <p className="text-xs font-bold text-[#1C1C1E]">David</p>
                              </div>
                              <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-[#FF9500]/15 text-[#FF9500]">
                                SEEKER
                              </span>
                            </div>
                          </div>
                        </div>
                      </div>

                      <div className="space-y-2">
                        <button
                          onClick={() => setSimScreen('GAME')}
                          className="w-full py-3 bg-[#007AFF] text-white rounded-xl text-xs font-bold shadow-sm"
                        >
                          Start Manhunt
                        </button>
                      </div>
                    </div>
                  )}

                  {/* SCREEN: ACTIVE GAME */}
                  {simScreen === 'GAME' && (
                    <div className="flex-1 flex flex-col relative overflow-hidden">
                      {/* Top HUD Card */}
                      <div className="p-3.5 z-20 space-y-2">
                        <div className="bg-white/95 backdrop-blur-md rounded-2xl p-3.5 shadow-md border border-[#E5E5EA] flex items-center justify-between">
                          <div>
                            <div className="flex items-center gap-2">
                              <span className="text-xs font-bold text-[#1C1C1E]">Sunset Park</span>
                              <span
                                className={`text-[10px] font-extrabold px-2 py-0.5 rounded-full ${
                                  simRole === 'HIDER'
                                    ? 'bg-[#34C759]/15 text-[#16A34A]'
                                    : 'bg-[#FF9500]/15 text-[#D97706]'
                                }`}
                              >
                                {simRole}
                              </span>
                            </div>
                            {isOutOfZone && (
                              <div className="flex items-center gap-1 text-[10px] font-bold text-[#FF3B30] mt-0.5">
                                <AlertTriangle className="w-3 h-3" />
                                <span>OUT OF BOUNDS!</span>
                              </div>
                            )}
                          </div>

                          <div className="flex items-center gap-2">
                            <span
                              className={`text-2xl font-black tracking-tight font-mono ${
                                remainingTime < 180 ? 'text-[#FF3B30]' : 'text-[#1C1C1E]'
                              }`}
                            >
                              {formatTimer(remainingTime)}
                            </span>
                            <button
                              onClick={() => setIsPaused(!isPaused)}
                              className="p-1.5 rounded-lg bg-[#F2F2F7] hover:bg-[#E5E5EA] transition-colors"
                              title="Organizer Safety Pause"
                            >
                              {isPaused ? <Play className="w-4 h-4 text-[#007AFF]" /> : <Pause className="w-4 h-4 text-[#1C1C1E]" />}
                            </button>
                          </div>
                        </div>

                        {/* Safety Pause Notice */}
                        {isPaused && (
                          <div className="bg-[#FF9500] text-white text-[11px] font-bold py-1 px-3 rounded-xl text-center shadow-sm">
                            SAFETY PAUSE ACTIVE — FREEZE POSITIONS
                          </div>
                        )}
                      </div>

                      {/* Map Area (Simulating OSMDroid MapView Canvas) */}
                      <div className="h-[260px] flex-none relative bg-[#E9EBEF] overflow-hidden">
                        {/* OpenStreetMap Grid & Park Styling */}
                        <div className="absolute inset-0 bg-[radial-gradient(#CBD5E1_1px,transparent_1px)] [background-size:16px_16px] opacity-70"></div>

                        {/* Map Park Features (Visual Street Map styling) */}
                        <div className="absolute top-1/4 left-1/4 w-44 h-44 bg-[#D1E7DD] rounded-3xl opacity-60"></div>
                        <div className="absolute top-1/3 right-4 w-28 h-52 bg-[#E2E8F0] rounded-xl opacity-40"></div>
                        <div className="absolute bottom-8 left-12 w-64 h-3 bg-[#E2E8F0] rotate-12"></div>
                        <div className="absolute top-12 left-24 w-3 h-64 bg-[#E2E8F0]"></div>

                        {/* OSMDroid Circular Play Zone Boundary */}
                        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-64 h-64 rounded-full border-2 border-[#007AFF]/60 bg-[#007AFF]/10 pointer-events-none flex items-center justify-center">
                          <span className="text-[10px] text-[#007AFF] font-mono font-semibold bg-white/70 px-2 py-0.5 rounded-full mb-48">
                            {zoneRadius}m Play Zone
                          </span>
                        </div>

                        {/* Player Marker (Blue Dot with pulsing radar) */}
                        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 flex items-center justify-center z-10">
                          <div className="w-8 h-8 rounded-full bg-[#007AFF]/25 animate-ping absolute"></div>
                          <div className="w-5 h-5 rounded-full bg-[#007AFF] border-2 border-white shadow-md flex items-center justify-center text-white text-[9px] font-bold">
                            {simRole === 'HIDER' ? '🏃' : '🎯'}
                          </div>
                        </div>

                        {/* Targets on Map */}
                        {simRole === 'SEEKER' ? (
                          /* Seeker sees Hiders as Orange Markers */
                          <div
                            style={{
                              transform: `translate(${relativeAngle > 0 ? 35 : -35}px, -${Math.max(15, Math.min(90, distanceToSeeker))}px)`
                            }}
                            className="absolute top-1/2 left-1/2 flex flex-col items-center z-10 transition-transform duration-300"
                          >
                            <div className="w-5 h-5 rounded-full bg-[#FF9500] border-2 border-white shadow-md flex items-center justify-center text-white text-[9px] font-bold">
                              🏃
                            </div>
                            <span className="text-[9px] font-bold bg-white text-[#FF9500] px-1 rounded shadow-xs mt-0.5">
                              Alex ({distanceToSeeker}m)
                            </span>
                          </div>
                        ) : (
                          /* Hiders do NOT see Seeker coordinates on map (Rules requirement) */
                          <div className="absolute bottom-2 left-3 bg-white/80 backdrop-blur-xs px-2 py-1 rounded-md text-[10px] text-[#8E8E93] z-10">
                            🛡️ Stealth Active: Seekers hidden from map
                          </div>
                        )}
                      </div>

                      {/* Bottom Controls HUD */}
                      <div className="p-3 z-20 space-y-2 bg-gradient-to-t from-[#F8F9FA] via-[#F8F9FA]/95 to-transparent pt-3 pb-1">
                        {/* Hider View: Compass Strip pointing towards nearest Seeker */}
                        {simRole === 'HIDER' && (
                          <div className="space-y-2">
                            <div className="bg-white rounded-2xl p-3 shadow-md border border-[#E5E5EA] flex items-center justify-between">
                              <div className="flex items-center gap-3">
                                {/* Direction Arrow */}
                                <div className="w-10 h-10 rounded-full bg-[#FFF7ED] flex items-center justify-center">
                                  <Compass
                                    style={{ transform: `rotate(${relativeAngle}deg)` }}
                                    className="w-6 h-6 text-[#FF9500] transition-transform duration-200"
                                  />
                                </div>
                                <div>
                                  <p className="text-[11px] font-medium text-[#8E8E93]">Nearest Seeker</p>
                                  <p className="text-sm font-bold text-[#1C1C1E]">{distanceToSeeker}m away</p>
                                </div>
                              </div>

                              <span
                                className={`text-[10px] font-bold px-2 py-1 rounded-lg ${
                                  distanceToSeeker < 15
                                    ? 'bg-[#FF3B30]/15 text-[#FF3B30] animate-pulse'
                                    : distanceToSeeker < 40
                                    ? 'bg-[#FF9500]/15 text-[#FF9500]'
                                    : 'bg-[#F2F2F7] text-[#8E8E93]'
                                }`}
                              >
                                {distanceToSeeker < 15 ? 'CRITICAL' : distanceToSeeker < 40 ? 'CLOSE' : 'SAFE'}
                              </span>
                            </div>

                            {/* Hider Catch Code */}
                            <div className="bg-white rounded-2xl p-3 border border-[#E5E5EA] flex items-center justify-between">
                              <div>
                                <p className="text-[10px] font-bold text-[#8E8E93] uppercase">Your Catch Code</p>
                                <p className="text-[11px] text-[#8E8E93]">Give only if physically tagged</p>
                              </div>
                              <span className="text-xl font-mono font-black text-[#1C1C1E] tracking-widest bg-[#F2F2F7] px-3 py-1 rounded-lg">
                                {hiderCatchCode}
                              </span>
                            </div>
                          </div>
                        )}

                        {/* Seeker View: Catch Action Button */}
                        {simRole === 'SEEKER' && (
                          <div>
                            <button
                              disabled={!canCatch}
                              onClick={() => setIsCatchSheetOpen(true)}
                              className={`w-full py-3.5 rounded-2xl font-bold text-xs shadow-md transition-all flex items-center justify-center gap-2 ${
                                canCatch
                                  ? 'bg-[#FF9500] text-white active:scale-95 hover:bg-[#E68600]'
                                  : 'bg-[#E5E5EA] text-[#8E8E93] cursor-not-allowed'
                              }`}
                            >
                              <span>⚡</span>
                              <span>{canCatch ? 'Tag Alex (Within 10m!)' : `Catch Button (Too Far: ${distanceToSeeker}m)`}</span>
                            </button>
                          </div>
                        )}

                        {/* Leave Game */}
                        <button
                          onClick={() => setSimScreen('HOME')}
                          className="w-full text-center text-[11px] text-[#8E8E93] hover:text-[#FF3B30] transition-colors py-1"
                        >
                          Leave Match
                        </button>
                      </div>

                      {/* Modal: Catch Bottom Sheet */}
                      {isCatchSheetOpen && (
                        <div className="absolute inset-0 bg-black/50 backdrop-blur-xs z-50 flex flex-col justify-end">
                          <div className="bg-white rounded-t-[32px] p-5 space-y-4 shadow-2xl animate-in slide-in-from-bottom duration-200">
                            <div className="w-10 h-1 bg-[#E5E5EA] rounded-full mx-auto"></div>
                            <div className="text-center">
                              <h3 className="text-base font-bold text-[#1C1C1E]">Tag Alex!</h3>
                              <p className="text-xs text-[#8E8E93]">Enter the 4-digit code shown on Alex's phone.</p>
                            </div>

                            {/* 4 Digit Boxes */}
                            <div className="flex justify-center gap-2.5">
                              {[0, 1, 2, 3].map((i) => (
                                <div
                                  key={i}
                                  className="w-11 h-12 rounded-xl bg-[#F2F2F7] border border-[#E5E5EA] flex items-center justify-center text-lg font-bold font-mono text-[#1C1C1E]"
                                >
                                  {catchInputPin[i] || ''}
                                </div>
                              ))}
                            </div>

                            {catchError && (
                              <p className="text-[11px] text-[#FF3B30] text-center font-medium">{catchError}</p>
                            )}

                            {/* Numeric Keypad */}
                            <div className="grid grid-cols-3 gap-2 max-w-[240px] mx-auto">
                              {['1', '2', '3', '4', '5', '6', '7', '8', '9', 'C', '0', '⌫'].map((key) => (
                                <button
                                  key={key}
                                  onClick={() => {
                                    setCatchError(null);
                                    if (key === 'C') setCatchInputPin('');
                                    else if (key === '⌫') setCatchInputPin((p) => p.slice(0, -1));
                                    else if (catchInputPin.length < 4) setCatchInputPin((p) => p + key);
                                  }}
                                  className="h-10 bg-[#F2F2F7] hover:bg-[#E5E5EA] rounded-xl text-sm font-semibold text-[#1C1C1E] active:scale-95 transition-transform"
                                >
                                  {key}
                                </button>
                              ))}
                            </div>

                            <div className="flex gap-2 pt-1">
                              <button
                                onClick={() => setIsCatchSheetOpen(false)}
                                className="flex-1 py-2.5 bg-[#F2F2F7] text-[#1C1C1E] rounded-xl text-xs font-semibold"
                              >
                                Cancel
                              </button>
                              <button
                                onClick={handleConfirmCatch}
                                disabled={catchInputPin.length !== 4}
                                className="flex-1 py-2.5 bg-[#FF9500] disabled:bg-[#E5E5EA] text-white rounded-xl text-xs font-bold shadow-sm"
                              >
                                Confirm Tag
                              </button>
                            </div>
                          </div>
                        </div>
                      )}
                    </div>
                  )}

                  {/* SCREEN: RESULTS */}
                  {simScreen === 'RESULTS' && (
                    <div className="flex-1 p-5 flex flex-col justify-between overflow-y-auto">
                      <div className="space-y-4 pt-4">
                        <div className="bg-white rounded-2xl p-5 border border-[#E5E5EA] text-center shadow-sm space-y-2">
                          <span className="text-4xl block">🎯</span>
                          <h2 className="text-xl font-black text-[#1C1C1E]">SEEKERS TRIUMPH!</h2>
                          <p className="text-xs text-[#8E8E93]">All hiders were tagged before the timer expired.</p>

                          <div className="grid grid-cols-3 gap-2 pt-3 border-t border-[#E5E5EA]">
                            <div>
                              <p className="text-[10px] text-[#8E8E93]">Duration</p>
                              <p className="text-sm font-bold text-[#1C1C1E]">18m</p>
                            </div>
                            <div>
                              <p className="text-[10px] text-[#8E8E93]">Catches</p>
                              <p className="text-sm font-bold text-[#1C1C1E]">3</p>
                            </div>
                            <div>
                              <p className="text-[10px] text-[#8E8E93]">Radius</p>
                              <p className="text-sm font-bold text-[#1C1C1E]">{zoneRadius}m</p>
                            </div>
                          </div>
                        </div>

                        <div className="space-y-2">
                          <span className="text-xs font-bold text-[#1C1C1E]">Player Standings</span>
                          <div className="space-y-1.5">
                            <div className="bg-white rounded-xl p-3 border border-[#E5E5EA] flex justify-between items-center text-xs">
                              <span className="font-semibold">🎯 Marcus (Seeker)</span>
                              <span className="text-[#34C759] font-bold">2 Catches (MVP)</span>
                            </div>
                            <div className="bg-white rounded-xl p-3 border border-[#E5E5EA] flex justify-between items-center text-xs">
                              <span className="font-semibold">🏃 Alex (Hider)</span>
                              <span className="text-[#FF3B30] font-bold">Caught at 17m</span>
                            </div>
                          </div>
                        </div>
                      </div>

                      <button
                        onClick={() => {
                          setSimScreen('HOME');
                          setRemainingTime(1800);
                        }}
                        className="w-full py-3 bg-[#007AFF] text-white rounded-xl text-xs font-bold shadow-sm"
                      >
                        Return to Home
                      </button>
                    </div>
                  )}

                  {/* Android Home Navigation Gesture Bar */}
                  <div className="py-2 flex justify-center bg-transparent z-40">
                    <div className="w-32 h-1 bg-[#1C1C1E]/30 rounded-full"></div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* CODE TAB */}
        {activeTab === 'code' && (
          <div className="bg-white rounded-2xl border border-[#E5E5EA] shadow-sm overflow-hidden flex flex-col h-[750px]">
            {/* Header */}
            <div className="px-5 py-3 border-b border-[#E5E5EA] flex items-center justify-between bg-[#F8F9FA]">
              <div className="flex items-center gap-2">
                <Code2 className="w-4 h-4 text-[#007AFF]" />
                <span className="font-bold text-sm text-[#1C1C1E]">Native Android Kotlin Project Structure</span>
                <span className="text-xs text-[#8E8E93]">({selectedFile.path})</span>
              </div>
              <div className="flex items-center gap-2">
                <button
                  onClick={handleCopyCode}
                  className="flex items-center gap-1.5 text-xs font-medium px-3 py-1.5 rounded-lg bg-white border border-[#E5E5EA] hover:bg-[#F2F2F7] transition-colors"
                >
                  {copied ? <Check className="w-3.5 h-3.5 text-[#34C759]" /> : <Copy className="w-3.5 h-3.5" />}
                  <span>{copied ? 'Copied!' : 'Copy Code'}</span>
                </button>
                <button
                  onClick={handleDownloadZip}
                  className="flex items-center gap-1.5 text-xs font-medium px-3 py-1.5 rounded-lg bg-[#007AFF] text-white hover:bg-[#0066D6] transition-colors"
                >
                  <Download className="w-3.5 h-3.5" />
                  <span>Download .ZIP</span>
                </button>
              </div>
            </div>

            {/* File explorer layout */}
            <div className="flex-1 flex overflow-hidden">
              {/* Sidebar File Tree */}
              <div className="w-72 border-r border-[#E5E5EA] bg-[#F8F9FA] p-3 overflow-y-auto space-y-1">
                <span className="text-[11px] font-bold text-[#8E8E93] uppercase tracking-wider px-2 block mb-2">
                  Source Files
                </span>
                {ANDROID_FILES.map((file) => (
                  <button
                    key={file.path}
                    onClick={() => setSelectedFile(file)}
                    className={`w-full text-left px-3 py-2 rounded-xl text-xs transition-all flex items-start gap-2 ${
                      selectedFile.path === file.path
                        ? 'bg-[#007AFF] text-white font-semibold shadow-xs'
                        : 'text-[#636366] hover:bg-[#E5E5EA] hover:text-[#1C1C1E]'
                    }`}
                  >
                    <span className="font-mono text-[10px] opacity-80 mt-0.5">KT</span>
                    <div className="truncate flex-1">
                      <p className="truncate">{file.name}</p>
                      <p className={`text-[10px] truncate ${selectedFile.path === file.path ? 'text-white/80' : 'text-[#8E8E93]'}`}>
                        {file.description}
                      </p>
                    </div>
                  </button>
                ))}
              </div>

              {/* Code viewer */}
              <div className="flex-1 bg-[#1E1E1E] text-[#D4D4D4] p-4 overflow-auto font-mono text-xs leading-relaxed">
                <pre>{selectedFile.content}</pre>
              </div>
            </div>
          </div>
        )}

        {/* GUIDE TAB */}
        {activeTab === 'guide' && (
          <div className="max-w-4xl mx-auto space-y-6">
            <div className="bg-white rounded-2xl p-6 border border-[#E5E5EA] shadow-sm space-y-4">
              <h2 className="text-xl font-bold text-[#1C1C1E]">
                How to Open in Android Studio & Generate Signed APK
              </h2>
              <p className="text-xs text-[#636366] leading-relaxed">
                The exported project is a 100% standard Gradle Android Studio project using modern Kotlin 1.9.23, Jetpack Compose 2024.04.01, OSMDroid 6.1.18, Room 2.6.1, and Accompanist Permissions.
              </p>

              <div className="space-y-4 pt-2">
                <div className="flex gap-4 items-start p-4 bg-[#F8F9FA] rounded-xl border border-[#E5E5EA]">
                  <span className="w-7 h-7 rounded-full bg-[#007AFF] text-white font-bold text-xs flex items-center justify-center shrink-0">
                    1
                  </span>
                  <div className="space-y-1">
                    <h3 className="text-sm font-bold text-[#1C1C1E]">Download & Extract Project ZIP</h3>
                    <p className="text-xs text-[#636366]">
                      Click <strong>"Export Android Project (.ZIP)"</strong> above. Unzip <code>manhunt-android-app.zip</code> to a folder of your choice (e.g. <code>~/AndroidStudioProjects/Manhunt</code>).
                    </p>
                  </div>
                </div>

                <div className="flex gap-4 items-start p-4 bg-[#F8F9FA] rounded-xl border border-[#E5E5EA]">
                  <span className="w-7 h-7 rounded-full bg-[#007AFF] text-white font-bold text-xs flex items-center justify-center shrink-0">
                    2
                  </span>
                  <div className="space-y-1">
                    <h3 className="text-sm font-bold text-[#1C1C1E]">Open in Android Studio</h3>
                    <p className="text-xs text-[#636366]">
                      Launch Android Studio (Hedgehog, Iguana, or newer). Click <strong>File &gt; Open</strong> and choose the extracted root folder. Android Studio will automatically invoke Gradle Sync and download all dependencies.
                    </p>
                  </div>
                </div>

                <div className="flex gap-4 items-start p-4 bg-[#F8F9FA] rounded-xl border border-[#E5E5EA]">
                  <span className="w-7 h-7 rounded-full bg-[#007AFF] text-white font-bold text-xs flex items-center justify-center shrink-0">
                    3
                  </span>
                  <div className="space-y-1">
                    <h3 className="text-sm font-bold text-[#1C1C1E]">JDK & SDK Setup</h3>
                    <p className="text-xs text-[#636366]">
                      Ensure Gradle is configured with <strong>Java 17</strong> (Settings &gt; Build, Execution, Deployment &gt; Build Tools &gt; Gradle &gt; Gradle JDK). Minimum SDK is <strong>26 (Android 8.0)</strong>, Target/Compile SDK is <strong>34 (Android 14)</strong>.
                    </p>
                  </div>
                </div>

                <div className="flex gap-4 items-start p-4 bg-[#F8F9FA] rounded-xl border border-[#E5E5EA]">
                  <span className="w-7 h-7 rounded-full bg-[#007AFF] text-white font-bold text-xs flex items-center justify-center shrink-0">
                    4
                  </span>
                  <div className="space-y-1">
                    <h3 className="text-sm font-bold text-[#1C1C1E]">Generate APK / Run on Physical Device</h3>
                    <p className="text-xs text-[#636366]">
                      Go to menu: <strong>Build &gt; Build Bundle(s) / APK(s) &gt; Build APK(s)</strong> or <strong>Generate Signed Bundle / APK</strong>. The compiled APK will be generated at <code>app/build/outputs/apk/debug/app-debug.apk</code>.
                    </p>
                  </div>
                </div>

                <div className="flex gap-4 items-start p-4 bg-[#F8F9FA] rounded-xl border border-[#E5E5EA]">
                  <span className="w-7 h-7 rounded-full bg-[#007AFF] text-white font-bold text-xs flex items-center justify-center shrink-0">
                    5
                  </span>
                  <div className="space-y-1">
                    <h3 className="text-sm font-bold text-[#1C1C1E]">Hotspot P2P Testing Outdoors</h3>
                    <p className="text-xs text-[#636366]">
                      Have one player enable their Android Wi-Fi Hotspot (no mobile cellular data is required). Other players connect their Wi-Fi to that hotspot. The Organizer taps <strong>Create Room</strong>, and all players will automatically discover each other via UDP multicast!
                    </p>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* CHECKLIST TAB */}
        {activeTab === 'checklist' && (
          <div className="max-w-3xl mx-auto space-y-5">
            <div className="bg-white rounded-2xl p-6 border border-[#E5E5EA] shadow-sm space-y-4">
              <div className="flex items-center justify-between">
                <h2 className="text-lg font-bold text-[#1C1C1E]">Final Deliverable Verification Checklist</h2>
                <span className="text-xs font-bold text-[#34C759] bg-[#34C759]/15 px-2.5 py-1 rounded-full">
                  8 / 8 Confirmed
                </span>
              </div>

              <div className="space-y-3">
                {[
                  {
                    title: 'Native Kotlin Android project with Jetpack Compose UI',
                    desc: 'Single-activity architecture (MainActivity.kt), Compose Material3, zero XML layouts.'
                  },
                  {
                    title: 'Gradle build files configured with all required dependencies',
                    desc: 'build.gradle.kts and settings.gradle.kts contain OSMDroid, Room, Coroutines, Accompanist, and Play Services.'
                  },
                  {
                    title: 'OSMDroid MapView wrapped in AndroidView Composable',
                    desc: 'Wrapped in OSMMapView.kt with custom circular Polygon geofence overlay and real-time player markers.'
                  },
                  {
                    title: 'Local network P2P approach (UDP Discovery + TCP Sockets)',
                    desc: 'P2PNetworkManager implements UDP multicast on port 8888 for auto-discovery and TCP relay on port 9876.'
                  },
                  {
                    title: 'AndroidManifest.xml declares all required permissions',
                    desc: 'ACCESS_FINE_LOCATION, ACCESS_BACKGROUND_LOCATION, FOREGROUND_SERVICE_LOCATION, WAKE_LOCK, INTERNET.'
                  },
                  {
                    title: 'Foreground Service keeps GPS alive',
                    desc: 'ManhuntLocationService runs in foreground with ongoing notification showing time remaining.'
                  },
                  {
                    title: 'Self-contained on-device with Room SQLite (No Firebase / No external backend)',
                    desc: 'AppDatabase, PlayerProfileDao, and MatchHistoryDao handle on-device persistence.'
                  },
                  {
                    title: 'Project exported as ZIP with Android Studio guide',
                    desc: 'manhunt-android-app.zip packaged and ready for direct download and compilation.'
                  }
                ].map((item, index) => (
                  <div key={index} className="flex items-start gap-3 p-3.5 bg-[#F8F9FA] rounded-xl border border-[#E5E5EA]">
                    <CheckCircle2 className="w-5 h-5 text-[#34C759] shrink-0 mt-0.5" />
                    <div>
                      <p className="text-xs font-bold text-[#1C1C1E]">{item.title}</p>
                      <p className="text-[11px] text-[#636366]">{item.desc}</p>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
