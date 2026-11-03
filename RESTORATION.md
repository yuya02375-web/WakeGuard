# WakeGuard Android source restoration

The project root is restored from the repository's `.wakeguard-parts` archive. The Android `app/` module is based on the 2.9.5 source ZIP referenced by the successful GitHub Actions run `41211969771` and then updated here as version 2.9.6.

The existing `.git` history and `.github/workflows` files are retained. Work is on branch `codex/restore-v295`.

## Build

From this directory on Windows:

```powershell
.\gradlew.bat assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. The build uses Android SDK 36 and Java 17.

## Stable installable APK

The debug APK uses an Android debug certificate and cannot update an existing install signed with the permanent IGNIDO Wake key. Use the **Build IGNIDO Wake 2.9.6 Stable APK** workflow to build the release variant and sign it with the `WAKEGUARD_*` Actions secrets described in `SIGNING_SETUP.md`. The workflow checks the app ID, version, and expected stable certificate fingerprint before publishing the `WakeGuard-2.9.6-stable` artifact.

The workflow runs on pushes to `codex/restore-v295` that change the workflow, `.wakeguard-parts/`, or `WakeGuard-2.9.6-app-overlay.zip`. It reconstructs the Gradle project, downloads the verified 2.9.5 source artifact from Actions run `41211969771`, applies the 2.9.6 overlay, and signs with the existing stable key. The source artifact is scheduled to expire on 2027-01-29, so this CI bootstrap is for the current 2.9.6 build unless the source archive is preserved elsewhere. Do not install the local debug APK as an update to an existing IGNIDO Wake install.

## Icon design files

Three new vector concepts are in `assets/icon-concepts-v296/`. “夜明けの時刻” is selected for the launcher. The adaptive foreground and Android 13 monochrome layers use new vector resources in `app/src/main/res/drawable/`.

No device install, launch, or screenshot capture is part of this restoration.
