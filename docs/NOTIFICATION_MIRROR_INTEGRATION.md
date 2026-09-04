# FoundIt Notification Mirror Integration

This workspace contains the Android app shell, not the website source. Apply these snippets in the FoundIt website so finder notification settings and item-status events are mirrored to Supabase.

## 1. Run The Schema

Run this file in the Supabase SQL editor:

```text
docs/notification_mirror_schema.sql
```

It creates:

- `app_notification_preferences` for each user's notification options.
- `item_status_notifications` for mirrored finder events when an item is confirmed or returned.
- `upsert_notification_preferences(...)` and `mirror_item_status_notification(...)` RPCs for website writes.

## 2. Add Website Service

Create `src/lib/notificationMirrorService.js` in the website:

```js
import { supabase } from './supabaseClient';

function userIdFrom(user) {
  return String(user?.id || user?.base44_user_id || user?.user_id || '').trim();
}

function userEmailFrom(user) {
  return String(user?.email || '').trim().toLowerCase() || null;
}

export async function saveNotificationPreferences(user, preferences) {
  const userId = userIdFrom(user);
  if (!userId) throw new Error('User id is required');

  const { error } = await supabase.rpc('upsert_notification_preferences', {
    p_user_id: userId,
    p_email: userEmailFrom(user),
    p_push_enabled: preferences.pushEnabled !== false,
    p_notify_finder_confirmation: preferences.finderConfirmation !== false,
    p_notify_item_returned: preferences.itemReturned !== false,
    p_metadata: {
      source: 'website',
      updatedAt: new Date().toISOString(),
    },
  });

  if (error) throw error;
}

export async function mirrorFinderNotification({ finder, item, type, sourceStatus, payload }) {
  const finderId = userIdFrom(finder);
  if (!finderId) throw new Error('Finder user id is required');

  const { data, error } = await supabase.rpc('mirror_item_status_notification', {
    p_user_id: finderId,
    p_email: userEmailFrom(finder),
    p_item_id: String(item?.id || item?.item_id || '').trim() || null,
    p_item_title: String(item?.title || item?.name || item?.thing_name || '').trim() || null,
    p_notification_type: type,
    p_source_status: sourceStatus || null,
    p_payload: payload || {},
  });

  if (error) throw error;
  return data;
}

export function mirrorFinderConfirmation(args) {
  return mirrorFinderNotification({
    ...args,
    type: 'finder_confirmation',
    sourceStatus: args.sourceStatus || 'confirmed',
  });
}

export function mirrorItemReturned(args) {
  return mirrorFinderNotification({
    ...args,
    type: 'item_returned',
    sourceStatus: args.sourceStatus || 'returned',
  });
}
```

## 3. Add The Website Option

Add two toggles wherever users manage notification settings:

```jsx
import { saveNotificationPreferences } from './lib/notificationMirrorService';

async function handleNotificationSettingsSubmit(user, values) {
  await saveNotificationPreferences(user, {
    pushEnabled: values.pushEnabled,
    finderConfirmation: values.finderConfirmation,
    itemReturned: values.itemReturned,
  });
}
```

Use these defaults for a new user:

```js
{
  pushEnabled: true,
  finderConfirmation: true,
  itemReturned: true,
}
```

## 4. Mirror The Two Item Events

When the owner confirms that the finder has the thing:

```js
import { mirrorFinderConfirmation } from './lib/notificationMirrorService';

await mirrorFinderConfirmation({
  finder,
  item,
  payload: {
    ownerId: owner?.id,
    itemId: item?.id,
  },
});
```

When the thing is returned:

```js
import { mirrorItemReturned } from './lib/notificationMirrorService';

await mirrorItemReturned({
  finder,
  item,
  payload: {
    ownerId: owner?.id,
    itemId: item?.id,
  },
});
```

## 5. Push Payloads For Android

The Android app now recognizes these FCM data payloads:

```json
{
  "notification_type": "finder_confirmation",
  "item_name": "Blue backpack",
  "deep_link": "https://find-it-copy-e35baad5.base44.app/items/123"
}
```

```json
{
  "notification_type": "item_returned",
  "item_name": "Blue backpack",
  "deep_link": "https://find-it-copy-e35baad5.base44.app/items/123"
}
```

Send the push only when the mirrored `item_status_notifications.should_notify` value is `true`. The SQL mirror records the event; the actual FCM send should happen from a trusted backend or Supabase Edge Function, not directly from the browser.
