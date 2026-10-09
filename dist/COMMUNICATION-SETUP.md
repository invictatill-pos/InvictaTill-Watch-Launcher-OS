# HealthSync communication setup — Watch 2.4.0 / Phone 2.3.0

Prepared 9 October 2026. Install both new APKs as updates, then open both apps and connect the watch in the phone's Device tab.

## Caller names and answering

1. On the phone, grant **Phone** permission for call alerts and **Contacts** for saved caller names. Enable HealthSync **Notification Access** for messages and additional caller information from the phone's dialer.
2. On Android 12 or newer, open **HealthSync Settings → Watch Calls → Enable watch call controls**. Confirm the connected watch in Android's companion device setup. This enables call control through Android's Telecom service while retaining your existing phone dialer.
3. On the watch, open **Settings → Watch notifications → Incoming call alerts** and enable the channel. On Android 14 or newer, also select **Allow incoming call screen** and allow HealthSync's full-screen call alerts.
4. Make a test phone call from a saved contact. The watch should show the contact's name, available number, and **Answer / Decline** controls. Controls remain disabled if the phone has not granted their capability.

Android 8–11 phones retain public Android answer controls with the granted call permission. Decline/end requires Android 9 or newer. Audio/mute controls require the associated companion service on Android 12 or newer. Multiple calls on the legacy phone path disable ambiguous controls and direct you to the phone. Hidden or unavailable caller numbers display **Unknown caller**; a name cannot be recovered if the phone does not expose identity information.

## Talking through the watch

The watch must have a working microphone, speaker, and firmware that exposes a Bluetooth **Calls** connection (HFP) to the phone. HealthSync's Bluetooth data connection alone does not supply call audio.

1. In the phone's Android **Bluetooth settings**, select this watch and enable **Calls / Phone calls** if available. Some watches use a separate Bluetooth identity for calling; this release verifies the same device address as HealthSync's connected watch. A separate identity is not automatically selected.
2. Answer a call from the watch. If Android already reports the watch's supported call connection, HealthSync requests that audio route after the phone confirms answering.
3. The active call screen shows the actual phone-confirmed audio route. **Use watch audio** becomes available when the watch's call connection is detected. **Speaker**, **Mute / Unmute**, **Use phone audio**, and **End call** update only after the phone confirms them.

If **Calls** is absent or **Use watch audio** stays disabled, the phone has not exposed a usable watch call connection. You can still receive alerts and use supported call controls; audio stays with the phone or its current headset. No voice is recorded or streamed through the fitness data channel. Another connected Bluetooth headset is labeled separately and is never automatically selected as the watch.

This uses Android's [companion InCallService](https://developer.android.com/reference/android/telecom/InCallService) and [supported Bluetooth call routing](https://developer.android.com/reference/android/telecom/InCallService#requestBluetoothAudio(android.bluetooth.BluetoothDevice)). Call commands are tied to the current call identity. Sending a request is not treated as success; Android must confirm it, otherwise the watch displays an error or timeout.

## Screen-off messages and popups

On the phone, enable Notification Access and select message sources in **Alerts → Filters** or **Settings → Notification Apps**. On the watch, open **Settings → Watch notifications**:

- Enable **Allow message notifications**.
- Leave **Show new message popup** and **Wake screen for new alerts** on, or choose your preferences.
- Use **Message sound & vibration** for Android's channel controls.
- Separately grant **Allow watch notification access** to include apps running on the watch itself.

New readable messages on an unlocked Android 8 watch request a brief screen wake and open their details. The automatic popup returns after 20 seconds of inactivity; reading from the inbox stays open. On newer Android watches, background message delivery uses Android's private high-priority banner when automatic launching is restricted. Tap that banner to read. DND, disabled channels, notification permission, secure locks, and firmware power management are respected. Ordinary messages do not use full-screen call permission. Android documents these [background activity restrictions](https://developer.android.com/guide/components/activities/background-starts).

Messages show app, sender, conversation name when available, expanded text, and time. The persistent inbox holds up to 100 messages for seven days. Reconnecting restores currently active messages quietly. Identical content and background progress do not repeatedly wake or vibrate the watch. Phone VoIP call notifications can appear as ordinary alerts when their app exposes them; cellular Answer/Decline controls apply to calls managed by Android Telecom.

## Replies and reconnects

Reply is available only for an active phone notification that offers a freeform reply action. **Sending…** waits for the phone. **Reply sent to app** means Android accepted the originating app's reply action; it does not prove network delivery or that the recipient read it. Explicit failures can be retried. An unconfirmed timeout says to check the phone, avoiding an accidental duplicate send.

Removing a phone notification, disabling its app, revoking notification access, or disconnecting disables stale reply targets. Saved text remains readable. Reconnecting refreshes valid actions and clears stale system copies without generating another alert. Ending a call while disconnected is reconciled on reconnect.

## Physical device check

No physical watch or phone was connected during this build. Confirm these behaviors on your devices before relying on them:

1. Saved contact, unknown number, hidden number, incoming call, answer on phone, answer on watch, decline, end, and missed call.
2. Actual two-way speech through the watch; switch between watch, phone, and speaker; mute/unmute; another headset connected at the same time.
3. Screen-off message, screen-off incoming call, watch locked/unlocked, DND on/off, popup/wake toggles off, channel vibration off, and notification access revoked.
4. Supported chat replies, expired notifications, app filters changed while connected, Bluetooth disconnect during a request, reconnect after messages/calls end, and app/process restart.
5. Battery use with your normal volume, vibration, brightness, and alert traffic. Wake locks are brief; call screen retention ends with the call screen.
