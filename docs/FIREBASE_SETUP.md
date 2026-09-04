# Firebase Setup

## Create The Project

1. Go to the Firebase Console.
2. Create a project named **FoundIt**.
3. Add an Android app with package name:

```text
com.foundit.app
```

4. Download the generated `google-services.json`.
5. Replace this file:

```text
app/google-services.json
```

The current file is a compile-time placeholder so the Android project structure is complete. Real Firebase services require the downloaded file.

## Enable Services

Enable these Firebase products:

```text
Authentication
Firestore Database
Storage
Cloud Messaging
Analytics
Crashlytics
```

## Authentication

In **Authentication > Sign-in method**, enable **Anonymous** sign-in. The app signs users in anonymously through `FirebaseManager` so Analytics, Crashlytics, device tokens, and future Firestore data can be tied to a stable Firebase user session.

## Firestore

Create Firestore in production or test mode. The app currently writes FCM tokens to:

```text
device_tokens/{tokenHash}
```

Suggested starter rule for development:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /device_tokens/{tokenId} {
      allow create, update: if request.auth != null;
      allow read, delete: if false;
    }
  }
}
```

Tighten these rules before publishing if more collections are added.

## Storage

Enable Firebase Storage. The app includes the SDK and `FirebaseManager.storage` for future native uploads.

## Cloud Messaging

The app includes:

```text
FoundItMessagingService
NotificationHelper
FirebaseManager.requestMessagingToken()
```

Send test notifications from **Firebase Console > Cloud Messaging** after installing a build with the real `google-services.json`.

Optional data payload keys:

```json
{
  "title": "FoundIt",
  "body": "Your item update is ready.",
  "url": "https://find-it-copy-e35baad5.base44.app"
}
```

Finder confirmation payload:

```json
{
  "notification_type": "finder_confirmation",
  "item_name": "Blue backpack",
  "deep_link": "https://find-it-copy-e35baad5.base44.app/items/123"
}
```

Returned item payload:

```json
{
  "notification_type": "item_returned",
  "item_name": "Blue backpack",
  "deep_link": "https://find-it-copy-e35baad5.base44.app/items/123"
}
```

Mirror website notification preferences and item-status events to Supabase with `docs/notification_mirror_schema.sql`; see `docs/NOTIFICATION_MIRROR_INTEGRATION.md`.

## Crashlytics

Crashlytics collection is disabled for debug builds and enabled for release builds through manifest placeholders.

## Analytics

The app logs:

```text
screen_view
notification_permission_granted
notification_permission_denied
blocked_navigation
```

Add more events in `FirebaseManager.logEvent(...)` when native features expand.
