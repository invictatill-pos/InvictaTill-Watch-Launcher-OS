# HealthSync 2.9.0 — Face Studio build report

Prepared 13 October 2026. Watch version code **34**; phone version code **28**.

## Delivered changes

- Blank analog, digital, and hybrid projects, plus six customizable starter designs.
- Custom text, panel, ellipse, and line layers with selection, dragging, 5% grid snapping, ordering, visibility, locking, duplication, and deletion.
- Numeric placement, dimensions, rotation, opacity, fonts, hex colors, hand styling, dial geometry, photo dimming, and gradient direction.
- Shared 320-unit design sizing on phone and watch; custom layers render above standard clock elements.
- Undo/redo with gesture grouping and bounded history; atomic project saves and recovery for the 20 most recent projects.
- Validated portable `.hswf` import/export, separate imported IDs, 512 KB package limit, and 128 custom-layer limit. Pending export snapshots survive screen recreation through a saved file reference.
- Eight configurable decorative animations and watch tap palette/layout actions.
- Functional dim, monochrome, and minimal-time ambient layouts; independent ambient visibility for artwork layers.
- A design test bench with selectable time, explicitly labeled simulated widget values, package validation, and circular-edge warnings.
- A searchable creation library with sorting, duplication, loading states, and confirmed deletion.
- Custom-layer transfer compatibility checks and correlated watch installation acknowledgments.
- Bounded image decoding, temporary photo bitmap cleanup, and corrected 24-hour watch time formatting.

## Validation

| Check | Result |
| --- | --- |
| Phone unit tests | 124 passed, 0 failures/errors |
| Watch unit tests | 231 passed, 0 failures/errors |
| New studio tests | 26 across document/history/storage, transfer compatibility, and schema compatibility |
| Actual studio starters and online catalog | Validated and export/import round-tripped in the phone suite |
| Phone lintDebug | 0 errors; 8 warnings remain |
| Watch lintDebug | 0 errors; 424 warnings remain |
| Phone optimized release APK | Built successfully |
| Watch debug APK | Built successfully |
| APK signatures | Both verify; watch retains v1 and v2 signatures, phone v2 |
| Upgrade certificate | Both match the existing 2.8.5 APK signing certificate |
| Source whitespace check | `git diff --check` passed |

Signing certificate SHA-256: `a82dab8373c6d19741e5c9159181a2d8240dbd5b13f00765efe621fdc5d47d41`.

The final coordinated command completed successfully in 2 minutes 49 seconds:

```powershell
.\gradlew.bat :PhoneApp:app:testDebugUnitTest :WatchApp:app:testDebugUnitTest :PhoneApp:app:lintDebug :WatchApp:app:lintDebug :PhoneApp:app:assembleRelease :WatchApp:app:assembleDebug --console=plain
.\tools\update_dist.ps1 -WatchVersion 2.9.0 -PhoneVersion 2.9.0
```

The local build log is `validation/studio-final-build.log`. Distribution aliases, versioned APKs, SHA-256 manifests, updater metadata, and the installation ZIP are produced from these exact build artifacts. This local packaging step does not publish a GitHub release.

## Device acceptance still required

No physical phone/watch or configured emulator was available. Automated verification does not prove touchscreen behavior, small-screen fit, OEM image-picker behavior, Bluetooth transfer, ambient brightness, animation performance, or battery use on the target device.

1. Install both APKs over the existing applications and confirm project/history retention.
2. Create a blank face, import a photo, and add rotated text and shapes. Check dragging, snapping, locking, ordering, and numeric entry.
3. Undo a drag/slider gesture, redo it, leave the studio, edit another project, and reopen the original to verify its recovery draft.
4. Save without leaving the editor. Search, duplicate, and delete a saved creation in the library.
5. Export a project, rotate the phone while the system picker is open, import the result as a separate creation, and compare artwork.
6. Send a layered face over Bluetooth and verify both the correlated confirmation and visible watch rendering. Check circular clipping and actual time after midday.
7. Preview active/ambient layouts and all eight effects, then verify configured tap actions and ambient settings on the watch.

Read [FACE-STUDIO.md](FACE-STUDIO.md) for the creator workflow and [ANDROID8-INSTALL.md](ANDROID8-INSTALL.md) for installation.
