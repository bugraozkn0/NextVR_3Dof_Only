# EasyAR Native Cinema

The current quality pass and task packages are documented in [CINEMA_QUALITY_PACKAGES.md](CINEMA_QUALITY_PACKAGES.md).
It adds automatic live-camera setup, visible tracking/room-lock status, GPU preview,
head-directed pinch selection and a touch rescue menu. The sections below describe
the initial backend contract; the quality-pass document supersedes the old CPU preview limits.

Base: `feature/cinema-passthrough-mvp` at `b8fb011e2c784bec7cce8fa6396e01e3424af98e`.
Work branch: `feature/easyar-native-cinema`. Android application ID remains
`com.samrat.cardboardhands.cinema`. Full/Lite keep their existing backends.

## Build without Android Studio

Requirements: existing JDK 17, Android SDK platform 37.0/build-tools 36.0.0,
an existing Android NDK for Gradle's native-library stripping, Python and 7-Zip.
The app uses AGP 9.2.0, built-in Kotlin and Gradle 9.5.1 as on the base branch.

1. Obtain official **EasyAR Sense 4.11.0.12415 Community Native SDK** from
   https://www.easyar.com/view/download.html (account login required).
2. Extract only the Android jar and arm64 runtime, with pinned SHA-256 checks:

   ```powershell
   python scripts/prepare_easyar_sdk.py "C:\path\EasyARSense_4.11.0.12415.7z"
   ```

3. Create **untracked** `easyar.local.properties` in the repository root:

   ```properties
   sdkDir=vendor/easyar
   licenseKey=YOUR_ANDROID_LICENSE_FOR_com.samrat.cardboardhands.cinema
   ```

   Never add this file to Git or paste its contents into logs. The build generates
   `app/build/generated/easyarAssets/easyar-license.txt`; the license necessarily
   ships inside the APK for native SDK initialization. It is not in source or BuildConfig.
   SDK files also remain untracked; no proprietary SDK binaries are republished in this repo.

4. Preserve the Cinema base's packaged backgrounds: run
   `python scripts/prepare_cinema_environments.py` if its four generated JPEGs are
   absent. These are the existing static 2K CC0 panoramas, not a new moving environment.
5. Build and execute tests:

   ```powershell
   .\gradlew.bat :app:testBeDebugUnitTest :app:assembleBeDebug -Pvins.enabled=false -Peasyar.required=true --no-daemon --max-workers=1
   ```

   On Linux/macOS use `./gradlew`. `easyar.required=true` fails early if the SDK
   or license is absent. Without it an SDK-free build remains an explicit 3DoF
   fallback and must not be called a 6DoF build. CI without local SDK/license can
   test the fallback but cannot validate the EasyAR implementation.

   Output: `app/build/outputs/apk/be/debug/app-be-debug.apk`.
   Debug signing uses the local Android debug key, as before. An APK from another
   machine may have a different signing certificate despite the same application ID.

For this Windows machine, an existing Unity installation supplies JDK 17 and NDK
27.2.12479018. A workspace-local Gradle init script sets `android.ndkPath` and
`android.ndkVersion` for all Android modules. It is not required on machines with
the default NDK installed. `-Pandroid.builder.sdkDownload=false` prevents SDK auto-downloads.

## Use and ownership

On the phone launch screen, tap **Tracking** to select **EasyAR Native**, then
Start VR and grant camera access. The initial default remains None/3DoF to keep
opening Cinema camera-free. Settings → Room also exposes the backend and status.
None restores the original CameraX implementation without removing any controls.

Only `MotionTrackerCameraDevice` is created. Its InputFrameSource feeds
InputFrameToOutputFrameAdapter and OutputFrameBuffer, as in the SDK's
`samples/Community/Android/HelloARMotionTracking/.../HelloAR.java` EasyARMotionTracker
path. No ARCore/AR Foundation/Unity/custom camera path is selected. Existing
ARCore dependency declarations for the other editions are retained.

Pending CameraX binds are invalidated before EasyAR takes ownership. A live
CameraX camera must reach CLOSED before the new tracker starts. Pause/close release
EasyAR; None restores CameraX. Background work cannot publish a hand frame into
a different session. Native SDK references are disposed before image conversion.

