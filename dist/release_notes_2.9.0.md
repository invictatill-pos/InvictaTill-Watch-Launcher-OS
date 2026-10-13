# HealthSync 2.9.0 — Face Studio

Create watch faces from a blank analog, digital, or hybrid project, or customize one of six starter designs. Face Studio now includes custom text and shape layers, canvas dragging and grid snapping, precise placement and styling, undo/redo, recovery drafts, portable `.hswf` project import/export, and a searchable creation library.

## Creator tools

- Up to 128 custom layers with ordering, visibility, locking, duplication, rotation, and opacity.
- Font, color, dial, hand, clock, date, widget, photo dimming, and gradient controls.
- Eight decorative animations, watch tap actions, and dim/monochrome/minimal ambient layouts.
- A design test bench with sample time and explicitly labeled simulated widget values.
- Per-project recovery for the 20 most recent drafts.
- Matching 320-unit sizing in the phone preview and watch renderer, bounded image decoding, and corrected 24-hour time.

## Update both apps

Install **HealthSync-Phone.apk** (2.9.0, code 28) on the phone and **HealthSync-Watch.apk** (2.9.0, code 34) on the watch. Custom artwork layers require the updated watch app. Existing users can use the app's update controls or download the APKs here. Android asks the device user to authorize installation.

Install over the existing apps to retain saved projects and history. Both APKs retain the existing development signing certificate. The phone requires Android 8/API 26 or newer; the watch requires Android 6/API 23 or newer. The ZIP includes both APKs, installation instructions, the Face Studio guide, build report, and checksums.

## Verification

355 unit tests passed; both lint checks report zero errors. APK signatures, build versions, and SHA-256 checksums were verified. Physical phone/watch testing remains unverified; check touchscreen editing, Bluetooth installation, circular clipping, ambient brightness, and battery use on your devices.

Read the attached **FACE-STUDIO.md** for the workflow and **BUILD-REPORT-Android8-2.9.0.md** for validation details.
