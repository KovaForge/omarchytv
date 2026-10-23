# Fork notes

Public fork of [gitlab.com/piermark/omarchytv](https://gitlab.com/piermark/omarchytv) (MIT).
Upstream `LICENSE`, `NOTICE` and `THIRD-PARTY.md` credits are unchanged. This file is the fork note.

The launcher in `launcher/` now installs on Android 6.0 (API 23). `compileSdk` and `targetSdk` stay at 34.
`minSdk` was 26 (Android 8.0).

## Why API 23

The sets this fork is aimed at are the 2015 Sony BRAVIA Android TVs KD-55X8500C and KD-49X8300C
(`BRAVIA_ATV`, MediaTek, about 1.5 GB RAM). They shipped with Android 5.0. Sony's public update notes
for that platform describe a move to Android 6.0. A later Android 7.0 build is possible on some regions
and is not confirmed for these two models. There is no device in hand, so the floor is API 23 rather
than API 21. A set that never took the Android 6 update still cannot install this APK.

## What changed

- `minSdk` 26 to 23. Version code and version name are unchanged (`18`, `2.10.2-omarchytv`).
- `androidx.core` is used so the Caskaydia font loads on Android 6. `Resources.getFont` and
  `android:fontFamily="@font/..."` are API 26 and crash the home screen on older TVs. Layouts no longer
  set that attribute. A missing font becomes the system monospace.
- `java.time` is not used. ISO-8601 timestamps are parsed in plain Java (`link/Iso.java`).
- `java.util.Base64` (API 26) in the link crypto is a small encoder/decoder with the same alphabet as the
  JDK encoders, so the existing test vectors still match and Android 6 can pair.
- `java.nio.file.Files`, `Map.removeIf` and `Math.floorMod` (API 24 or 26) have local replacements.
- `Arrays.sort(T[], Comparator)` (API 24) in the cast file cache uses `Collections.sort`.
- `java.util.function.Consumer` (API 24) is a small local interface (request log, web-app edits).
- `URLConnection.getContentLengthLong`, `Configuration.getLocales` and `GradientDrawable.getColor`
  are API 24. Android 6 uses the int content length, the single `locale` field, and leaves shape
  colours from XML alone. Downloaded cast images are still capped while they are read.
- Network callbacks: the Handler form is API 26, the single-argument default callback is API 24.
  Android 6 uses `registerNetworkCallback`. If the TV rejects that too, the old connectivity broadcast
  is used. The Wi-Fi label still refreshes on the minute tick if both fail.
- WebView: Android 6 calls `shouldOverrideUrlLoading(WebView, String)` and
  `shouldInterceptRequest(WebView, String)`. Those overloads are implemented. The saver still falls
  back to the native logo when WebView is missing or too old to run tte.js. A web app with no WebView
  closes with "Not available on this TV" instead of crashing.
- `WebView.getCurrentWebViewPackage` and `PixelCopy` stay behind an API 26 check. On Android 6 there is
  no WebView version toast and no pixel-copy black-frame check. The native logo is already on screen.
- HDMI: see below.
- No notification channels and no foreground-service type are used. Omarchy Link is a plain started
  service. That is valid on Android 6. On Android 8 and later the process is in the foreground while
  the launcher is the home screen, which is the same as before.

## HDMI and an Apple TV

Workspace 2 and the Inputs menu switch inputs with `TvContract.buildChannelUriForPassthroughInput`
plus `TvInputInfo.EXTRA_INPUT_ID`. Both have existed since Android 5.0, so they are available on
Android 6.

`TvInputInfo.getParentId()` is Android 7. That is how newer TVs name a CEC device ("Apple TV") as a
child of an HDMI port. Calling it on Android 6 crashes the row. On API 23 the launcher lists passthrough
HDMI ports (or a passthrough input whose label contains "HDMI") and does not ask for a parent id.
The row title is the port label, not the CEC product name. Picking it still sends the passthrough URI
so the TV can switch to that HDMI input.

If the TV reports every port disconnected, the row still shows the ports. A wrong state must not hide
the Apple TV's input. If the implicit intent does not resolve, the launcher tries the input service's
package, then `com.sony.dtv.tvx`, then `com.mediatek.wwtv.tvcenter` (the 2015 MediaTek tuner named in
`config.xml`).

On Android 7 and later the CEC name is still shown when the TV provides a child input. That path is
unchanged.

## What would change for API 21 or 22

Only if a set is still on Android 5.0 or 5.1. Not done in this fork:

- `Context.getSystemService(Class)`, `Resources.getColor(int, Theme)`, `Activity.requestPermissions`
  and `Activity.checkSelfPermission` are API 23. The string form of `getSystemService`, `getColor(int)`,
  and install-time permissions would be required. The location prompt is already skipped below API 26.
  On API 21/22 the Wi-Fi name is readable without a runtime prompt.
- `ConnectivityManager.getActiveNetwork()` is API 23. API 21/22 need `getActiveNetworkInfo()`.
- `android:drawableTint` in `styles.xml` is API 23. API 21/22 would ignore it or need a manual tint.
- `TvInputManager`, passthrough channel URIs, leanback launch intents and vector drawables are API 21,
  so the HDMI row itself would not need a new design.
- Media3 1.4, RecyclerView and DynamicAnimation already allow API 21. The cast player could stay.
- `AppOpsManager.OPSTR_GET_USAGE_STATS` is API 23. Usage access would need a different check, or to stay off.

## Debloat example

`examples/sony-bravia-2015/debloat.txt` is an untested starting point copied down from
`examples/sony-kd55xe9005/debloat.txt`. It disables promo, demo, bug-report and a few store apps only.
It does not list anything whose name touches tvinput, HDMI, braviasync, CEC, input switching, the
remote, or a Sony / MediaTek system service. Do not run it blindly. Disable one package, then test
Home, the remote, HDMI (Apple TV) and Live TV.

## Untested on real hardware

Nothing in this fork was installed on a KD-55X8500C, a KD-49X8300C, or any other TV. Checks were
`./gradlew :app:lintDebug`, `:app:testDebugUnitTest` and `:app:assembleDebug` on a build machine.
In particular these are untested on Android 6:

- HDMI switching and CEC names (there is no CEC name on Android 6)
- Live TV when `com.sony.dtv.tvx` is absent
- WebView and the animated screensaver (expect the native logo if the system WebView is older than 72)
- Cast / ExoPlayer, Omarchy Link pairing, and the crash watchdog
- The debloat list
- Memory behaviour at 1.5 GB RAM

## Sideload

On the TV: Settings, Network, turn on remote debugging (the menu name varies; it is the ADB network
option). Note the TV's IP address.

```sh
adb connect <ip>:5555
adb install -r launcher/app/build/outputs/apk/debug/app-debug.apk
```

Then press Home and choose OmarchyTV. If the stock home takes over after a reboot, the launcher asks
once. It does not change the home app by itself. To put the stock home back, pick it in Settings, or
clear OmarchyTV as the home app over adb.

`adb install -r` replaces an existing debug build signed with the same key. A release build signed
with a different key will not install over this debug APK; uninstall first if you mix them.
