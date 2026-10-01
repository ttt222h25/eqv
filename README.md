# EQV: music visualizer overlay for Nothing Phone (3)

A system-wide, click-through music visualizer that runs on top of any app (Spotify, YouTube, anything), in the spirit of Muviz Edge. It has a Nothing-style settings app (black, white, one red accent, dot-matrix headings) with a live preview for every control.

- **Layers** (combine freely, independent settings): edge lighting that follows the real rounded corners and punch-hole, EQ bars (bottom/top/both/sides), radial spectrum, waveform, and beat pulse (vignette/flash/ring).
- **Beat haptics**: composition primitives (THUD/CLICK/LOW_TICK), with waveform fallbacks.
- **Thump**: a fake screen shake (scale/offset pulse + chromatic edge flash + haptic). The in-app preview can shake for real.
- **Smart behavior**: auto start/stop with music, which players trigger it, hide in fullscreen or in chosen apps, pause on screen-off/calls/low battery, album-art colors, Quick Settings tiles (toggle and preset), and a notification with on/off + next preset.
- **Audio**: Visualizer API → microphone fallback chain (with a silence watchdog), optional HQ playback capture, and a demo signal.
- **DSP**: Hann-windowed FFT (1024/2048, 50% overlap), log bands (8–64), fast-attack/slow-decay smoothing, peak hold, auto-gain, spectral-flux beats with cooldown, and an A/V sync delay per output route.
- **Presets**: 5 built-in presets; save/rename/duplicate/delete; import/export JSON (file or clipboard).

See [PLAN.md](PLAN.md) for the research and architecture, and [IDEAS.md](IDEAS.md) for the backlog.

## Install (phone only, no computer needed)

Every push is built by GitHub Actions, and the newest APK is always at:

**https://github.com/ttt222h25/eqv/releases/download/latest/eqv.apk**

1. Open that link in Chrome on the phone and download `eqv.apk`.
2. Open it. If asked, allow **Install unknown apps** for Chrome. If Play Protect warns about an unknown developer, tap **More details → Install anyway** (it's a debug build signed with the repo's debug key).
3. If Play Protect says **"App blocked to protect your device"** (it blocks browser-installed apps that request notification access): Play Store → profile → Play Protect → ⚙ → turn off **Scan apps with Play Protect**, install, then turn it back on.
4. Open **EQV → Setup** and grant each item. If Android says **"App was denied access"** / **"Restricted setting"**: App info → ⋮ → **Allow restricted settings** → confirm, then try again (needed once for both the overlay and notification access):
   - Display over other apps
   - Microphone (required by Android for the system visualizer; nothing is recorded)
   - Notifications
   - Notification access
5. Optional: Usage access (for "hide in these apps") and Battery → Unrestricted (keeps auto-start instant).
6. Quick Settings → edit (pencil) → add the **EQV** and **EQV preset** tiles.

Updates install over the previous build: all builds share one signing key and increasing version codes.

## Build locally (if you ever have a computer)

Requirements: JDK 17+ and the Android SDK (platform 36). Android Studio installs both.

```bash
./gradlew testDebugUnitTest      # unit tests (FFT, band mapping, smoothing, beat detector, presets)
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Wireless adb (Android 11+): Developer options → Wireless debugging → Pair device with pairing code, then `adb pair IP:PORT` and `adb connect IP:PORT`.

## Project layout

```
app/src/main/java/com/eqv/visualizer/
  audio/dsp/     Fft, BandMapper, BandSmoother/AutoGain, BeatDetector, SpectrumPipeline,
                 AnalysisFrame + FrameRing (lock-protected ring, A/V delay), SyntheticMusic
  audio/source/  VisualizerSource, RecordSource (mic / playback capture), DemoSource
  audio/         AudioEngine (thread, fallback chain, silence watchdog)
  render/        VisualizerView (overlay + preview), VisualRenderer, EdgeGlowLayer (AGSL),
                 RenderSupport (geometry, colors, GPU glow), layers/ (bars, radial, wave, pulse)
  service/       VisualizerService (FGS state machine), OverlayWindow, DeviceSignals,
                 Notifications, Tiles (+ start/projection trampolines)
  media/         NowPlayingListener (MediaSession via notification access), AlbumPalette
  haptics/       BeatHaptics
  settings/      Model (one typed settings tree), BuiltInPresets, PresetOps, SettingsRepository
  ui/            Compose screens, controls, live preview, debug panel
```

The render and audio hot loops reuse every buffer; the settings model holds every tunable value.

## Known platform limits (by design, not bugs)

- **Max overlay opacity is 80%.** Android 12+ blocks touches through other apps' overlays above 0.8 opacity, so EQV uses one window capped at 0.8.
- **Visuals draw under the status bar and notification shade.** That's where Android places app overlays.
- **HQ capture** asks for consent every session, shows a red chip, and stops at screen lock (Android 14/15). **Spotify blocks capture** entirely, which is why Auto uses the system Visualizer API.
- **No real shake of other apps** without root. Thump fakes it.
