# HealthSync Face Studio

Face Studio in HealthSync 2.9.0 creates portable watch faces on your phone and renders them in the HealthSync watch launcher. Open **Faces → Creator**. Install both 2.9.0 APKs before sending designs with custom artwork layers.

## Create from scratch

1. In **Project**, choose a blank analog, digital, or hybrid project. Blank projects contain a clock and no decorative markers, date, or widgets. You can also apply any of the six starter designs.
2. Set the face name, creator, and description. Use **Background** for a solid color, two-color gradient, or personal photo. Imported photos are centered, cropped to a square, and optimized to 320 × 320. Photo dimming and gradient direction are adjustable.
3. In **Dial**, change marker counts, numerals, radius, tick dimensions, and colors. In **Hands**, choose hand silhouettes and adjust each hand's size, tail, color, and seconds visibility.
4. In **Time & Date**, set typography, formats, colors, and exact positions. In **Widgets**, enable heart rate, steps, and battery, then set each widget's style, radius, position, and colors.

## Compose artwork

**Layers** adds text, panels, circles/ellipses, and lines. Rows at the top draw above lower rows. Custom layers draw above the standard clock, date, dial, and widgets. Use Forward/Backward to change their order. Duplicate, hide, lock, and delete controls operate on individual layers.

Tap artwork in the canvas to select it. Drag an unlocked layer to place it; Snap rounds its position to the 5% grid. Guides include a circular margin reference. The inspector provides numeric positions, shape dimensions, rotation, opacity, typography, hex colors, and horizontal/vertical centering. Locked layers remain selectable and can be unlocked from their row.

Dimensions use the same 320-unit design space in the phone preview and watch renderer. X and Y indicate the layer's center as a percentage of the full canvas. Text size controls its rendered dimensions; shape width and height control their bounds. Round and Square switch the preview viewport. The watch's physical display determines the actual visible area.

## Motion and ambient design

**Effects** offers eight decorative animations, speed/scale/position/color controls, watch tap color cycling, and a layout toggle. Decorative ECG and equalizer graphics are visual effects. Canvas taps select artwork; configured tap actions run on the watch.

**Always-on** configures a dimmed active layout, monochrome layout, or minimal time layout. Brightness and background, seconds, minor ticks, and widget visibility are adjustable. Each custom layer independently controls whether it appears in ambient mode. Animations stop in ambient mode. Actual always-on availability depends on the watch and its settings.

## Test, recover, and transfer

**Test** changes preview time and enables explicitly labeled example battery, step, and heart-rate values. These values are design simulations and are never saved as health records. Without example values, health widgets display unavailable placeholders. The panel also shows package size and validation/layout warnings.

Undo/Redo supports recent document changes and treats a slider gesture or canvas drag as one edit. Drafts automatically save on the phone after editing pauses. Creator resumes the latest draft; editing a saved face recovers its own draft. Recovery retains the 20 most recent projects. Saving adds or updates a face in **My Creations** and keeps the editor open. The library provides search, sorting, duplication, and confirmed deletion.

The top-right project menu imports or exports `.hswf` JSON files and duplicates the current project. Import creates a separate creation with new identifiers, preserving existing saved designs. Invalid or oversized files produce an error. Packages are limited to 512 KB and 128 custom layers; complex or animated designs may consume more battery or render more slowly on the watch.

**Send to watch** saves a snapshot, sends it over Bluetooth, and waits for a matching watch acknowledgment. Custom layers require HealthSync Watch 2.9.0 (code 34) or newer. A queued Bluetooth message is not reported as a confirmed installation. Validate readability, circular edge clipping, ambient brightness, and tap actions on the physical watch before relying on a design.
