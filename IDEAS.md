# IDEAS backlog

Effort: S = under 1 h, M = a few hours, L = a day or more. Status: `todo` / `approved` / `done` / `rejected`.

| # | Idea | Pitch | Effort | Status |
|---|---|---|---|---|
| 1 | **Glyph Matrix mirror** | Mirror the spectrum and beat pulses onto the Phone (3)'s 25×25 rear Glyph Matrix through Nothing's official Glyph Matrix SDK, so the phone visualizes even face-down. | M | todo |
| 2 | **A/V sync offset** | Delay visuals and haptics by the output latency (auto default per speaker/wired/Bluetooth, plus a slider), because Bluetooth audio lags ~200 ms behind the analysis tap. | S | todo (proposed in plan) |
| 3 | **Silence watchdog fallback** | Detect "session says PLAYING but source is silent" and drop to the next audio source automatically, with a UI badge. | S | todo (proposed in plan) |
| 4 | **Accessibility-overlay mode** | Optional mode that draws through an AccessibilityService overlay: no 0.8 alpha cap, and it can draw over the status bar. Needs "Allow restricted settings" for sideloaded apps; opt-in only. | M | todo |
| 5 | **Genre/BPM-aware auto preset** | Estimate tempo and spectral balance, then auto-pick a preset (calm wave for ambient, edge + bars for EDM). | M | todo |
| 6 | **Dot-matrix bar style** | A "Nothing" bar style drawn as quantized LED dots that matches the Glyph aesthetic. | S | todo |
| 7 | **Per-app presets** | Automatically switch the preset per source app (Spotify edge glow, YouTube subtle bars). | S | todo |
| 8 | **Adaptive frame rate** | Drop to 30 fps automatically during quiet passages or low-energy music; boost to 90/120 on heavy beats. | S | todo |
| 9 | **Tempo-locked haptics** | Predict the next beat from the BPM estimate so haptics land *on* the beat instead of reacting late. | M | todo |
| 10 | **Shareable preset QR** | Export a preset as a QR code or deep link so friends can import it instantly. | S | todo |
| 11 | **Lock-screen / AOD-aware dim mode** | Ultra-low-power thin edge line while the screen is on but idle. | M | todo |
| 12 | **Auto-start on headphones** | Arm the service when headphones or Bluetooth audio connect. | S | todo |
