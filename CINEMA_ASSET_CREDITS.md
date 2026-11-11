# Cinema environment sources
All four genuine equirectangular HDRIs are CC0-1.0: https://polyhaven.com/license
Powered by Poly Haven. No Poly Haven API is called by the application.
- https://polyhaven.com/a/pretville_cinema
- https://polyhaven.com/a/warm_bar
- https://polyhaven.com/a/rooftop_night
- https://polyhaven.com/a/rogland_clear_night

The CI converter downloads only the pinned 2K RGBE files (about 25 MB total on the runner),
checks source MD5, tone maps offline and encodes 2048×1024 JPEGs.
No perspective preview/thumbnail is substituted for a panorama.
Generated environments/credits.json records source URL, digest, output digest and conversion.
Only the selected texture is decoded/uploaded in the app. Black/Void is always available.
4K is deliberately not enabled before Xiaomi thermal/frame-time validation.
