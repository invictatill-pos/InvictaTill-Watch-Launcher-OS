# Bluetooth internet for watch apps

HealthSync 2.9.3 adds guided setup and connection status for Android Bluetooth internet sharing. No map changes are included.

Android's Bluetooth tethering can provide internet to watch apps through the existing phone/watch pairing. HealthSync sync continues on its existing connection. Tethering uses an additional Bluetooth network profile on that pairing; the HealthSync data socket does not route other apps' internet traffic.

## Set up

1. Install both updated apps and pair the phone and watch as usual.
2. In phone HealthSync, open **Device → Internet for watch apps → Set up Bluetooth internet**. Enable mobile data or another connection the phone supports sharing.
3. Tap **Phone network settings**, then find **Hotspot & tethering** or **Mobile Hotspot and Tethering** and enable **Bluetooth tethering**. Settings names vary by device. HealthSync opens public Android network settings; enabling sharing remains a system setting.
4. In watch HealthSync, open **Settings → Phone connection → Internet from phone → Watch Bluetooth settings**. Select the same paired phone used for HealthSync and enable **Internet access**, if available.
5. Return to the watch's Internet from phone screen, or tap **Refresh watch status** on the phone. **Internet via Bluetooth** means Android has verified internet on the watch's current default Bluetooth network. Watch apps that use Android's default connection can use it.
6. To stop sharing, turn off **Bluetooth tethering** in the phone's system settings. HealthSync's **Disconnect** button pauses app sync; it does not turn off Android tethering.

Both devices must support Bluetooth network sharing (PAN). If the phone has no Bluetooth tethering setting or the watch has no Internet access option, HealthSync cannot add that firmware capability. Use the phone's Wi-Fi hotspot instead; a Wi-Fi settings shortcut is provided on the watch. Sharing availability can depend on the phone, watch firmware, mobile plan, and carrier.

## Read the status

- **Internet via Bluetooth:** Android reports an internet-capable, validated Bluetooth default network on the watch. Android does not expose the identity of the supplying Bluetooth peer here; select the same phone during setup.
- **Bluetooth · internet not verified:** the Bluetooth network is present but Android has not verified internet. Check the phone's internet connection, tethering toggle, and mobile plan.
- **Internet via Wi-Fi / mobile data / Ethernet:** the watch currently uses that network. This does not confirm Bluetooth sharing. For a device check of Bluetooth sharing, temporarily disable other watch internet connections.
- **VPN active · Bluetooth sharing not confirmed:** a VPN is the default route. HealthSync does not infer which underlying connection it uses.
- **No default internet connection / Internet status unavailable:** the watch has no selected network, or Android could not report its capabilities.

The watch screen reads its current default network while visible. The phone displays watch-reported snapshots; changes may take 20 seconds to arrive, unchanged reports refresh every minute, and snapshots expire after 90 seconds. Disconnecting HealthSync clears the phone's live report. Older watch versions show an unreported state instead of a sharing success.

## Device acceptance

No phone or watch was attached during implementation. Automated checks verify status classification, telemetry compatibility, freshness, and compilation, but cannot establish Kolabee U8 Ultra firmware support or actual traffic routing.

On your devices, enable Bluetooth tethering and the paired phone's Internet access option. Temporarily disable watch Wi-Fi/mobile data, confirm **Internet via Bluetooth**, and open an online page in a watch browser. Confirm HealthSync sync still works, then turn tethering off and confirm the watch's status and page loading reflect the loss of internet. Repeat after leaving Bluetooth range, reconnecting, and restarting either device. Test a VPN separately if you use one. Mobile data charges follow your plan.

## Platform references

- [Android Bluetooth tethering setup](https://support.google.com/android/answer/9059108)
- [Android default network state and validated internet capabilities](https://developer.android.com/develop/connectivity/network-ops/reading-network-state)
- [Public Android network settings action](https://developer.android.com/reference/android/provider/Settings#ACTION_WIRELESS_SETTINGS)
- [Public Android Bluetooth settings action](https://developer.android.com/reference/android/provider/Settings#ACTION_BLUETOOTH_SETTINGS)
