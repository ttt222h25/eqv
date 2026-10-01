# EQV: system-wide music visualizer for Nothing Phone (3) (Step 0 plan)

Status: **approved and built**. The phone-only workflow uses GitHub Actions for builds (see README).

## 1. Research findings (Android 15/16), and what they force

| Topic | Current rule | Consequence for us |
|---|---|---|
| **Overlay** (`TYPE_APPLICATION_OVERLAY`) | Needs `SYSTEM_ALERT_WINDOW` (user toggles it in Settings). The window draws above apps but **below the status bar, nav bar, shade and IME**. | Edge glow shows *through* the transparent status bar but sits under the shade. We use `layoutInDisplayCutoutMode=ALWAYS` + `FLAG_LAYOUT_NO_LIMITS` to reach the cutout and corners. |
| **Untrusted-touch rule** (Android 12+) | A click-through overlay from another app **blocks touches** if its window opacity is > **0.8**. Opacities of the same app's overlapping windows **combine** (two 0.8 windows give 0.96, so touches get blocked). | **Hard limits:** (a) a single overlay window, (b) window `alpha ≤ 0.8`. Visuals can never be 100% opaque. We make up for it with additive blending and bright palettes. *(There is a way past it: an accessibility-service overlay. See IDEAS.md for its caveats.)* |
| **FGS from background** | Android 15+: an app holding `SYSTEM_ALERT_WINDOW` may start an FGS from the background **only while it has a visible overlay window**. | Auto-start order: add the overlay window first, then promote the service. |
| **While-in-use (mic) FGS** | A `microphone`-type FGS **cannot be created from the background** (Android 14+). Exemptions: notification tap, widget, and system component. | We use an **armed service** (see §3). It is started once while the app is in the foreground and then stays alive with zero work until music plays. |
| **MediaProjection / AudioPlaybackCapture** | Android 14+ asks for **consent on every session** (a token can't be reused) and needs an FGS of type `mediaProjection`. **Android 15 QPR1+ shows a big red status-bar chip and auto-stops the projection when the screen locks.** | Playback capture **cannot auto-start**. You would see a consent dialog after every unlock and a red "sharing" chip the whole time. This is a bad default for an always-on ambient visualizer. |
| **Spotify** | Sets `allowAudioPlaybackCapture="false"`, so capture returns **pure silence**. | The brief's fallback chain (capture first) would fail for the main use case on every start. |
| **YouTube / YT Music** | Behavior varies by version and content (DRM). | Detected at runtime (§2). |
| **Visualizer API** (`audioSession=0`) | Still works and is still what Muviz uses. It needs `RECORD_AUDIO` + `MODIFY_AUDIO_SETTINGS`. **It is not affected by the playback-capture opt-out**, so Spotify works. Downsides: 8-bit samples, max 1024 samples, offloaded or "hi-fi" tracks can return zeros on some devices, and it is likely gated by while-in-use mic access in the background. | The **most reliable source** for Spotify/YouTube. |
| **Microphone** | Works anywhere. It shows the green privacy dot and picks up room noise. | Last resort only. |
| **Now playing** | `NotificationListenerService` access lets `MediaSessionManager.getActiveSessions()` return every session: package, play state, metadata, and album-art bitmap. | Covers auto-start/stop, the source-app filter and album colors. No root, no extra permissions. |
| **Foreground-app filter** | Telling which app is in front needs `PACKAGE_USAGE_STATS` (a special "Usage access" toggle). | Optional permission, used only when you set per-app rules for the foreground app. |
| **Fullscreen video detection** | There is no public API. The overlay window's `WindowInsets.isVisible(statusBars())` follows the focused app's bars on Android 11+. | Primary signal, **verify on device**. Fallback: a "hide in these apps" list. |
| **Haptics** | Composition primitives (`THUD`, `CLICK`, `LOW_TICK`, `QUICK_RISE`; support is checked at runtime). Android 16 adds envelope/frequency effects (`BasicEnvelopeBuilder`). | Real waveforms, with feature-detection and a fallback to amplitude waveforms. |
| **Shaking other apps** | Impossible without root, as you said. | Fake "Thump" + a real shake inside our own preview, as you asked. |

### Changes I recommend to the brief
1. **Reorder the fallback chain.** `Auto` = **Visualizer → Mic**, with **Playback capture as an explicit opt-in "HQ mode"**: one tap on the consent dialog per session, and it ends at screen lock. Auto still detects failure and drops down: if a MediaSession reports PLAYING but the source gives ~silence for 1.5 s, we switch and show it in the UI. Putting capture first would mean a dialog plus a red chip on every start, and silence on Spotify anyway.
2. **A/V sync offset (new).** Visualizer and capture tap the audio *before* the output path, so visuals and haptics run **ahead** of what you hear. That lead is tiny on the speaker and **~150–250 ms on Bluetooth**. I'll add a delay line with a per-output default (speaker / wired / BT) and a slider. Without it, beat haptics on earbuds will feel wrong.
3. **Window alpha is capped at 0.8**, as explained above. The UI will say so instead of offering a fake "100%".

## 2. Architecture (single module, Kotlin, Compose, min SDK 33 / target 36)

```
media/     NowPlayingListener (NotificationListenerService), SessionTracker, AlbumPalette
audio/     AudioSource (interface) ← VisualizerSource, PlaybackCaptureSource, MicSource, SyntheticSource
           SourceSelector (fallback + silence watchdog), Fft (radix-2, Hann, 1024/2048, 50% overlap)
           BandMapper (log bands 8–64, weighting), Smoother (attack/decay, peak-hold, auto-gain)
           BeatDetector (spectral flux, adaptive threshold, cooldown), SyncDelay (ring buffer)
           AnalysisFrame (preallocated, double-buffered, lock-free hand-off to render)
render/    OverlayRenderer (Choreographer, FPS cap), one class per layer:
           EdgeGlowLayer, BarsLayer, RadialLayer, WaveLayer, BeatPulseLayer, ThumpFx
           ScreenGeometry (rounded corners, cutout path, insets, rotation)
service/   VisualizerService (FGS: specialUse | microphone | mediaProjection), OverlayWindow,
           ToggleTileService, NotificationController, BehaviorGate (screen/call/battery/fullscreen/app rules)
haptics/   BeatHaptics (primitives → envelope → waveform fallback)
settings/  model (one typed @Serializable data class tree), SettingsRepository (DataStore),
           PresetRepository (JSON import/export), ui/ (Compose screens, live preview, debug panel)
```

**Threads:** a dedicated audio thread (source read → FFT → bands → beat → SyncDelay) publishes into a preallocated double buffer. The render thread is the main thread's Choreographer. It reads the latest frame and does no allocation. Haptics fire from the audio thread after the sync delay.

**Rendering choice: one hardware-accelerated custom `View`, with an AGSL `RuntimeShader` for edge glow, gradients and the chromatic flash, and `RenderNode` + `RenderEffect` blur for bloom.**
- Why not GL: an EGL surface means more code, its own thread and lifecycle on rotation and overlay re-adds, and it gains little. HWUI already draws on the GPU. AGSL gives us real per-pixel shaders (soft edge falloff, smooth rounded-corner SDF) without a GL context.
- Why not plain Canvas blur (`BlurMaskFilter`): it rasterizes in software, which is too slow for animated paths. `RenderEffect` blur runs on the GPU.
- Cost control: one full-screen RGBA layer. We cap the FPS (default 60 on the 120 Hz LTPO panel), vote the frame rate (`setRequestedFrameRate`), and draw nothing and remove the window when idle. If profiling in Phase 7 shows > 4 ms frames I'll revisit GL.

**Live preview:** the settings app embeds the *same* renderer in a Compose `AndroidView`, fed by the active or synthetic source. "Real shake" applies only to the preview container (`graphicsLayer` translation).

## 3. Permissions and lifecycle

| Permission | Why | When asked |
|---|---|---|
| Display over other apps | overlay | onboarding step 1 |
| Microphone (`RECORD_AUDIO`) | Visualizer + mic source | onboarding step 2 |
| Notification access | now playing, auto-start, album art | onboarding step 3 |
| Post notifications | FGS notification | onboarding step 4 |
| Usage access *(optional)* | foreground-app filter | only when you set a foreground-app rule |
| MediaProjection consent *(optional)* | HQ capture | each time HQ mode starts |
| `MODIFY_AUDIO_SETTINGS`, `FOREGROUND_SERVICE_*`, `VIBRATE` | normal (auto-granted) | — |

**States:** `OFF` → `ARMED` (FGS alive, minimal notification, no audio, no rendering, ~0% CPU) → `ACTIVE` (music playing, overlay + audio + render) → back to `ARMED` after a ~3 s grace once playback stops.
- The ARMED FGS is started while in the foreground (app, tile tap or notification tap), so it keeps while-in-use mic access. The notification listener just flips ARMED ↔ ACTIVE.
- If the process gets killed, the listener restarts what Android allows: overlay first, then a `specialUse` FGS. If Visualizer is then denied, we post a "Tap to resume" notification, because a notification tap is an allowed exemption.
- The ongoing notification and the tile show on/off plus a preset switcher (no media controls). That notification *is* the required FGS notification, so it costs nothing extra.

## 4. Risks (ranked)
1. **Visualizer API quirks on Nothing OS.** Zeros during offloaded playback, volume-dependent levels, or background gating. → Phase 2 debug panel shows the raw source and level; the watchdog falls back to mic; fix found on device.
2. **Fullscreen detection** relies on insets behavior that varies by OEM. → Verify in Phase 6; the app list is the fallback.
3. **0.8 alpha cap** makes visuals less punchy than an unrestricted overlay. → Additive blending and contrast tuning. The accessibility mode is an opt-in idea.
4. **Battery:** a full-screen translucent layer at 60 fps. → FPS cap, idle teardown, quality setting; measured in Phase 7.
5. **Build environment (blocker for me):** see §5.

## 5. Build and tooling status
- This cloud container has **JDK 21 and Gradle 8.14** but **no Android SDK and no adb**. The network policy **blocks `dl.google.com`**, which serves the Android SDK *and* Google Maven (AGP, AndroidX, Compose). So I cannot compile or run unit tests here until that host is allowed.
- adb from this container to your phone is impossible either way: the container is in the cloud and your phone is on your network. You install from your own PC.
- **Plan:**
  - (a) You allow `dl.google.com` + `maven.google.com` in this environment's network settings (cloud environment menu → Edit → Network access). I'll then install the SDK here and compile and test every phase before pushing.
  - (b) I add a **GitHub Actions workflow** that runs unit tests and builds a debug APK on every push. You download the APK from the run's artifacts, so you don't even need the SDK locally.
- **Your PC (for adb install):** install Android SDK Platform-Tools only:
  1. Get the zip from https://developer.android.com/tools/releases/platform-tools, unzip it, and add it to PATH.
  2. On the phone: Settings → System → Developer options → Wireless debugging → Pair device with pairing code.
  3. Run `adb pair <ip>:<pair-port>`, then `adb connect <ip>:<port>`, then `adb install -r app-debug.apk`.

## 6. Phases (unchanged from the brief)
1. Skeleton, onboarding/permissions flow, armed/active service, overlay with a synthetic signal, CI workflow.
2. Audio sources + fallback watchdog + FFT/bands/beat + debug panel + unit tests.
3. Layers: edge glow (corner/cutout aware), bars, radial, wave, beat pulse.
4. Haptics + Thump + A/V sync offset.
5. Full settings UI, presets (5 built-in), import/export, live preview.
6. Auto-start, app filters, fullscreen hide, album colors, tile, notification.
7. Polish, performance pass, ideas review.
