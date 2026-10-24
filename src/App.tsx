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
  ExternalLink,
  Sun,
  Moon,
  Battery,
  BatteryCharging,
  Zap,
  Info,
  Sliders,
  Settings,
  User,
  ShieldAlert,
  BellRing
} from 'lucide-react';
import confetti from 'canvas-confetti';
import { ANDROID_FILES, ProjectFile } from './data/androidFiles';
import { calculateBearing, calculateDistanceMeters, evaluateGeofence, GeofenceStatus } from './logic/gameRules';

export default function App() {
  const [activeTab, setActiveTab] = useState<'simulator' | 'code' | 'guide' | 'checklist'>('simulator');
  const [selectedFile, setSelectedFile] = useState<ProjectFile>(ANDROID_FILES[0]);
  const [copied, setCopied] = useState(false);
  const [isDownloading, setIsDownloading] = useState(false);

  // Global Theme: Apple Light vs OLED Stealth Dark Mode
  const [isDarkMode, setIsDarkMode] = useState(false);

  // Battery Optimization State (Simulating OEM Whitelist)
  const [batteryExempt, setBatteryExempt] = useState(true);
  const [showBatteryDialog, setShowBatteryDialog] = useState(false);
  const [showOemGuide, setShowOemGuide] = useState(false);

  // Simulator State
  const [simRole, setSimRole] = useState<'SEEKER' | 'HIDER'>('HIDER');
  const [simScreen, setSimScreen] = useState<'HOME' | 'CREATE' | 'LOBBY' | 'GAME' | 'RESULTS' | 'PROFILE'>('GAME');
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

  // Network status simulation
  const [networkOnline, setNetworkOnline] = useState(true);

  // Geofence Evaluation & Grace Timer
  const geofence = evaluateGeofence(playerCoord, centerCoord, zoneRadius, 25);
  const [graceCountdown, setGraceCountdown] = useState(30);

  // Grace timer countdown when out of bounds
  useEffect(() => {
    if (simScreen !== 'GAME' || isPaused) return;

    if (geofence.status === 'OUT_OF_BOUNDS') {
      const timer = setInterval(() => {
        setGraceCountdown((prev) => {
          if (prev <= 1) return 0;
          return prev - 1;
        });
      }, 1000);
      return () => clearInterval(timer);
    } else {
      setGraceCountdown(30);
    }
  }, [geofence.status, simScreen, isPaused]);

  // Derived calculations
  const distanceToSeeker = Math.round(calculateDistanceMeters(playerCoord, targetSeekerCoord));
  const bearingToSeeker = Math.round(calculateBearing(playerCoord, targetSeekerCoord));
  const relativeAngle = ((bearingToSeeker - phoneHeading + 540) % 360) - 180;
  const canCatch = distanceToSeeker <= 10;
  const isBreached = geofence.status === 'OUT_OF_BOUNDS' && graceCountdown === 0;

  // Countdown match timer in simulator
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

  // Move player along latitude to simulate distance from center
  const setPlayerDistanceMeters = (distanceMeters: number) => {
    const deltaLat = distanceMeters / 111000;
    setPlayerCoord({
      latitude: centerCoord.latitude + deltaLat,
      longitude: centerCoord.longitude
    });
  };

  return (
    <div
      className={`min-h-screen flex flex-col font-sans transition-colors duration-200 ${
        isDarkMode
          ? 'bg-[#0B0C0E] text-[#F2F2F7] selection:bg-[#0A84FF]/30'
          : 'bg-[#F2F2F7] text-[#1C1C1E] selection:bg-[#007AFF]/20'
      }`}
    >
      {/* Top Navigation Bar */}
      <header
        className={`sticky top-0 z-50 px-6 py-3.5 flex flex-wrap items-center justify-between gap-4 border-b backdrop-blur-md transition-colors ${
          isDarkMode
            ? 'bg-[#121316]/90 border-[#22242B]'
            : 'bg-white/85 border-[#E5E5EA]'
        }`}
      >
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-[#007AFF] text-white flex items-center justify-center font-black shadow-sm tracking-tighter">
            MH
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h1 className={`font-black text-lg tracking-tight ${isDarkMode ? 'text-white' : 'text-[#1C1C1E]'}`}>
                MANHNT
              </h1>
              <span
                className={`text-[11px] font-semibold px-2 py-0.5 rounded-full uppercase tracking-wider ${
                  isDarkMode ? 'bg-[#22242B] text-[#8E8E93]' : 'bg-[#E5E5EA] text-[#636366]'
                }`}
              >
                Native Android & Jetpack Compose
              </span>
            </div>
            <p className={`text-xs ${isDarkMode ? 'text-[#8E8E93]' : 'text-[#8E8E93]'}`}>
              Long-Range GPS Chase • Firebase Realtime DB REST • OSMDroid • Room SQLite • Cellular 5G/4G
            </p>
          </div>
        </div>

        {/* Center Mode Tabs */}
        <div
          className={`flex items-center p-1 rounded-xl gap-1 border ${
            isDarkMode ? 'bg-[#18191E] border-[#2A2C35]' : 'bg-[#F2F2F7] border-[#E5E5EA]'
          }`}
        >
          <button
            onClick={() => setActiveTab('simulator')}
            className={`flex items-center gap-2 px-3.5 py-1.5 text-xs font-semibold rounded-lg transition-all ${
              activeTab === 'simulator'
                ? isDarkMode
                  ? 'bg-[#242630] text-[#0A84FF] shadow-sm'
                  : 'bg-white text-[#007AFF] shadow-sm'
                : isDarkMode
                ? 'text-[#8E8E93] hover:text-white'
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
                ? isDarkMode
                  ? 'bg-[#242630] text-[#0A84FF] shadow-sm'
                  : 'bg-white text-[#007AFF] shadow-sm'
                : isDarkMode
                ? 'text-[#8E8E93] hover:text-white'
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
                ? isDarkMode
                  ? 'bg-[#242630] text-[#0A84FF] shadow-sm'
                  : 'bg-white text-[#007AFF] shadow-sm'
                : isDarkMode
                ? 'text-[#8E8E93] hover:text-white'
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
                ? isDarkMode
                  ? 'bg-[#242630] text-[#0A84FF] shadow-sm'
                  : 'bg-white text-[#007AFF] shadow-sm'
                : isDarkMode
                ? 'text-[#8E8E93] hover:text-white'
                : 'text-[#636366] hover:text-[#1C1C1E]'
            }`}
          >
            <CheckCircle2 className="w-3.5 h-3.5" />
            Deliverable Checklist (10)
          </button>
        </div>

        {/* Right Actions: Dark Mode Toggle & Download ZIP */}
        <div className="flex items-center gap-2.5">
          <button
            onClick={() => setIsDarkMode(!isDarkMode)}
            className={`flex items-center gap-1.5 px-3 py-2 rounded-xl text-xs font-semibold border transition-all ${
              isDarkMode
                ? 'bg-[#1E2028] border-[#2E313C] text-[#FFD60A] hover:bg-[#252834]'
                : 'bg-white border-[#E5E5EA] text-[#636366] hover:bg-[#F2F2F7] hover:text-[#1C1C1E]'
            }`}
            title="Toggle between Clean Apple Light & OLED Stealth Dark Theme"
          >
            {isDarkMode ? <Sun className="w-4 h-4 text-[#FFD60A]" /> : <Moon className="w-4 h-4 text-[#636366]" />}
            <span className="hidden sm:inline">{isDarkMode ? 'OLED Dark' : 'Light Theme'}</span>
          </button>

          <button
            onClick={handleDownloadZip}
            disabled={isDownloading}
            className="flex items-center gap-2 bg-[#007AFF] hover:bg-[#0066D6] active:scale-95 text-white font-medium text-xs px-4 py-2 rounded-xl shadow-sm transition-all"
          >
            <Download className="w-4 h-4" />
            <span>{isDownloading ? 'Archiving...' : 'Export Android Project (.ZIP)'}</span>
            <span className="bg-white/20 text-[10px] px-1.5 py-0.5 rounded font-mono">52 KB</span>
          </button>
        </div>
      </header>

      {/* Main Content Area */}
      <main className="flex-1 max-w-7xl mx-auto w-full p-6">
        {activeTab === 'simulator' && (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
            {/* Left Controls & Inspection Panel */}
            <div className="lg:col-span-5 space-y-5">
              {/* Simulator Card */}
              <div
                className={`rounded-2xl p-5 border shadow-sm space-y-4 transition-colors ${
                  isDarkMode ? 'bg-[#14151A] border-[#22242B]' : 'bg-white border-[#E5E5EA]'
                }`}
              >
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-[#8E8E93] uppercase tracking-wider">
                    Interactive Android Preview
                  </span>
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => setNetworkOnline(!networkOnline)}
                      className={`flex items-center gap-1.5 text-xs font-medium px-2.5 py-0.5 rounded-full transition-all ${
                        networkOnline
                          ? 'text-[#34C759] bg-[#34C759]/10 hover:bg-[#34C759]/20'
                          : 'text-[#FF3B30] bg-[#FF3B30]/10 hover:bg-[#FF3B30]/20'
                      }`}
                      title="Click to simulate network disconnection"
                    >
                      <span
                        className={`w-1.5 h-1.5 rounded-full ${
                          networkOnline ? 'bg-[#34C759] animate-pulse' : 'bg-[#FF3B30]'
                        }`}
                      ></span>
                      {networkOnline ? '5G Cloud Sync' : 'Offline'}
                    </button>
                  </div>
                </div>

                <h2 className={`text-xl font-bold tracking-tight ${isDarkMode ? 'text-white' : 'text-[#1C1C1E]'}`}>
                  Test Game Engine & Real-Time Sensors
                </h2>
                <p className={`text-xs leading-relaxed ${isDarkMode ? 'text-[#8E8E93]' : 'text-[#636366]'}`}>
                  Simulate GPS boundaries, 25m perimeter buffer warnings, 30s return grace countdown, proximity catch
                  confirmation, and battery optimization protection.
                </p>

                {/* Role Switcher */}
                <div
                  className={`grid grid-cols-2 gap-2 p-1.5 rounded-xl ${
                    isDarkMode ? 'bg-[#1C1E26]' : 'bg-[#F2F2F7]'
                  }`}
                >
                  <button
                    onClick={() => setSimRole('HIDER')}
                    className={`py-2 px-3 rounded-lg text-xs font-bold transition-all flex items-center justify-center gap-2 ${
                      simRole === 'HIDER'
                        ? isDarkMode
                          ? 'bg-[#2A2C37] text-[#30D158] shadow-sm'
                          : 'bg-white text-[#34C759] shadow-sm'
                        : 'text-[#8E8E93] hover:text-white'
                    }`}
                  >
                    <span>🏃</span> Hider View
                  </button>
                  <button
                    onClick={() => setSimRole('SEEKER')}
                    className={`py-2 px-3 rounded-lg text-xs font-bold transition-all flex items-center justify-center gap-2 ${
                      simRole === 'SEEKER'
                        ? isDarkMode
                          ? 'bg-[#2A2C37] text-[#FF9F0A] shadow-sm'
                          : 'bg-white text-[#FF9500] shadow-sm'
                        : 'text-[#8E8E93] hover:text-white'
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
                  <div className="grid grid-cols-5 gap-1.5">
                    {(['HOME', 'CREATE', 'LOBBY', 'GAME', 'PROFILE'] as const).map((screen) => (
                      <button
                        key={screen}
                        onClick={() => setSimScreen(screen)}
                        className={`py-1.5 text-xs font-semibold rounded-lg border transition-all ${
                          simScreen === screen
                            ? 'bg-[#007AFF] text-white border-[#007AFF]'
                            : isDarkMode
                            ? 'bg-[#1C1E26] text-[#8E8E93] border-[#2A2C35] hover:bg-[#252834]'
                            : 'bg-white text-[#636366] border-[#E5E5EA] hover:bg-[#F2F2F7]'
                        }`}
                      >
                        {screen}
                      </button>
                    ))}
                  </div>
                </div>

                {/* Geofencing Engine Simulator */}
                <div
                  className={`p-3.5 rounded-xl border space-y-3 ${
                    isDarkMode ? 'bg-[#18191E] border-[#2A2C35]' : 'bg-[#F8F9FA] border-[#E5E5EA]'
                  }`}
                >
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-1.5">
                      <ShieldAlert className="w-4 h-4 text-[#007AFF]" />
                      <span className="text-xs font-bold">Geofence Status:</span>
                    </div>
                    <span
                      className={`text-[10px] font-extrabold px-2 py-0.5 rounded-full ${
                        isBreached
                          ? 'bg-[#FF3B30] text-white animate-pulse'
                          : geofence.status === 'OUT_OF_BOUNDS'
                          ? 'bg-[#FF3B30]/20 text-[#FF3B30]'
                          : geofence.status === 'BOUNDARY_ALERT'
                          ? 'bg-[#FF9500]/20 text-[#FF9500]'
                          : 'bg-[#34C759]/20 text-[#34C759]'
                      }`}
                    >
                      {isBreached
                        ? 'BREACH PENALTY'
                        : geofence.status === 'OUT_OF_BOUNDS'
                        ? `OUT OF BOUNDS (${graceCountdown}s)`
                        : geofence.status === 'BOUNDARY_ALERT'
                        ? `BUFFER ALERT (${Math.round(geofence.distanceToBoundary)}m to edge)`
                        : 'INSIDE (SAFE)'}
                    </span>
                  </div>

                  {/* Geofence Preset Buttons */}
                  <div className="grid grid-cols-3 gap-1.5 text-[11px]">
                    <button
                      onClick={() => setPlayerDistanceMeters(100)}
                      className={`py-1.5 px-2 rounded-lg font-medium border text-center transition-all ${
                        geofence.distanceFromCenter < zoneRadius - 25
                          ? 'bg-[#34C759] text-white border-[#34C759]'
                          : isDarkMode
                          ? 'bg-[#1C1E26] text-[#8E8E93] border-[#2A2C35]'
                          : 'bg-white text-[#636366] border-[#E5E5EA]'
                      }`}
                    >
                      Center (100m)
                    </button>
                    <button
                      onClick={() => setPlayerDistanceMeters(zoneRadius - 12)}
                      className={`py-1.5 px-2 rounded-lg font-medium border text-center transition-all ${
                        geofence.status === 'BOUNDARY_ALERT'
                          ? 'bg-[#FF9500] text-white border-[#FF9500]'
                          : isDarkMode
                          ? 'bg-[#1C1E26] text-[#8E8E93] border-[#2A2C35]'
                          : 'bg-white text-[#636366] border-[#E5E5EA]'
                      }`}
                    >
                      Buffer (488m)
                    </button>
                    <button
                      onClick={() => setPlayerDistanceMeters(zoneRadius + 45)}
                      className={`py-1.5 px-2 rounded-lg font-medium border text-center transition-all ${
                        geofence.status === 'OUT_OF_BOUNDS'
                          ? 'bg-[#FF3B30] text-white border-[#FF3B30]'
                          : isDarkMode
                          ? 'bg-[#1C1E26] text-[#8E8E93] border-[#2A2C35]'
                          : 'bg-white text-[#636366] border-[#E5E5EA]'
                      }`}
                    >
                      Out-of-Bounds (545m)
                    </button>
                  </div>
                </div>

                {/* Battery Optimization Whitelist Simulator */}
                <div
                  className={`p-3.5 rounded-xl border space-y-2.5 ${
                    isDarkMode ? 'bg-[#18191E] border-[#2A2C35]' : 'bg-[#F8F9FA] border-[#E5E5EA]'
                  }`}
                >
                  <div className="flex items-center justify-between text-xs">
                    <div className="flex items-center gap-1.5 font-bold">
                      <Zap className={`w-3.5 h-3.5 ${batteryExempt ? 'text-[#34C759]' : 'text-[#FF9500]'}`} />
                      <span>Battery Whitelist (OEM Protection):</span>
                    </div>
                    <span
                      className={`text-[10px] font-bold px-2 py-0.5 rounded-full ${
                        batteryExempt ? 'bg-[#34C759]/15 text-[#34C759]' : 'bg-[#FF9500]/15 text-[#FF9500]'
                      }`}
                    >
                      {batteryExempt ? 'Exempt (Safe)' : 'Restricted (Doze Risk)'}
                    </span>
                  </div>

                  <p className="text-[11px] text-[#8E8E93] leading-tight">
                    On Xiaomi (MIUI/HyperOS), Samsung (OneUI), and Oppo, aggressive battery managers kill GPS background
                    services unless whitelisted.
                  </p>

                  <div className="flex items-center gap-2 pt-1">
                    <button
                      onClick={() => setBatteryExempt(!batteryExempt)}
                      className={`flex-1 py-1.5 text-xs font-semibold rounded-lg border transition-all ${
                        batteryExempt
                          ? isDarkMode
                            ? 'bg-[#1C1E26] border-[#2A2C35] text-[#8E8E93]'
                            : 'bg-white border-[#E5E5EA] text-[#636366]'
                          : 'bg-[#FF9500] text-white border-[#FF9500]'
                      }`}
                    >
                      {batteryExempt ? 'Simulate Restrict Battery' : 'Grant Whitelist Exemption'}
                    </button>
                    <button
                      onClick={() => setShowOemGuide(!showOemGuide)}
                      className={`px-2.5 py-1.5 text-xs font-semibold rounded-lg border flex items-center gap-1 ${
                        isDarkMode
                          ? 'bg-[#1C1E26] border-[#2A2C35] text-[#8E8E93]'
                          : 'bg-white border-[#E5E5EA] text-[#636366]'
                      }`}
                    >
                      <Info className="w-3.5 h-3.5" />
                      OEM Guide
                    </button>
                  </div>

                  {showOemGuide && (
                    <div
                      className={`p-3 rounded-lg text-[10px] space-y-1.5 border mt-2 ${
                        isDarkMode ? 'bg-[#121316] border-[#22242B] text-[#A0A0A5]' : 'bg-white border-[#E5E5EA] text-[#636366]'
                      }`}
                    >
                      <p className="font-bold text-[#007AFF]">OEM Fallback Intents in BatteryOptimizationHelper.kt:</p>
                      <ul className="space-y-1 list-disc pl-3">
                        <li><strong>Xiaomi / Redmi</strong>: Autostart Permission & No Restrictions Battery Saver.</li>
                        <li><strong>Samsung</strong>: Never Sleeping Apps (`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`).</li>
                        <li><strong>Oppo / OnePlus</strong>: Background Execution Authorization (`com.coloros.safecenter`).</li>
                      </ul>
                    </div>
                  )}
                </div>

                {/* Dynamic Proximity Simulator */}
                <div
                  className={`pt-3 border-t space-y-3 ${
                    isDarkMode ? 'border-[#22242B]' : 'border-[#E5E5EA]'
                  }`}
                >
                  <div className="flex items-center justify-between text-xs">
                    <span className="font-semibold">GPS Distance to Seeker:</span>
                    <span className="font-mono font-bold text-[#007AFF]">{distanceToSeeker} meters</span>
                  </div>
                  <input
                    type="range"
                    min="3"
                    max="150"
                    value={distanceToSeeker}
                    onChange={(e) => {
                      const newDist = Number(e.target.value);
                      const deltaLat = newDist / 111000;
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
                    <span className="font-semibold">Device Heading (Sensor):</span>
                    <span className="font-mono font-bold text-[#8E8E93]">{phoneHeading}°</span>
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
              </div>

              {/* Technical Specifications Callout */}
              <div
                className={`rounded-2xl p-4 border shadow-sm text-xs space-y-2.5 transition-colors ${
                  isDarkMode ? 'bg-[#14151A] border-[#22242B]' : 'bg-white border-[#E5E5EA]'
                }`}
              >
                <div className="flex items-center gap-2 font-bold">
                  <Shield className="w-4 h-4 text-[#007AFF]" />
                  <span>Verified Native Android Kotlin Architecture</span>
                </div>
                <ul className="space-y-1.5 text-[#8E8E93]">
                  <li className="flex items-start gap-2">
                    <Check className="w-3.5 h-3.5 text-[#34C759] shrink-0 mt-0.5" />
                    <span><strong>Dynamic Light & OLED Dark Theme</strong>: Implemented in <code>Theme.kt</code> and <code>Color.kt</code> with instant toggle.</span>
                  </li>
                  <li className="flex items-start gap-2">
                    <Check className="w-3.5 h-3.5 text-[#34C759] shrink-0 mt-0.5" />
                    <span><strong>Battery Optimization Whitelist</strong>: <code>BatteryOptimizationHelper.kt</code> prevents OS background kills during multi-hour chases.</span>
                  </li>
                  <li className="flex items-start gap-2">
                    <Check className="w-3.5 h-3.5 text-[#34C759] shrink-0 mt-0.5" />
                    <span><strong>Geofencing Engine</strong>: <code>GeofenceManager.kt</code> evaluates 25m boundary buffer warning and 30s return grace period.</span>
                  </li>
                  <li className="flex items-start gap-2">
                    <Check className="w-3.5 h-3.5 text-[#34C759] shrink-0 mt-0.5" />
                    <span><strong>Long-Range Cellular Sync</strong>: Firebase Realtime Database REST API via Retrofit 2 & OkHttp 3.</span>
                  </li>
                </ul>
              </div>
            </div>

            {/* Right: Phone Frame with Pixel Aspect Ratio & Apple-on-Android Compose UI */}
            <div className="lg:col-span-7 flex justify-center">
              <div
                className={`w-[350px] h-[680px] rounded-[44px] p-2.5 shadow-2xl border-4 relative overflow-hidden flex flex-col transition-colors ${
                  isDarkMode
                    ? 'bg-[#000000] border-[#2A2C35]'
                    : 'bg-[#1C1C1E] border-[#3A3A3C]'
                }`}
              >
                {/* Speaker & Camera Pill */}
                <div className="absolute top-3.5 left-1/2 -translate-x-1/2 w-24 h-3.5 bg-black rounded-full z-50 flex items-center justify-center">
                  <div className="w-2.5 h-2.5 rounded-full bg-[#1C1C1E] mr-2"></div>
                  <div className="w-1.5 h-1.5 rounded-full bg-[#0A84FF]/40"></div>
                </div>

                {/* Android Screen Canvas */}
                <div
                  className={`w-full h-full rounded-[34px] overflow-hidden flex flex-col relative select-none transition-colors ${
                    isDarkMode ? 'bg-[#000000] text-white' : 'bg-[#F8F9FA] text-[#1C1C1E]'
                  }`}
                >
                  {/* Android Status Bar */}
                  <div
                    className={`pt-2 px-5 pb-0.5 flex justify-between items-center text-[10px] font-bold z-40 bg-transparent ${
                      isDarkMode ? 'text-white' : 'text-[#1C1C1E]'
                    }`}
                  >
                    <span>9:41</span>
                    <div className="flex items-center gap-1.5">
                      <Wifi className="w-3 h-3" />
                      <div
                        className={`w-4 h-2 border rounded-sm p-[1px] flex items-center ${
                          isDarkMode ? 'border-white' : 'border-[#1C1C1E]'
                        }`}
                      >
                        <div
                          className={`w-full h-full rounded-xs ${
                            isDarkMode ? 'bg-white' : 'bg-[#1C1C1E]'
                          }`}
                        ></div>
                      </div>
                    </div>
                  </div>

                  {/* Battery Exemption Prompt Banner inside Android */}
                  {!batteryExempt && simScreen === 'GAME' && (
                    <div className="bg-[#FF9500] text-white px-3 py-1 text-[10px] font-bold flex items-center justify-between z-30">
                      <div className="flex items-center gap-1">
                        <Battery className="w-3 h-3" />
                        <span>Battery Saver Active: GPS may sleep</span>
                      </div>
                      <button
                        onClick={() => setShowBatteryDialog(true)}
                        className="underline text-[9px] font-extrabold uppercase bg-white/20 px-1.5 py-0.5 rounded"
                      >
                        Fix
                      </button>
                    </div>
                  )}

                  {/* SCREEN: HOME */}
                  {simScreen === 'HOME' && (
                    <div className="flex-1 p-5 flex flex-col justify-between overflow-y-auto">
                      <div className="space-y-4">
                        <div className="flex items-center justify-between pt-2">
                          <div>
                            <h2
                              className={`text-2xl font-black tracking-tight ${
                                isDarkMode ? 'text-white' : 'text-[#1C1C1E]'
                              }`}
                            >
                              MANHNT
                            </h2>
                            <p className="text-[12px] text-[#8E8E93]">Long-Range GPS Chase</p>
                          </div>
                          <button
                            onClick={() => setSimScreen('PROFILE')}
                            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-full shadow-sm border transition-colors ${
                              isDarkMode ? 'bg-[#1C1C1E] border-[#2C2E35]' : 'bg-white border-[#E5E5EA]'
                            }`}
                          >
                            <span className="text-base">🏃</span>
                            <span className={`text-xs font-semibold ${isDarkMode ? 'text-white' : 'text-[#1C1C1E]'}`}>
                              Profile
                            </span>
                          </button>
                        </div>

                        {/* Join with Code Card */}
                        <div
                          className={`rounded-2xl p-4 shadow-sm border space-y-3 ${
                            isDarkMode ? 'bg-[#1C1C1E] border-[#2C2E35]' : 'bg-white border-[#E5E5EA]'
                          }`}
                        >
                          <h3 className={`text-sm font-bold ${isDarkMode ? 'text-white' : 'text-[#1C1C1E]'}`}>
                            Join with Game Code
                          </h3>
                          <p className="text-xs text-[#8E8E93]">Enter the 6-character code from the organizer.</p>
                          <div
                            className={`border rounded-xl px-3 py-2 text-sm font-mono tracking-widest text-center text-[#007AFF] font-bold ${
                              isDarkMode ? 'bg-[#000000] border-[#2C2E35]' : 'bg-[#F8F9FA] border-[#E5E5EA]'
                            }`}
                          >
                            HUNT42
                          </div>
                          <button
                            onClick={() => setSimScreen('LOBBY')}
                            className="w-full py-2.5 bg-[#007AFF] text-white rounded-xl text-xs font-semibold shadow-sm active:scale-95 transition-transform"
                          >
                            Join Chase
                          </button>
                        </div>

                        {/* Organize Card */}
                        <div
                          className={`rounded-2xl p-4 shadow-sm border space-y-3 ${
                            isDarkMode ? 'bg-[#1C1C1E] border-[#2C2E35]' : 'bg-white border-[#E5E5EA]'
                          }`}
                        >
                          <h3 className={`text-sm font-bold ${isDarkMode ? 'text-white' : 'text-[#1C1C1E]'}`}>
                            Organize New Chase
                          </h3>
                          <p className="text-xs text-[#8E8E93]">Set boundary radius, duration, and invite players.</p>
                          <button
                            onClick={() => setSimScreen('CREATE')}
                            className={`w-full py-2.5 rounded-xl text-xs font-semibold shadow-sm active:scale-95 transition-transform ${
                              isDarkMode ? 'bg-white text-black' : 'bg-[#1C1C1E] text-white'
                            }`}
                          >
                            Create Room
                          </button>
                        </div>

                        {/* Theme and Battery Quick Access */}
                        <div
                          className={`p-3 rounded-2xl border flex items-center justify-between text-xs ${
                            isDarkMode ? 'bg-[#1C1C1E] border-[#2C2E35]' : 'bg-white border-[#E5E5EA]'
                          }`}
                        >
                          <div className="flex items-center gap-2">
                            {isDarkMode ? <Moon className="w-4 h-4 text-[#0A84FF]" /> : <Sun className="w-4 h-4 text-[#FF9500]" />}
                            <span className="font-semibold">{isDarkMode ? 'OLED Dark Mode' : 'Light Theme'}</span>
                          </div>
                          <button
                            onClick={() => setIsDarkMode(!isDarkMode)}
                            className="text-[11px] font-bold text-[#007AFF]"
                          >
                            Switch
                          </button>
                        </div>
                      </div>
                    </div>
                  )}

                  {/* SCREEN: PROFILE & SETTINGS */}
                  {simScreen === 'PROFILE' && (
                    <div className="flex-1 p-5 flex flex-col justify-between overflow-y-auto">
                      <div className="space-y-4">
                        <div className="flex items-center gap-2">
                          <button
                            onClick={() => setSimScreen('HOME')}
                            className="text-xs text-[#007AFF] font-semibold"
                          >
                            ← Back
                          </button>
                          <h2 className={`text-lg font-bold ${isDarkMode ? 'text-white' : 'text-[#1C1C1E]'}`}>
                            Profile & Preferences
                          </h2>
                        </div>

                        {/* User Identity Card */}
                        <div
                          className={`rounded-2xl p-4 border space-y-3 ${
                            isDarkMode ? 'bg-[#1C1C1E] border-[#2C2E35]' : 'bg-white border-[#E5E5EA]'
                          }`}
                        >
                          <div className="flex items-center gap-3">
                            <div className="w-12 h-12 rounded-2xl bg-[#007AFF] text-white flex items-center justify-center text-xl font-bold">
                              🏃
                            </div>
                            <div>
                              <p className={`font-bold text-sm ${isDarkMode ? 'text-white' : 'text-[#1C1C1E]'}`}>
                                Agent Alex
                              </p>
                              <p className="text-[10px] text-[#8E8E93] font-mono">UUID: a829-f021-99bc</p>
                            </div>
                          </div>
                        </div>

                        {/* OLED Stealth Theme Toggle */}
                        <div
                          className={`rounded-2xl p-4 border flex items-center justify-between ${
                            isDarkMode ? 'bg-[#1C1C1E] border-[#2C2E35]' : 'bg-white border-[#E5E5EA]'
                          }`}
                        >
                          <div className="space-y-0.5">
                            <span className={`text-xs font-bold block ${isDarkMode ? 'text-white' : 'text-[#1C1C1E]'}`}>
                              OLED Stealth Dark Mode
                            </span>
                            <span className="text-[11px] text-[#8E8E93] block">Saves battery and keeps stealth at night</span>
                          </div>
                          <button
                            onClick={() => setIsDarkMode(!isDarkMode)}
                            className={`w-12 h-7 rounded-full p-1 transition-colors ${
                              isDarkMode ? 'bg-[#007AFF]' : 'bg-[#E5E5EA]'
                            }`}
                          >
                            <div
                              className={`w-5 h-5 rounded-full bg-white transition-transform ${
                                isDarkMode ? 'translate-x-5' : 'translate-x-0'
                              }`}
                            ></div>
                          </button>
                        </div>

                        {/* Battery Optimization Card */}
                        <div
                          className={`rounded-2xl p-4 border space-y-3 ${
                            isDarkMode ? 'bg-[#1C1C1E] border-[#2C2E35]' : 'bg-white border-[#E5E5EA]'
                          }`}
                        >
                          <div className="flex items-center justify-between">
                            <div className="flex items-center gap-2">
                              <Zap className={`w-4 h-4 ${batteryExempt ? 'text-[#34C759]' : 'text-[#FF9500]'}`} />
                              <span className={`text-xs font-bold ${isDarkMode ? 'text-white' : 'text-[#1C1C1E]'}`}>
                                Background GPS Whitelist
                              </span>
                            </div>
                            <span
                              className={`text-[9px] font-bold px-2 py-0.5 rounded-full ${
                                batteryExempt ? 'bg-[#34C759]/15 text-[#34C759]' : 'bg-[#FF9500]/15 text-[#FF9500]'
                              }`}
                            >
                              {batteryExempt ? 'EXEMPT' : 'RESTRICTED'}
                            </span>
                          </div>
                          <p className="text-[11px] text-[#8E8E93]">
                            Prevents Android from freezing GPS updates when your phone is locked in your pocket.
                          </p>
                          <button
                            onClick={() => setShowBatteryDialog(true)}
                            className="w-full py-2 bg-[#007AFF] text-white rounded-xl text-xs font-semibold"
                          >
                            Configure OEM Whitelist
                          </button>
                        </div>
                      </div>

                      <button
                        onClick={() => setSimScreen('HOME')}
                        className="w-full py-2.5 bg-[#007AFF] text-white rounded-xl text-xs font-bold"
                      >
                        Save & Return
                      </button>
                    </div>
                  )}

                  {/* SCREEN: CREATE ROOM */}
                  {simScreen === 'CREATE' && (
                    <div className="flex-1 p-5 flex flex-col justify-between overflow-y-auto">
                      <div className="space-y-4">
                        <div className="flex items-center gap-2">
                          <button
                            onClick={() => setSimScreen('HOME')}
                            className="text-xs text-[#007AFF] font-semibold"
                          >
                            ← Back
                          </button>
                          <h2 className={`text-lg font-bold ${isDarkMode ? 'text-white' : 'text-[#1C1C1E]'}`}>
                            New Match
                          </h2>
                        </div>

                        <div
                          className={`rounded-2xl p-4 border space-y-2 ${
                            isDarkMode ? 'bg-[#1C1C1E] border-[#2C2E35]' : 'bg-white border-[#E5E5EA]'
                          }`}
                        >
                          <label className="text-xs font-semibold">Room Name</label>
                          <div
                            className={`border rounded-xl px-3 py-2 text-xs font-medium ${
                              isDarkMode ? 'bg-[#000000] border-[#2C2E35] text-white' : 'bg-[#F8F9FA] border-[#E5E5EA] text-[#1C1C1E]'
                            }`}
                          >
                            Sunset Park Chase
                          </div>
                        </div>

                        <div
                          className={`rounded-2xl p-4 border space-y-2 ${
                            isDarkMode ? 'bg-[#1C1C1E] border-[#2C2E35]' : 'bg-white border-[#E5E5EA]'
                          }`}
                        >
                          <div className="flex justify-between items-center text-xs font-semibold">
                            <span>Play Zone Boundary</span>
                            <span className="text-[#007AFF] font-bold">{zoneRadius}m radius</span>
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
                          <p className="text-[11px] text-[#8E8E93]">Circular zone with 25m boundary alert buffer.</p>
                        </div>

                        <div
                          className={`rounded-2xl p-4 border space-y-2 ${
                            isDarkMode ? 'bg-[#1C1C1E] border-[#2C2E35]' : 'bg-white border-[#E5E5EA]'
                          }`}
                        >
                          <span className="text-xs font-semibold">Match Duration</span>
                          <div className="grid grid-cols-4 gap-2">
                            {[15, 30, 45, 60].map((m) => (
                              <button
                                key={m}
                                className={`py-1.5 text-xs font-bold rounded-lg ${
                                  m === 30
                                    ? 'bg-[#007AFF] text-white'
                                    : isDarkMode
                                    ? 'bg-[#2C2E35] text-white'
                                    : 'bg-[#F2F2F7] text-[#1C1C1E]'
                                }`}
                              >
                                {m}m
                              </button>
                            ))}
                          </div>
                        </div>
                      </div>

                      <button
                        onClick={() => setSimScreen('LOBBY')}
                        className="w-full py-3 bg-[#007AFF] text-white rounded-xl text-xs font-bold shadow-sm active:scale-95 transition-transform"
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
                          <h2 className={`text-base font-bold ${isDarkMode ? 'text-white' : 'text-[#1C1C1E]'}`}>
                            Sunset Park Chase
                          </h2>
                          <button onClick={() => setSimScreen('HOME')} className="text-xs text-[#8E8E93]">
                            Leave
                          </button>
                        </div>

                        {/* Join Code Card */}
                        <div
                          className={`rounded-2xl p-4 text-center border shadow-sm space-y-1 ${
                            isDarkMode ? 'bg-[#1C1C1E] border-[#2C2E35]' : 'bg-white border-[#E5E5EA]'
                          }`}
                        >
                          <span className="text-[10px] font-bold text-[#8E8E93] tracking-widest uppercase">
                            CLOUD JOIN CODE
                          </span>
                          <p className="text-3xl font-black text-[#007AFF] tracking-widest">HUNT42</p>
                          <p className="text-[11px] text-[#8E8E93]">Long-range tracking via Firebase REST API</p>
                        </div>

                        {/* Cellular Data Required Notice */}
                        <div className="bg-[#FFFBEB] rounded-xl p-3 border border-[#FDE68A] flex items-start gap-2.5 text-left">
                          <AlertTriangle className="w-4 h-4 text-[#D97706] shrink-0 mt-0.5" />
                          <div className="text-[10px] leading-tight">
                            <span className="font-bold text-[#B45309] block">Cellular Data Required (5G / 4G / LTE)</span>
                            <span className="text-[#92400E]">All players must have mobile data enabled to track across miles.</span>
                          </div>
                        </div>

                        {/* Players */}
                        <div className="space-y-2">
                          <div className="flex justify-between text-xs font-bold">
                            <span>Players (4)</span>
                            <span className="text-[#8E8E93]">3 Seekers • 1 Hider</span>
                          </div>

                          <div className="space-y-1.5">
                            <div
                              className={`rounded-xl p-3 border flex items-center justify-between ${
                                isDarkMode ? 'bg-[#1C1C1E] border-[#2C2E35]' : 'bg-white border-[#E5E5EA]'
                              }`}
                            >
                              <div className="flex items-center gap-2">
                                <span className="text-lg">🏃</span>
                                <div>
                                  <p className="text-xs font-bold">Agent Alex (You)</p>
                                  <p className="text-[10px] text-[#007AFF]">Organizer</p>
                                </div>
                              </div>
                              <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-[#34C759]/15 text-[#34C759]">
                                HIDER
                              </span>
                            </div>

                            <div
                              className={`rounded-xl p-3 border flex items-center justify-between ${
                                isDarkMode ? 'bg-[#1C1C1E] border-[#2C2E35]' : 'bg-white border-[#E5E5EA]'
                              }`}
                            >
                              <div className="flex items-center gap-2">
                                <span className="text-lg">🎯</span>
                                <p className="text-xs font-bold">Marcus</p>
                              </div>
                              <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-[#FF9500]/15 text-[#FF9500]">
                                SEEKER
                              </span>
                            </div>

                            <div
                              className={`rounded-xl p-3 border flex items-center justify-between ${
                                isDarkMode ? 'bg-[#1C1C1E] border-[#2C2E35]' : 'bg-white border-[#E5E5EA]'
                              }`}
                            >
                              <div className="flex items-center gap-2">
                                <span className="text-lg">⚡</span>
                                <p className="text-xs font-bold">Elena</p>
                              </div>
                              <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-[#FF9500]/15 text-[#FF9500]">
                                SEEKER
                              </span>
                            </div>
                          </div>
                        </div>
                      </div>

                      <button
                        onClick={() => setSimScreen('GAME')}
                        className="w-full py-3 bg-[#007AFF] text-white rounded-xl text-xs font-bold shadow-sm active:scale-95 transition-transform"
                      >
                        Start MANHNT
                      </button>
                    </div>
                  )}

                  {/* SCREEN: ACTIVE GAME */}
                  {simScreen === 'GAME' && (
                    <div className="flex-1 flex flex-col relative overflow-hidden">
                      {/* Top HUD Card */}
                      <div className="p-3 z-20 space-y-1.5">
                        <div
                          className={`rounded-2xl p-3 shadow-md border flex items-center justify-between transition-colors ${
                            isDarkMode
                              ? 'bg-[#1C1C1E]/95 backdrop-blur-md border-[#2C2E35]'
                              : 'bg-white/95 backdrop-blur-md border-[#E5E5EA]'
                          }`}
                        >
                          <div>
                            <div className="flex items-center gap-1.5">
                              <span className="text-xs font-bold">Sunset Park</span>
                              <span
                                className={`text-[10px] font-extrabold px-2 py-0.5 rounded-full ${
                                  simRole === 'HIDER'
                                    ? 'bg-[#34C759]/15 text-[#30D158]'
                                    : 'bg-[#FF9500]/15 text-[#FF9F0A]'
                                }`}
                              >
                                {simRole}
                              </span>
                              <span
                                className={`w-2 h-2 rounded-full ${
                                  networkOnline ? 'bg-[#34C759]' : 'bg-[#FF3B30] animate-ping'
                                }`}
                                title={networkOnline ? 'Cellular Cloud Sync Online' : 'Network Disconnected'}
                              ></span>
                            </div>
                            <div className="text-[10px] text-[#8E8E93] flex items-center gap-1 mt-0.5">
                              <span>Zone: {zoneRadius}m</span>
                              <span>•</span>
                              <span>Edge: {Math.max(0, Math.round(geofence.distanceToBoundary))}m</span>
                            </div>
                          </div>

                          <div className="flex items-center gap-2">
                            <span
                              className={`text-xl font-black tracking-tight font-mono ${
                                remainingTime < 180 ? 'text-[#FF3B30]' : isDarkMode ? 'text-white' : 'text-[#1C1C1E]'
                              }`}
                            >
                              {formatTimer(remainingTime)}
                            </span>
                            <button
                              onClick={() => setIsPaused(!isPaused)}
                              className={`p-1.5 rounded-lg transition-colors ${
                                isDarkMode ? 'bg-[#2C2E35] hover:bg-[#3A3C44]' : 'bg-[#F2F2F7] hover:bg-[#E5E5EA]'
                              }`}
                              title="Organizer Safety Pause"
                            >
                              {isPaused ? <Play className="w-4 h-4 text-[#007AFF]" /> : <Pause className="w-4 h-4" />}
                            </button>
                          </div>
                        </div>

                        {/* Geofence Alert Banners */}
                        {isBreached ? (
                          <div className="bg-[#FF3B30] text-white text-[10px] font-bold py-1.5 px-3 rounded-xl text-center shadow-md animate-pulse">
                            🚨 BOUNDARY BREACH PENALTY! HIDER BEACON EXPOSED TO SEEKERS!
                          </div>
                        ) : geofence.status === 'OUT_OF_BOUNDS' ? (
                          <div className="bg-[#FF3B30] text-white text-[10px] font-bold py-1.5 px-3 rounded-xl text-center shadow-md flex items-center justify-between">
                            <span className="flex items-center gap-1">
                              <AlertTriangle className="w-3.5 h-3.5" />
                              <span>OUT OF BOUNDS!</span>
                            </span>
                            <span className="font-mono bg-black/30 px-1.5 py-0.5 rounded">
                              RETURN IN {graceCountdown}s
                            </span>
                          </div>
                        ) : geofence.status === 'BOUNDARY_ALERT' ? (
                          <div className="bg-[#FF9500] text-white text-[10px] font-bold py-1 px-3 rounded-xl text-center shadow-sm">
                            ⚠️ BUFFER ALERT: Approaching Perimeter Boundary ({Math.round(geofence.distanceToBoundary)}m left)
                          </div>
                        ) : null}

                        {/* Network Offline Alert */}
                        {!networkOnline && (
                          <div className="bg-[#FF3B30] text-white text-[10px] font-bold py-1 px-3 rounded-xl text-center shadow-sm">
                            ⚠️ CELLULAR SYNC DISCONNECTED — CHECK MOBILE DATA
                          </div>
                        )}

                        {/* Safety Pause Notice */}
                        {isPaused && (
                          <div className="bg-[#FF9500] text-white text-[11px] font-bold py-1 px-3 rounded-xl text-center shadow-sm">
                            SAFETY PAUSE ACTIVE — FREEZE POSITIONS
                          </div>
                        )}
                      </div>

                      {/* Map Area (Simulating OSMDroid MapView Canvas) */}
                      <div
                        className={`h-[240px] flex-none relative overflow-hidden transition-colors ${
                          isDarkMode ? 'bg-[#121316]' : 'bg-[#E9EBEF]'
                        }`}
                      >
                        {/* OpenStreetMap Grid & Park Styling */}
                        <div
                          className={`absolute inset-0 [background-size:16px_16px] opacity-60 ${
                            isDarkMode
                              ? 'bg-[radial-gradient(#2A2C35_1px,transparent_1px)]'
                              : 'bg-[radial-gradient(#CBD5E1_1px,transparent_1px)]'
                          }`}
                        ></div>

                        {/* Map Park Features (Visual Street Map styling) */}
                        <div
                          className={`absolute top-1/4 left-1/4 w-44 h-44 rounded-3xl opacity-60 ${
                            isDarkMode ? 'bg-[#1A2E26]' : 'bg-[#D1E7DD]'
                          }`}
                        ></div>
                        <div
                          className={`absolute top-1/3 right-4 w-28 h-52 rounded-xl opacity-40 ${
                            isDarkMode ? 'bg-[#1E2028]' : 'bg-[#E2E8F0]'
                          }`}
                        ></div>

                        {/* OSMDroid Circular Play Zone Boundary */}
                        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-64 h-64 rounded-full border-2 border-[#007AFF] bg-[#007AFF]/10 pointer-events-none flex items-center justify-center">
                          <span
                            className={`text-[9px] text-[#007AFF] font-mono font-semibold px-2 py-0.5 rounded-full mb-48 ${
                              isDarkMode ? 'bg-black/70' : 'bg-white/70'
                            }`}
                          >
                            {zoneRadius}m Play Zone
                          </span>
                        </div>

                        {/* 25m Boundary Buffer Inner Ring (Amber dashed) */}
                        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-56 h-56 rounded-full border-2 border-dashed border-[#FF9500]/60 pointer-events-none"></div>

                        {/* Player Marker (Blue Dot with pulsing radar) */}
                        <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 flex items-center justify-center z-10">
                          <div className="w-8 h-8 rounded-full bg-[#007AFF]/25 animate-ping absolute"></div>
                          <div className="w-5 h-5 rounded-full bg-[#007AFF] border-2 border-white shadow-md flex items-center justify-center text-white text-[9px] font-bold">
                            {simRole === 'HIDER' ? '🏃' : '🎯'}
                          </div>
                        </div>

                        {/* Targets on Map */}
                        {simRole === 'SEEKER' || isBreached ? (
                          /* Seeker sees Hiders as Orange Markers (Or exposed on breach) */
                          <div
                            style={{
                              transform: `translate(${relativeAngle > 0 ? 35 : -35}px, -${Math.max(
                                15,
                                Math.min(90, distanceToSeeker)
                              )}px)`
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
                          <div
                            className={`absolute bottom-2 left-3 backdrop-blur-xs px-2 py-1 rounded-md text-[10px] z-10 ${
                              isDarkMode ? 'bg-black/80 text-[#8E8E93]' : 'bg-white/80 text-[#8E8E93]'
                            }`}
                          >
                            🛡️ Stealth Active: Seekers hidden from map
                          </div>
                        )}
                      </div>

                      {/* Bottom Controls HUD */}
                      <div
                        className={`p-3 z-20 space-y-2 pt-2 pb-1 ${
                          isDarkMode
                            ? 'bg-gradient-to-t from-black via-black/95 to-transparent'
                            : 'bg-gradient-to-t from-[#F8F9FA] via-[#F8F9FA]/95 to-transparent'
                        }`}
                      >
                        {/* Hider View: Compass Strip pointing towards nearest Seeker */}
                        {simRole === 'HIDER' && (
                          <div className="space-y-1.5">
                            <div
                              className={`rounded-2xl p-2.5 shadow-md border flex items-center justify-between ${
                                isDarkMode ? 'bg-[#1C1C1E] border-[#2C2E35]' : 'bg-white border-[#E5E5EA]'
                              }`}
                            >
                              <div className="flex items-center gap-2.5">
                                <div
                                  className={`w-9 h-9 rounded-full flex items-center justify-center ${
                                    isDarkMode ? 'bg-[#2C2E35]' : 'bg-[#FFF7ED]'
                                  }`}
                                >
                                  <Compass
                                    style={{ transform: `rotate(${relativeAngle}deg)` }}
                                    className="w-5 h-5 text-[#FF9500] transition-transform duration-200"
                                  />
                                </div>
                                <div>
                                  <p className="text-[10px] font-medium text-[#8E8E93]">Nearest Seeker</p>
                                  <p className="text-xs font-bold">{distanceToSeeker}m away</p>
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
                            <div
                              className={`rounded-2xl p-2.5 border flex items-center justify-between ${
                                isDarkMode ? 'bg-[#1C1C1E] border-[#2C2E35]' : 'bg-white border-[#E5E5EA]'
                              }`}
                            >
                              <div>
                                <p className="text-[10px] font-bold text-[#8E8E93] uppercase">Your Catch Code</p>
                                <p className="text-[10px] text-[#8E8E93]">Give only if physically tagged</p>
                              </div>
                              <span
                                className={`text-lg font-mono font-black tracking-widest px-3 py-0.5 rounded-lg ${
                                  isDarkMode ? 'bg-[#000000] text-white' : 'bg-[#F2F2F7] text-[#1C1C1E]'
                                }`}
                              >
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
                              className={`w-full py-3 rounded-2xl font-bold text-xs shadow-md transition-all flex items-center justify-center gap-2 ${
                                canCatch
                                  ? 'bg-[#FF9500] text-white active:scale-95 hover:bg-[#E68600]'
                                  : 'bg-[#E5E5EA] text-[#8E8E93] cursor-not-allowed'
                              }`}
                            >
                              <span>⚡</span>
                              <span>
                                {canCatch ? 'Tag Alex (Within 10m!)' : `Catch Button (Too Far: ${distanceToSeeker}m)`}
                              </span>
                            </button>
                          </div>
                        )}

                        {/* Leave Game */}
                        <button
                          onClick={() => setSimScreen('HOME')}
                          className="w-full text-center text-[10px] text-[#8E8E93] hover:text-[#FF3B30] transition-colors py-0.5"
                        >
                          Leave Match
                        </button>
                      </div>

                      {/* Modal: Catch Bottom Sheet */}
                      {isCatchSheetOpen && (
                        <div className="absolute inset-0 bg-black/60 backdrop-blur-xs z-50 flex flex-col justify-end">
                          <div
                            className={`rounded-t-[32px] p-5 space-y-4 shadow-2xl animate-in slide-in-from-bottom duration-200 ${
                              isDarkMode ? 'bg-[#1C1C1E] text-white' : 'bg-white text-[#1C1C1E]'
                            }`}
                          >
                            <div className="w-10 h-1 bg-[#8E8E93]/40 rounded-full mx-auto"></div>
                            <div className="text-center">
                              <h3 className="text-base font-bold">Tag Alex!</h3>
                              <p className="text-xs text-[#8E8E93]">Enter the 4-digit code shown on Alex's phone.</p>
                            </div>

                            {/* 4 Digit Boxes */}
                            <div className="flex justify-center gap-2.5">
                              {[0, 1, 2, 3].map((i) => (
                                <div
                                  key={i}
                                  className={`w-11 h-12 rounded-xl border flex items-center justify-center text-lg font-bold font-mono ${
                                    isDarkMode
                                      ? 'bg-[#000000] border-[#2C2E35] text-white'
                                      : 'bg-[#F2F2F7] border-[#E5E5EA] text-[#1C1C1E]'
                                  }`}
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
                                  className={`h-10 rounded-xl text-sm font-semibold active:scale-95 transition-transform ${
                                    isDarkMode
                                      ? 'bg-[#2C2E35] text-white hover:bg-[#3A3C44]'
                                      : 'bg-[#F2F2F7] text-[#1C1C1E] hover:bg-[#E5E5EA]'
                                  }`}
                                >
                                  {key}
                                </button>
                              ))}
                            </div>

                            <div className="flex gap-2 pt-1">
                              <button
                                onClick={() => setIsCatchSheetOpen(false)}
                                className={`flex-1 py-2.5 rounded-xl text-xs font-semibold ${
                                  isDarkMode ? 'bg-[#2C2E35] text-white' : 'bg-[#F2F2F7] text-[#1C1C1E]'
                                }`}
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
                        <div
                          className={`rounded-2xl p-5 border text-center shadow-sm space-y-2 ${
                            isDarkMode ? 'bg-[#1C1C1E] border-[#2C2E35]' : 'bg-white border-[#E5E5EA]'
                          }`}
                        >
                          <span className="text-4xl block">🎯</span>
                          <h2 className="text-xl font-black">SEEKERS TRIUMPH!</h2>
                          <p className="text-xs text-[#8E8E93]">All hiders were tagged before the timer expired.</p>

                          <div
                            className={`grid grid-cols-3 gap-2 pt-3 border-t ${
                              isDarkMode ? 'border-[#2C2E35]' : 'border-[#E5E5EA]'
                            }`}
                          >
                            <div>
                              <p className="text-[10px] text-[#8E8E93]">Duration</p>
                              <p className="text-sm font-bold">18m</p>
                            </div>
                            <div>
                              <p className="text-[10px] text-[#8E8E93]">Catches</p>
                              <p className="text-sm font-bold">3</p>
                            </div>
                            <div>
                              <p className="text-[10px] text-[#8E8E93]">Radius</p>
                              <p className="text-sm font-bold">{zoneRadius}m</p>
                            </div>
                          </div>
                        </div>

                        <div className="space-y-2">
                          <span className="text-xs font-bold">Player Standings</span>
                          <div className="space-y-1.5">
                            <div
                              className={`rounded-xl p-3 border flex justify-between items-center text-xs ${
                                isDarkMode ? 'bg-[#1C1C1E] border-[#2C2E35]' : 'bg-white border-[#E5E5EA]'
                              }`}
                            >
                              <span className="font-semibold">🎯 Marcus (Seeker)</span>
                              <span className="text-[#34C759] font-bold">2 Catches (MVP)</span>
                            </div>
                            <div
                              className={`rounded-xl p-3 border flex justify-between items-center text-xs ${
                                isDarkMode ? 'bg-[#1C1C1E] border-[#2C2E35]' : 'bg-white border-[#E5E5EA]'
                              }`}
                            >
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
                    <div
                      className={`w-32 h-1 rounded-full ${
                        isDarkMode ? 'bg-white/30' : 'bg-[#1C1C1E]/30'
                      }`}
                    ></div>
                  </div>

                  {/* Simulated Android System Dialog for Battery Exemption */}
                  {showBatteryDialog && (
                    <div className="absolute inset-0 bg-black/60 backdrop-blur-xs z-50 flex items-center justify-center p-4">
                      <div
                        className={`rounded-2xl p-4 space-y-3 shadow-2xl max-w-[280px] w-full text-left ${
                          isDarkMode ? 'bg-[#1C1C1E] text-white' : 'bg-white text-[#1C1C1E]'
                        }`}
                      >
                        <div className="flex items-center gap-2">
                          <Zap className="w-5 h-5 text-[#007AFF]" />
                          <h4 className="text-xs font-bold">Ignore Battery Optimizations?</h4>
                        </div>
                        <p className="text-[11px] text-[#8E8E93] leading-relaxed">
                          Let <strong>MANHNT</strong> run in the background without battery restrictions? This ensures
                          GPS tracking and 5s cloud synchronization continue when your screen is locked.
                        </p>
                        <div className="flex justify-end gap-2 pt-1">
                          <button
                            onClick={() => setShowBatteryDialog(false)}
                            className="px-3 py-1.5 text-xs text-[#8E8E93] font-semibold"
                          >
                            Deny
                          </button>
                          <button
                            onClick={() => {
                              setBatteryExempt(true);
                              setShowBatteryDialog(false);
                            }}
                            className="px-3.5 py-1.5 bg-[#007AFF] text-white rounded-lg text-xs font-bold"
                          >
                            Allow
                          </button>
                        </div>
                      </div>
                    </div>
                  )}
                </div>
              </div>
            </div>
          </div>
        )}

        {/* CODE TAB */}
        {activeTab === 'code' && (
          <div
            className={`rounded-2xl border shadow-sm overflow-hidden flex flex-col h-[750px] transition-colors ${
              isDarkMode ? 'bg-[#14151A] border-[#22242B]' : 'bg-white border-[#E5E5EA]'
            }`}
          >
            {/* Header */}
            <div
              className={`px-5 py-3 border-b flex items-center justify-between ${
                isDarkMode ? 'bg-[#18191E] border-[#22242B]' : 'bg-[#F8F9FA] border-[#E5E5EA]'
              }`}
            >
              <div className="flex items-center gap-2">
                <Code2 className="w-4 h-4 text-[#007AFF]" />
                <span className="font-bold text-sm">Native Android Kotlin Project Structure</span>
                <span className="text-xs text-[#8E8E93]">({selectedFile.path})</span>
              </div>
              <div className="flex items-center gap-2">
                <button
                  onClick={handleCopyCode}
                  className={`flex items-center gap-1.5 text-xs font-medium px-3 py-1.5 rounded-lg border transition-colors ${
                    isDarkMode
                      ? 'bg-[#1C1E26] border-[#2C2E35] text-white hover:bg-[#252834]'
                      : 'bg-white border-[#E5E5EA] text-[#1C1C1E] hover:bg-[#F2F2F7]'
                  }`}
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
              <div
                className={`w-72 border-r p-3 overflow-y-auto space-y-1 ${
                  isDarkMode ? 'bg-[#14151A] border-[#22242B]' : 'bg-[#F8F9FA] border-[#E5E5EA]'
                }`}
              >
                <span className="text-[11px] font-bold text-[#8E8E93] uppercase tracking-wider px-2 block mb-2">
                  Source Files ({ANDROID_FILES.length})
                </span>
                {ANDROID_FILES.map((file) => (
                  <button
                    key={file.path}
                    onClick={() => setSelectedFile(file)}
                    className={`w-full text-left px-3 py-2 rounded-xl text-xs transition-all flex items-start gap-2 ${
                      selectedFile.path === file.path
                        ? 'bg-[#007AFF] text-white font-semibold shadow-xs'
                        : isDarkMode
                        ? 'text-[#8E8E93] hover:bg-[#1E2028] hover:text-white'
                        : 'text-[#636366] hover:bg-[#E5E5EA] hover:text-[#1C1C1E]'
                    }`}
                  >
                    <span className="font-mono text-[10px] opacity-80 mt-0.5">KT</span>
                    <div className="truncate flex-1">
                      <p className="truncate">{file.name}</p>
                      <p
                        className={`text-[10px] truncate ${
                          selectedFile.path === file.path ? 'text-white/80' : 'text-[#8E8E93]'
                        }`}
                      >
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
            <div
              className={`rounded-2xl p-6 border shadow-sm space-y-4 transition-colors ${
                isDarkMode ? 'bg-[#14151A] border-[#22242B]' : 'bg-white border-[#E5E5EA]'
              }`}
            >
              <h2 className="text-xl font-bold">
                MANHNT: Android Studio & Signed APK Deployment Guide
              </h2>
              <p className="text-xs text-[#8E8E93] leading-relaxed">
                The exported project is a production-grade Gradle Android Studio project using modern Kotlin 1.9.23,
                Jetpack Compose 2024.04.01, OSMDroid 6.1.18, Room 2.6.1, Retrofit 2, and Accompanist Permissions.
              </p>

              <div className="space-y-4 pt-2">
                <div
                  className={`flex gap-4 items-start p-4 rounded-xl border ${
                    isDarkMode ? 'bg-[#18191E] border-[#22242B]' : 'bg-[#F8F9FA] border-[#E5E5EA]'
                  }`}
                >
                  <span className="w-7 h-7 rounded-full bg-[#007AFF] text-white font-bold text-xs flex items-center justify-center shrink-0">
                    1
                  </span>
                  <div className="space-y-1">
                    <h3 className="text-sm font-bold">Download & Extract Project ZIP</h3>
                    <p className="text-xs text-[#8E8E93]">
                      Click <strong>"Export Android Project (.ZIP)"</strong> above. Extract <code>manhunt-android-app.zip</code> to your projects directory (e.g. <code>~/AndroidStudioProjects/MANHNT</code>).
                    </p>
                  </div>
                </div>

                <div
                  className={`flex gap-4 items-start p-4 rounded-xl border ${
                    isDarkMode ? 'bg-[#18191E] border-[#22242B]' : 'bg-[#F8F9FA] border-[#E5E5EA]'
                  }`}
                >
                  <span className="w-7 h-7 rounded-full bg-[#007AFF] text-white font-bold text-xs flex items-center justify-center shrink-0">
                    2
                  </span>
                  <div className="space-y-1">
                    <h3 className="text-sm font-bold">Open in Android Studio</h3>
                    <p className="text-xs text-[#8E8E93]">
                      Launch Android Studio (Hedgehog, Iguana, or Koala). Click <strong>File &gt; Open</strong> and choose the extracted root folder. Android Studio will execute Gradle Sync and resolve all dependencies automatically.
                    </p>
                  </div>
                </div>

                <div
                  className={`flex gap-4 items-start p-4 rounded-xl border ${
                    isDarkMode ? 'bg-[#18191E] border-[#22242B]' : 'bg-[#F8F9FA] border-[#E5E5EA]'
                  }`}
                >
                  <span className="w-7 h-7 rounded-full bg-[#007AFF] text-white font-bold text-xs flex items-center justify-center shrink-0">
                    3
                  </span>
                  <div className="space-y-1">
                    <h3 className="text-sm font-bold">Pre-Configured Firebase Cloud Sync</h3>
                    <p className="text-xs text-[#8E8E93]">
                      Your Firebase Realtime Database URL is pre-configured in <code>Constants.kt</code>:
                      <br />
                      <code className="text-[#007AFF] font-mono text-[11px]">
                        https://manhnt-ba00c-default-rtdb.asia-southeast1.firebasedatabase.app/
                      </code>
                      <br />
                      Ensure Realtime Database rules allow read and write access for rooms:
                      <br />
                      <code className="font-mono text-[10px] bg-black/30 px-1 py-0.5 rounded text-amber-300">
                        &#123; "rules": &#123; "rooms": &#123; ".read": true, ".write": true &#125; &#125; &#125;
                      </code>
                    </p>
                  </div>
                </div>

                <div
                  className={`flex gap-4 items-start p-4 rounded-xl border ${
                    isDarkMode ? 'bg-[#18191E] border-[#22242B]' : 'bg-[#F8F9FA] border-[#E5E5EA]'
                  }`}
                >
                  <span className="w-7 h-7 rounded-full bg-[#007AFF] text-white font-bold text-xs flex items-center justify-center shrink-0">
                    4
                  </span>
                  <div className="space-y-1">
                    <h3 className="text-sm font-bold">Battery Optimization & Doze Mode Whitelist</h3>
                    <p className="text-xs text-[#8E8E93]">
                      <code>BatteryOptimizationHelper.kt</code> includes OEM fallback intents for Xiaomi (MIUI/HyperOS Autostart), Samsung (OneUI Never Sleeping Apps), and Oppo (ColorOS Background Execution). Players should approve exemption when prompted to guarantee uninterrupted 5-second GPS updates with phone locked in pocket.
                    </p>
                  </div>
                </div>

                <div
                  className={`flex gap-4 items-start p-4 rounded-xl border ${
                    isDarkMode ? 'bg-[#18191E] border-[#22242B]' : 'bg-[#F8F9FA] border-[#E5E5EA]'
                  }`}
                >
                  <span className="w-7 h-7 rounded-full bg-[#007AFF] text-white font-bold text-xs flex items-center justify-center shrink-0">
                    5
                  </span>
                  <div className="space-y-1">
                    <h3 className="text-sm font-bold">Generate APK / Run on Physical Device</h3>
                    <p className="text-xs text-[#8E8E93]">
                      Select <strong>Build &gt; Build Bundle(s) / APK(s) &gt; Build APK(s)</strong> or <strong>Generate Signed Bundle / APK</strong>. The output APK will be placed at <code>app/build/outputs/apk/debug/app-debug.apk</code>.
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
            <div
              className={`rounded-2xl p-6 border shadow-sm space-y-4 transition-colors ${
                isDarkMode ? 'bg-[#14151A] border-[#22242B]' : 'bg-white border-[#E5E5EA]'
              }`}
            >
              <div className="flex items-center justify-between">
                <h2 className="text-lg font-bold">MANHNT: Production Verification Checklist</h2>
                <span className="text-xs font-bold text-[#34C759] bg-[#34C759]/15 px-2.5 py-1 rounded-full">
                  10 / 10 Confirmed
                </span>
              </div>

              <div className="space-y-3">
                {[
                  {
                    title: 'Native Kotlin Android project with Jetpack Compose UI',
                    desc: 'Single-activity architecture (MainActivity.kt), Compose Material3, zero XML layouts, StateFlow reactive state.'
                  },
                  {
                    title: 'Apple-Inspired Light & OLED Stealth Dark Theme',
                    desc: 'Implemented in Theme.kt and Color.kt with instant dynamic theme toggle, pure #000000 surfaces, and crisp #0A84FF accents.'
                  },
                  {
                    title: 'Battery Optimization Whitelist Helper (OEM Protection)',
                    desc: 'BatteryOptimizationHelper.kt requests ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS with Xiaomi, Samsung, and Oppo fallbacks.'
                  },
                  {
                    title: 'High-Accuracy Geofencing Engine & Grace Timer',
                    desc: 'GeofenceManager.kt evaluates circular perimeter with 25m boundary buffer warning and 30-second return grace countdown.'
                  },
                  {
                    title: 'Long-Range Cloud Sync via Firebase Realtime Database REST API',
                    desc: 'FirebaseSyncService handles room creation (PUT), joining (GET), and player coordinate patching (PATCH) via cellular 5G/4G.'
                  },
                  {
                    title: 'Background GPS Foreground Service (5s updates)',
                    desc: 'ManhuntLocationService uses FusedLocationProviderClient and pushes coordinates even when phone is locked or screen off.'
                  },
                  {
                    title: 'OSMDroid MapView with Boundary & Buffer Polygons',
                    desc: 'OSMMapView.kt wraps OSMDroid in Compose AndroidView with circular boundary overlay and 25m warning buffer ring.'
                  },
                  {
                    title: 'Asymmetric Roles & Compass Heading Sensor',
                    desc: 'Seekers see fleeing Hiders; Hiders have stealth map with Apple-style compass needle pointing to nearest Seeker.'
                  },
                  {
                    title: 'Physical Tag & 4-Digit Catch Code Verification',
                    desc: 'Hider displays 4-digit PIN; Seeker must be within 10 meters and enter correct code to register catch.'
                  },
                  {
                    title: 'Complete Gradle Project packaged in ZIP with GitHub README',
                    desc: 'manhunt-android-app.zip contains complete Android Studio Gradle source tree, Constants.kt, and README.md.'
                  }
                ].map((item, index) => (
                  <div
                    key={index}
                    className={`flex items-start gap-3 p-3.5 rounded-xl border ${
                      isDarkMode ? 'bg-[#18191E] border-[#22242B]' : 'bg-[#F8F9FA] border-[#E5E5EA]'
                    }`}
                  >
                    <CheckCircle2 className="w-5 h-5 text-[#34C759] shrink-0 mt-0.5" />
                    <div>
                      <p className="text-xs font-bold">{item.title}</p>
                      <p className="text-[11px] text-[#8E8E93]">{item.desc}</p>
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
