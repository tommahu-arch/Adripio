# Adripio

Personal fork of the Android keyboard HeliBoard (https://github.com/heliborg/heliboard, GPL-3.0).
Fork: https://github.com/tommahu-arch/Adripio (`origin`); upstream is `heliborg/heliboard` (`upstream`).
Long-lived branch: `main`. Pull upstream with `git fetch upstream && git merge upstream/main`.

## Licence and rename rules
- Stays GPL-3.0. Keep upstream copyright/licence notices; mark modified files as modified.
- Rename is cosmetic: `applicationId` is `io.adrip.keyboard`, labels read "Adripio". The Kotlin/Java package
  and namespace stay `helium314.keyboard`. Do not mass-rename: it keeps upstream merges clean.
- Provider authorities are `io.adrip.keyboard.clipprovider` and `io.adrip.keyboard.provider`.
  Changing them would clash with an installed HeliBoard otherwise.

## One app, one key
Every build type (debug, debugNoMinify, release, nouserlib, runTests) has the same package (no `.debug`
suffix), the same labels and the same signing key, so `adb install -r` of any APK updates the other in place
and keeps the data. The `src/debug*` resource overrides were removed for that reason; a lower versionCode
cannot be installed over a higher one.
- Key: `~/.android/adripio-release.jks`, alias `adripio`; passwords in the gitignored `keystore.properties`
  at the repo root (or `local/`). Never commit either. Back both up: losing the key means users can't update.
- Without `keystore.properties`, release is unsigned and debug uses the default debug key.
- Check: `apksigner verify --print-certs <apk>`; SHA-256 of the cert must be `81:92:0D:AC...CB:7D:C1`.

## Build (Arch Linux)
JDK 17, Android SDK at `~/android/sdk` (`local.properties`), platform 37, NDK 28.0.13004108 (native code).
`./gradlew assembleDebug` -> `app/build/outputs/apk/debug/Adripio_<version>-debug.apk`;
`./gradlew assembleRelease`; unit tests `./gradlew testDebugUnitTest`.
Install with `adb install -r --user 0 <apk>`.
