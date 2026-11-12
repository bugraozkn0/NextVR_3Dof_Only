# Cinema quality pass: research, packages and validation

User feedback on build 5500c44: Xiaomi 14 Pro / Android 14, tested without a headset;
camera stutters, hands feel poor, screen seems to move with the user, diagnostics
could not be found. Those are user observations, not measurements captured here.
No ADB phone is connected to the development machine.

## Lowest-cost route

Keep Native Android, the existing EasyAR Community SDK, MediaPipe and the working
browser/media/stereo/lens renderer. No new SDK purchase, dependency, cloud inference,
model download, Unity migration or Android Studio installation is needed for this pass.
This is an implementation-cost judgment based on the current code, not a quoted vendor price.
Existing license entitlement still has to be accepted by the SDK on the phone.

The achievable product is a room-positioned cinema with dependable selection and
simple window manipulation. A single phone camera does not supply Vision Pro's eye
tracking and multi-camera/depth coverage. We use head-directed selection plus pinch
as an explicit substitute for eyes, keeping direct hand aiming as an optional mode.

Apple describes indirect look-and-pinch interaction:
https://developer.apple.com/visionos/get-started/
https://developer.apple.com/design/human-interface-guidelines/designing-for-visionos/

MediaPipe documents GPU/CPU delegates, tracking between detections and asynchronous
busy-frame dropping. This app already has one exclusive inference worker and drops
new hand requests while busy. Keeping synchronous VIDEO on that worker avoids a
second callback/ownership queue; preview must be independent of that worker.
https://developers.google.com/edge/mediapipe/solutions/vision/hand_landmarker

## Package 1 — observable tracking and room placement

Implemented:

- On EasyAR launch, show real-world camera and guided scanning automatically.
- Always-visible status in both eyes; tap it on the phone for scan, lock, rescue,
  retry, 3DoF fallback and copyable tracking/license details.
- Show native status, DOF type, accepted pose count, XYZ metres, SDK license
  validation, device quality code, inference time/FPS and rendering FPS.
- Require native tracking AND at least 3 cm of measured position excursion before
  confirming initial room lock. A selected backend name alone is insufficient.
- Query `hitTestAgainstHorizontalPlane` during setup. A small grid marks a real
  returned hit. It does not claim the size/boundary of the detected plane, wall
  reconstruction or a complete room mesh. A floating screen can be locked without
  finding a floor plane once metric movement is established.
- Clear the old persisted travel/car-mode gate on EasyAR launch. That gate could
  prevent the tracker from starting. We have not established that it caused this
  user's failed session, because no phone diagnostics from that session exist.
- Lock window handles against accidental dwell/pinch movement. Explicit placement
  unlocks them; a rescue command cancels held input and returns panels to the front
  without reloading their browser/video content.

EasyAR primary references:
https://www.easyar.com/doc/en/api/native/easyar.MotionTrackerCameraDevice.html
https://www.easyar.com/doc/en/api/native/easyar.InputFrame.html
https://www.easyar.com/doc/en/api/native/easyar.ValidationState.html

## Package 2 — independent preview and bounded hand work

The previous implementation intentionally capped copied images around 15 Hz, then
throttled inference a second time. It converted camera images to RGB bitmaps and
uploaded those only after the inference job. This structurally coupled preview
latency to the hand model and produced avoidable allocation/copy work.

Implemented:

- Native output -> reused direct buffer -> GPU YUV/RGB shader. Copy into the direct
  buffer uses SDK `Buffer.wrapBuffer` / `tryCopyTo`, not a custom camera input.
- Upload once per new camera frame; draw the same textures for both eyes.
- GPU rotation/crop and padded NV21/NV12/I420/YV12/RGB/BGR/RGBA/BGRA/Gray support.
- Reuse staging memory, hand byte/pixel arrays, vertices and shader locations.
- Camera preview continues while the hand worker is busy. No inference backlog.
- Hands request at most native 30 Hz, naturally dropping work when inference is
  busy; GPU delegate is attempted on the same worker used for detection, with CPU
  fallback. No achieved device rate is asserted.
- If preview shader setup fails, hold native pose tracking and expose a CPU
  preview fallback rather than taking down the tracker.
- Preserve EGL-context ownership when releasing/recreating preview textures.

Layout and source signatures were checked against the supplied official 4.11.0
HelloARMotionTracking sample and jar. Buffer API:
https://www.easyar.com/doc/en/api/native/easyar.Buffer.html

## Package 3 — easier input and readable controls

Implemented:

- Default head-directed targeting plus pinch confirmation, with a visible reticle.
- Require an open hand before arming a newly acquired/reacquired pinch; short noise
  does not select a button. Cancel held gestures when switching camera/input modes.
- Classify pinch before cursor smoothing; use the steadier palm-base aim point in
  optional hand-ray mode. Manual camera orientation correction is applied once.
- Keep the existing vertical pinch scroll, handle manipulation, two-hand resize,
  direct-hand mode and gaze/dwell fallback. World-locked handles require explicit
  unlocking before movement, to prevent the screen drifting after an accidental grab.
- Default to a 12-action, two-row control panel with larger labels. All advanced
  controls remain under More; nothing is removed from browser/YouTube/video paths.

## Validation and limits

The prior baseline had 105 passing BE tests. Eight additional tests cover
open-to-arm/reacquisition/noise, native padded image capacities, pixel-array reuse,
world-space plane-hit conversion and compact control targeting. Final counts and
APK hash are saved with the delivered build after the last source changes.

Build/test configuration reuses local JDK 17/NDK, disables SDK downloads and limits
Gradle to one worker and 1.5 GB heap. Native SDK and local license stay outside Git.
The official SDK jar and native binary are unchanged. No code strips or obscures
an SDK watermark. License/watermark behavior belongs to EasyAR; absence of a visible
watermark does not prove valid tracking:
https://www.easyar.com/doc/en/develop/license-sense.html

No phone GPU execution, physical 6DoF movement, plane detection, hand accuracy,
browser/YouTube playback regression, thermals, power or latency has been measured
by the agent. Compilation, math tests and APK signature checks do not establish
those results. The next phone test should use the visible XYZ/status and copyable
diagnostics so an actual tracking failure can be distinguished from UI placement.

Deferred: persistent spatial maps, reconstructed room meshes, moving 360° scenes,
new visual themes and eye-tracking hardware. Static environments and existing
media/browser functionality remain packaged.