HeadTracker remains the sole source of view rotation. EasyAR provides metric
camera translation. Row-major camera-to-world transforms are transposed once,
image/display rotation is applied once, and a full 3D alignment maps translation
into HeadTracker's recentered world (including reclining). No tracker rotation
is multiplied into the view a second time. Camera optical centre is used as the
head reference; no measured camera-to-eye lever-arm calibration is claimed.

Tracking loss/stale frames freeze position while sensor rotation and gaze continue.
Reacquisition/map jumps rebase at the held position; movement while tracking was
lost is intentionally discarded to avoid a room snap. Recenter defines a new origin.
Timestamps preserve SDK seconds; sensor history uses that clock only when it
matches Android elapsed realtime, otherwise arrival time is explicitly reported.

The same EasyAR image stream supplies existing PinchHands/MediaPipe and passthrough.
Pinch selection, scroll, drag/resize, two-hand zoom, gaze/dwell, browser/YouTube,
video and stereo/lens rendering retain their existing handlers. Conversion supports
SDK Gray, NV21, NV12, I420, YV12, RGB/BGR and RGBA/BGRA padded layouts. Unknown layouts
are reported; no hand feature is silently deleted. The quality pass uploads raw padded camera planes through a reused direct buffer
and converts them in a GPU shader independently of hand inference. Only hand images
use bounded CPU conversion (maximum 640-pixel side), with reused byte/pixel arrays.
Hands use a GPU delegate when available, with CPU fallback. Camera input remains
the built-in native 30 Hz stream; achieved device rates require phone measurement.
Moving 360° environments remain deferred.

Rendering and flat/curved window hit tests use the translated head origin.
Position is updated before both gaze hit testing and drawing in each GL frame.
Diagnostics expose native status, XYZ metres, frame rate, raw timestamp, clock
choice and rebase count. They are in Settings and the existing Diagnostics toggle.

## Validation and remaining device checks

Automated results are recorded with the delivered APK; do not interpret compilation
or mathematical unit tests as phone tracking/interaction verification.

New tests cover row/column matrix conversion, metric axis signs/scale, landscape
and reclining alignment, rotation-only motion, loss/relocalization, map jumps,
invalid/stale timestamps, all supported pixel layouts and translated window hits.
The original BE test suites remain enabled.

No ADB phone was attached during initial implementation. Still requiring a phone:

- [ ] License acceptance and MotionTrackerCameraDevice.isAvailable on target phone.
- [ ] Sideways/vertical/forward motion moves a stationary screen in correct metric directions.
- [ ] Camera/view alignment in both landscape orientations; reclining recenter.
- [ ] Tracking loss, pause/resume and None ↔ EasyAR switches without HAL conflicts or jumps.
- [ ] Pinch, fist, scroll, grab, resize, two-hand zoom and gaze/dwell on the same pages.
- [ ] Browser, YouTube, native video playback, fullscreen, stereo and lens distortion.
- [ ] Passthrough image orientation and hand cursor alignment with real camera intrinsics.
- [ ] Sustained frame rate, thermals, power use and long-session drift.

Device calibration/quality and license entitlement can prevent native motion
tracking. Initialization/start failures are shown without logging the license;
None remains available. No runtime success or performance measurement is claimed
until these checks run on hardware.

## Primary references

- https://www.easyar.com/doc/en/api/native/easyar.MotionTrackerCameraDevice.html
- https://www.easyar.com/doc/en/api/native/easyar.InputFrame.html
- https://www.easyar.com/doc/en/api/native/easyar.CameraParameters.html
- https://www.easyar.com/doc/en/api/native/easyar.OutputFrameBuffer.html
- https://www.easyar.com/doc/en/api/native/easyar.Engine.html
- https://www.easyar.com/doc/en/develop/samples.html
- https://www.easyar.com/doc/en/develop/motion-tracking/devices-easyar.html
- https://developer.android.com/build/migrate-to-built-in-kotlin

Java namespace and disposal signatures were verified against the supplied
`cn.easyar` jar and HelloARMotionTracking sample, not inferred from Unity docs.
