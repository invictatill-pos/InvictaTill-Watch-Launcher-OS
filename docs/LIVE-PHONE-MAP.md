# Live Google Maps view on the watch

HealthSync 2.10.0 adds a live phone-screen view over the existing Bluetooth connection. It shows the actual Google Maps screen rendered by your phone, including the visible map and navigation instructions. Google Maps continues to run navigation on the phone. The watch displays images of that view; watch zoom and pan do not control Google Maps on the phone.

## Start

1. Install phone 2.10.0 and watch 2.10.0 and connect them in HealthSync. Older watches cannot receive screen frames and must be updated first.
2. On the watch, open **Live phone map** from Apps or **Settings → Phone connection**.
3. On the phone, open **Device → Google Maps on watch → Share Google Maps**, then **Start sharing**. Review the sharing instructions and approve Android's screen-capture request. Every session requires new approval.
4. When Android offers **A single app**, select **Google Maps**. On older Android versions, or if you select the entire screen, everything visible on the phone can be shared, including other apps and notifications. Keep this in mind before switching apps.
5. Open Google Maps on the phone, select a destination, and start navigation. Keep the phone unlocked and Maps visible so Android can keep providing images.
6. View the map on the watch. The image uses the full watch screen by default. Pinch or double-tap to zoom, drag to pan, and use **Fit** to show the complete phone image inside the watch's round boundary. Tap once to show or hide the controls; they hide automatically while fresh images arrive, leaving a small freshness indicator. **Help** explains the gestures. For a larger map, use landscape orientation on the phone where supported. Route changes and map controls remain on the phone.

The watch does not need its own internet connection for this feature. Google Maps on the phone needs a working route/data source; HealthSync does not provide Maps data or calculate a separate route.

## Stop and connection status

Stop sharing using the phone's Stop control, its ongoing screen-sharing notification, the watch's Stop control, or Android's capture controls. Sharing stops when Bluetooth disconnects, the phone locks/turns its screen off, or Android revokes capture. Reconnecting never silently restarts capture; approve a new session on the phone.

Screen images refresh at a limited rate over Bluetooth and can lag behind the phone. The watch labels delayed images after six seconds and clears them when sharing stops or disconnects. A slow connection or delayed frame acknowledgment stops the session with a status message. Pause screen sharing before transferring updates or other large Bluetooth files.

Single-app sharing, phone rotation, power management, protected content, and device firmware can affect capture. Some protected views may appear blank. This feature cannot bypass Android capture restrictions or keep navigation running on a locked phone.

## Data handling

Frames are bounded JPEG images kept in memory on the paired devices. HealthSync does not save a screen recording, add images to notification history, capture audio, or upload frames to a server. At most one unacknowledged frame is in flight; a queued frame from a stopped session is discarded before sending. Stopping or losing the connection invalidates that session immediately, including images still being encoded.

## Verification and device acceptance

No physical phone or watch was attached during development. Automated checks cover protocol limits, capability compatibility, session/sequence handling, and build correctness; they cannot establish capture quality, readable map labels, actual Bluetooth throughput, or firmware behavior.

Check both an older Android phone's whole-screen flow and Android 14+ single-app sharing where available. Confirm consent cancellation, selecting Maps, route movement, zoom/pan/Fit, phone rotation, phone screen lock, Android capture revocation, Bluetooth loss/reconnect, stopping from each device, and delayed-frame handling. Confirm that other apps are excluded when single-app sharing is selected and that ordinary watch sync continues while sharing.

Android documents [MediaProjection consent, foreground services, app selection, resizing, and cleanup](https://developer.android.com/media/grow/media-projection).
