# Release Guide

## Build A Signed APK Or AAB

In Android Studio:

1. Open **Build > Generate Signed Bundle / APK**.
2. Choose **Android App Bundle** for Play Store publishing or **APK** for direct sharing.
3. Create a new keystore and store it safely.
4. Select the `release` build variant.
5. Finish the wizard.

Output locations:

```text
app/build/outputs/bundle/release/
app/build/outputs/apk/release/
```

## Command Line Release Build

After creating a keystore, add signing config in `app/build.gradle.kts` or use Android Studio's signing wizard. If you generate a Gradle wrapper for command-line builds, run:

```powershell
.\gradlew.bat assembleRelease
.\gradlew.bat bundleRelease
```

## Pre-Publish Checklist

1. Replace `app/google-services.json` with the real Firebase config.
2. Confirm package name is `com.foundit.app`.
3. Enable Anonymous Auth, Firestore, Storage, FCM, Analytics, and Crashlytics.
4. Test login/session creation.
5. Test offline screen and reconnect.
6. Test file upload and camera capture.
7. Test downloads.
8. Test push notification delivery.
9. Verify release build has WebView debugging disabled.
10. Upload the `.aab` to Google Play Console.

## Google Play Publishing

1. Create an app in Google Play Console.
2. Complete app content, privacy, data safety, and store listing.
3. Upload the release `.aab`.
4. Add testers in Internal Testing.
5. Fix any pre-launch report issues.
6. Promote to Closed Testing, Open Testing, or Production.

The app should be submitted with FoundIt branding only and without visible generator, platform-builder, or wrapper references.
