# NextVR 3DoF Cinema — test build

## Features
- 3DoF, no ARCore; rear camera is opt-in and may appear *behind* an open video window.
- Browser, web video, Android MediaStore local videos and photos.
- Gaze selection after about one second; no gamepad or hand tracking necessary.
- Gaze-operated up/down scroll buttons injected into the browser page.
- While media is playing, the screen surface does **not** accept gaze clicks.
  Look at the movie window's toolbar for ~1 s to unlock controls for 12 s.
- Dwell on move/resize handle, move head, hold still ~1 s to place window.
- Private test app ID `com.samrat.cardboardhands.cinema`, separate from upstream.
- No account required; unused privileged permissions disabled in BE manifest.

## Build
1. Fork Actions may need enabling via GitHub's Actions page.
2. Commit changes to `feature/cinema-passthrough-mvp`.
3. Download `cinema-3dof-debug-apk` from a successful `Cinema APK` run.
4. Install APK on Xiaomi 14 Pro. Use a spare local test video, not private data.
5. To install a build from a later GitHub-hosted runner, uninstall the previous
   Cinema test build; the security fix removes the shared debug signing key,
   so new CI debug keys will differ.

## Verification checklist
- Head tracking and gaze select launcher/keyboard/browser controls.
- 20 consecutive YouTube scrolls without inadvertent links.
- Web video full-screen enter/exit without audio/video stopping.
- Local MP4 plays; gaze at screen does not accidentally pause.
- Controls can be opened from toolbar and close again.
- Movie remains visible while toggling Real world passthrough.
- Camera stops when switching to Space/Night; Android camera indicator clears.
- Camera denied: browser and library should still work.
- 20 minutes sustained use, measure heat, battery drain and headset comfort.

## Limitations
- This patch is not a successful Android build or device validation by itself.
- Android WebView may reject Prime Video/Widevine DRM.
- No claim of a complete security audit; do not log in with real credentials
  until APK provenance, signing and WebView safety are independently reviewed.
- Android MediaPlayer controls codec/subtitle coverage.
- Single-camera passthrough is *not* stereoscopic depth-correct AR.
