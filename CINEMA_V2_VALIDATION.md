# Cinema V2 device validation and implementation record

The Cinema APK workflow runs BE JVM unit tests, assembleBeDebug, lintBeDebug, apksigner and
aapt package/launcher inspection. Those checks do not establish device integration, FPS,
thermal safety, visual alignment or gesture accuracy.

## Implemented
- Semantic native/DOM gaze targets, spatial edge hysteresis, one-shot dwell/repeat scrolling,
  progress geometry, selectable dwell time/dead zone and native hover labels.
- Persistent native control layer below browser/video windows, explicit Cinema mode and
  explicit Website controls opt-in. Scrolling does not depend on media state or page CSS.
- Native HTML5 play/pause/seek for accessible same-document video. Unsupported iframe/DRM
  players report unavailable; use Website controls and touchscreen fallback.
- Browser viewport switches to 16:9 without navigation. Flat by default, mild 35/55-degree
  curvature preserving screen arc width. Size and distance controls; full rotation recenter
  in BE (including reclined initial pose). Existing lens/IPD offsets are retained.
- BE CameraX Preview -> external OES SurfaceTexture, separate from optional latest-frame
  640-pixel analysis throttled to at most ~12 FPS. Surface lease released on CameraX completion.
- Capture callback FPS, latched preview FPS, render FPS, analysis duration and approximate
  buffer age shown independently while camera active. Buffer age may be -1 for mismatched
  clock domains; it is not end-to-end optical latency.
- Experimental one-hand CPU MediaPipe using the existing model; no YOLO pass in BE.
  Pinch debounce, held handle move/resize, release/cancel on hand loss, gaze fallback.
- CC0 genuine Poly Haven 2K panorama conversion in CI; one selected texture at a time.
- User-opened Help, optional gallery permission and single-file system document picker.
- No privileged PhoneXR JavascriptInterface on arbitrary BE pages or frames. Native page
  probing returns data only. File/content access disabled for WebView; native picker is separate.
- No package identity change, telemetry, new Android runtime dependency, root or Shizuku.

## Device checklist (NOT executed by CI)
On Xiaomi 14 Pro / Android 14 / HyperOS DEV:
- [ ] Natural head tremor, button boundaries, look-away cancellation, visible progress ring.
- [ ] YouTube inline play/pause and 20 consecutive native scroll operations; no random links.
- [ ] Browser fullscreen enter/exit, with no reload, navigation or audio reset.
- [ ] Local MP4 via individual picker while full gallery permission is denied.
- [ ] Native Cinema bar accessible playing/paused/buffering/ended; Hide/Show and Website controls.
- [ ] Camera on/off behind active movie; verify upright crop/aspect in BOTH eyes.
- [ ] Camera busy/disconnect/permission denial; pause/resume; forced GL context loss.
- [ ] Hands toggle: camera active even with virtual background; off stops camera if passthrough off.
- [ ] Hand click, held move/resize, release, occlusion, low light, stale timestamps and hand loss.
- [ ] Gaze/hand arbitration: no head movement of a hand-held window; safe fallback on loss.
- [ ] Recenter while reclining; compare left/right stereo alignment and native bar hit positions.
- [ ] Panorama theater/screen heading, readable labels, flat and curved content proportions.
- [ ] 20-minute playback: accidental clicks, heat, battery, frame drops and audio continuity.
- [ ] Record capture / preview / render FPS and analysis ms separately, hands OFF and ON.
- [ ] Use external optical measurement for real camera-to-display latency, not buffer age.

## Experimental / limits
Single rear camera is monocular, not depth-correct stereo AR or true 6DoF hand tracking.
One-hand controls and camera UV/rotation require device validation before recommending hands.
No two-hand zoom, screen-edge glow or 4K preset; 2K is the mobile baseline.
Page JS cannot access cross-origin iframe or DRM players; native controls expose availability
and explicit website/touch fallback. Native fullscreen controls may require touch fallback.
Distance applies to the shared 3DoF window radius; screen/pose comfort needs hardware review.
Debug CI signing keys can differ across fresh runners: previous APK remains downloadable,
but Android may require uninstalling an old differently signed test app to install this one.
Do not uninstall an existing build until a replacement is available and app-data loss is acceptable.

## Sources checked
- https://developer.android.com/reference/androidx/camera/core/SurfaceRequest
- https://developer.android.com/reference/android/graphics/SurfaceTexture
- https://developer.android.com/privacy-and-security/risks/insecure-webview-native-bridges
- https://developer.android.com/training/data-storage/shared/documents-files
- https://polyhaven.com/license
- CINEMA_ASSET_CREDITS.md
