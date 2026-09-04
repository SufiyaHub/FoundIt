# Base44 Prompt: Make FoundIt Supabase-Ready

Paste this into Base44's AI/code assistant from the app editor.

```text
Update this app to use Supabase as the backend for authentication, user profile storage, login tracking, generated project/application records, and real-time cloud synchronization.

Important architecture:
- Keep Base44 services for the existing AI generation workflow.
- Do not remove or rewrite the whole app.
- Replace only the old authentication/user storage backend with Supabase.
- Keep the frontend UI stable.
- Use Supabase Auth for email login, signup, password reset, and persistent sessions.
- Use Supabase PostgreSQL tables for users, login events, generated project records, and application data.

Supabase config:
- Project URL: https://byrkzjnnlledsuvikfwd.supabase.co
- Publishable key: sb_publishable_0aUQb00pgdDF4oXUeRHDjA_8ww4QhbI

Create a Supabase client file:

src/lib/supabaseClient.js

Code:

import { createClient } from '@supabase/supabase-js';

const supabaseUrl = 'https://byrkzjnnlledsuvikfwd.supabase.co';
const supabaseKey = 'sb_publishable_0aUQb00pgdDF4oXUeRHDjA_8ww4QhbI';

export const supabase = createClient(supabaseUrl, supabaseKey, {
  auth: {
    persistSession: true,
    autoRefreshToken: true,
    detectSessionInUrl: true,
  },
});

Replace old login/signup/forgot-password logic with:

Login:
await supabase.auth.signInWithPassword({ email, password });

Signup:
await supabase.auth.signUp({
  email,
  password,
  options: {
    data: { name },
  },
});

Forgot password:
await supabase.auth.resetPasswordForEmail(email, {
  redirectTo: 'https://find-it-copy-e35baad5.base44.app/reset-password',
});

Reset password:
await supabase.auth.updateUser({ password: newPassword });

Logout:
await supabase.auth.signOut();

Session:
Use supabase.auth.getSession() on app startup.
Use supabase.auth.onAuthStateChange() to keep the UI synchronized.

After a successful login, call:

await supabase.rpc('record_login', {
  p_user_agent: navigator.userAgent,
});

For user profile:
- Read from public.users where id equals the current Supabase user id.
- Update only the current user's profile.

For generated records and app data:
- Insert generated outputs into public.generated_project_records.
- Store user-owned app data in public.application_data.
- Use Supabase realtime subscriptions where the UI needs live updates.

Error handling:
- Show clear messages for invalid email/password, signup errors, reset email failures, and network errors.
- Do not expose service role keys.
- Do not store passwords manually.

After implementation:
- Remove old auth imports and calls.
- Keep Base44 AI workflow calls unchanged.
- Publish the app.
```

After Base44 publishes the app, test a new signup at:

`https://find-it-copy-e35baad5.base44.app`

Then verify:

- Supabase > Authentication > Users
- Supabase > Table Editor > users
- Supabase > Table Editor > user_login_events
- Supabase > Table Editor > generated_project_records
- Supabase > Table Editor > application_data
