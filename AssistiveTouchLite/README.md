# Assistive Touch Lite

A tiny floating on-screen button (like iOS AssistiveTouch) for Android. Tap it
to reveal two options:

- **⏻ Power** — opens the real Android power menu (shutdown / restart), the
  same dialog you'd get from holding the physical power button.
- **🔊 Volume** — a small panel with Media +/− /mute and Ringer +/− buttons.

It's built to work around broken physical buttons.

The button auto-dims and slides to the nearest screen edge (mostly
off-screen, like Facebook's chat heads) after 3 seconds of no use. Tap the
sliver that's still visible once to bring it back fully, tap again to open
the menu. Drag it anywhere any time.

## Important things to know before you build

- **Android can't let a normal app silently reboot/shutdown the phone.**
  The only non-root way to summon the power menu is via Android's
  **Accessibility Service** API. That's why this app asks you to enable
  "Accessibility" once, in Settings — Android requires that to be a manual,
  visible step for security reasons (otherwise any app could pop up a fake
  power menu). This app's accessibility service does nothing except show
  that menu — it does not read your screen.
- Same idea for volume: it uses Android's standard volume API, no special
  root needed.
- The floating button needs the **"Display over other apps"** permission —
  also a manual one-time toggle.

## How to build the APK (recommended: Android Studio — 100% free)

1. Download and install **Android Studio** from
   `https://developer.android.com/studio` — no payment, no account
   required. It bundles everything else you need (Java, the Android SDK,
   Gradle).
2. Open Android Studio → **Open** → select the `AssistiveTouchLite` folder
   (the one containing this README).
3. Let it "Sync" (bottom status bar) — first time it may take a few
   minutes downloading the Android SDK/build tools. Just wait, don't
   cancel it.
4. Once synced, go to the menu: **Build → Build App Bundle(s) / APK(s) →
   Build APK(s)**.
5. When it finishes, click the **"locate"** link in the notification, or
   find the file at:
   `app/build/outputs/apk/debug/app-debug.apk`
6. Copy that `app-debug.apk` file to the phone (via USB cable, Google
   Drive, WhatsApp to yourself, email — anything), then tap it on the
   phone to install.
   - You'll need to allow **"Install unknown apps"** for whichever app
     you used to transfer it (Android will prompt you the first time,
     it's a one-tap toggle).

## Setting it up on the phone (one-time)

1. Open **Assistive Touch Lite**.
2. Tap **"1. Grant display over other apps permission"** → toggle it on
   for this app → go back.
3. Tap **"2. Turn on Accessibility Service"** → find "Assistive Touch
   Lite" in the list → turn it on → confirm.
4. Tap **"3. Start floating button"**.
5. A small circular button appears on screen, on top of any app. Drag it
   anywhere. Tap it (don't drag) to open the Power/Volume menu.

The floating button stays visible until you stop it from the app or
restart the phone (you'll need to reopen the app and tap Start again
after a restart — that's normal for this kind of lightweight setup).

## If you'd rather build without Android Studio (command line)

This needs slightly more technical comfort:

1. Install a JDK 17 and the Android command-line SDK tools.
2. Accept SDK licenses: `sdkmanager --licenses`
3. Install platform + build tools:
   `sdkmanager "platforms;android-34" "build-tools;34.0.0"`
4. From this project folder, generate the Gradle wrapper (needs Gradle
   installed once, e.g. via `brew install gradle` or your package
   manager): `gradle wrapper --gradle-version 8.2`
5. Then: `./gradlew assembleDebug`
6. APK will be at `app/build/outputs/apk/debug/app-debug.apk`

Android Studio is genuinely the easier route — it handles steps 1–4 for
you automatically.

## "Blocked by Play Protect" warning

This is expected for any APK installed outside the Play Store — it's not
specific to this app. Two real options:

- **Google Play Internal App Sharing** (recommended if you want zero
  scary warnings without disabling anything): requires a one-time $25
  Google Play Developer account. Upload the APK there, install via the
  private link it gives you — Play Protect trusts that path.
- **Temporarily toggle off "Scan apps with Play Protect"** in
  Settings → Security → Google Play Protect, install, then turn it back
  on. Free and immediate.

## Notes / limitations

- This is a "debug" build — fine for personal use on your wife's own
  phone, not meant for the Play Store.
- Volume buttons control Media and Ringer streams. If you need Alarm or
  Call volume too, that's an easy addition (say the word).
- On some phones (Xiaomi/MIUI, some Samsung), you may also need to enable
  an extra "Autostart" or "Display pop-up windows while running in
  background" permission in the phone's own battery/app settings for the
  bubble to stay visible reliably.
