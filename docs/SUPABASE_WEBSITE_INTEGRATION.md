# FoundIt Supabase Website Integration

This workspace contains the Android WebView project. Apply the website snippets below in the React/Base44 website source that is deployed to:

`https://find-it-copy-e35baad5.base44.app`

References checked against official Supabase docs on 2026-05-17:

- Password auth: https://supabase.com/docs/guides/auth/passwords
- Auth state events: https://supabase.com/docs/reference/javascript/auth-onauthstatechange
- Session retrieval: https://supabase.com/docs/reference/javascript/auth-getsession

## 1. Supabase Project Settings

In Supabase Dashboard:

- Enable Email provider under Authentication > Providers.
- Set Site URL to `https://find-it-copy-e35baad5.base44.app`.
- Add Redirect URLs:
  - `https://find-it-copy-e35baad5.base44.app/*`
  - your local dev URL, for example `http://localhost:5173/*`
- Configure a production SMTP provider before launch. Supabase's default email sender is only suitable for testing.

Create these website environment variables:

```env
VITE_SUPABASE_URL=https://byrkzjnnlledsuvikfwd.supabase.co
VITE_SUPABASE_PUBLISHABLE_KEY=sb_publishable_0aUQb00pgdDF4oXUeRHDjA_8ww4QhbI
```

Do not put the Supabase service role key in the React app.

## 2. Database Schema

Run `docs/supabase_users_schema.sql` in the Supabase SQL editor.

It creates:

- `public.users` for user id, email, name, and last-login summary.
- `public.user_login_events` for login history.
- RLS policies so users can read/update only their own profile.
- Auth triggers to create/update `public.users` from `auth.users`.
- `public.record_login()` RPC for recording successful logins.

For notification settings and finder item-status events, also run:

```text
docs/notification_mirror_schema.sql
```

Then apply the website snippets in `docs/NOTIFICATION_MIRROR_INTEGRATION.md`.

## 3. Install Website Dependency

```bash
npm install @supabase/supabase-js
```

## 4. Add Supabase Client

Create or replace `src/lib/supabaseClient.js`:

```js
import { createClient } from '@supabase/supabase-js';

const supabaseUrl = import.meta.env.VITE_SUPABASE_URL || 'https://byrkzjnnlledsuvikfwd.supabase.co';
const supabaseKey = import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY;

if (!supabaseUrl || !supabaseKey) {
  throw new Error('Missing Supabase environment variables');
}

export const supabase = createClient(supabaseUrl, supabaseKey, {
  auth: {
    persistSession: true,
    autoRefreshToken: true,
    detectSessionInUrl: true,
    flowType: 'implicit',
  },
});
```

`persistSession: true` stores the session in browser/WebView storage. The Android app now keeps DOM storage enabled and does not clear Web storage, so users remain logged in.

## 5. Add Auth Service

Create `src/lib/authService.js`:

```js
import { supabase } from './supabaseClient';

const appUrl = 'https://find-it-copy-e35baad5.base44.app';

function cleanEmail(email) {
  return String(email || '').trim().toLowerCase();
}

export async function signUpWithEmail({ email, password, name }) {
  const { data, error } = await supabase.auth.signUp({
    email: cleanEmail(email),
    password,
    options: {
      emailRedirectTo: `${appUrl}/auth/callback`,
      data: { name: String(name || '').trim() },
    },
  });

  if (error) throw error;
  return data;
}

export async function signInWithEmail({ email, password }) {
  const { data, error } = await supabase.auth.signInWithPassword({
    email: cleanEmail(email),
    password,
  });

  if (error) throw error;
  await recordLogin();
  return data;
}

export async function sendPasswordReset(email) {
  const { data, error } = await supabase.auth.resetPasswordForEmail(cleanEmail(email), {
    redirectTo: `${appUrl}/reset-password`,
  });

  if (error) throw error;
  return data;
}

export async function updatePassword(password) {
  const { data, error } = await supabase.auth.updateUser({ password });
  if (error) throw error;
  return data;
}

export async function signOut() {
  const { error } = await supabase.auth.signOut({ scope: 'local' });
  if (error) throw error;
}

export async function getCurrentSession() {
  const { data, error } = await supabase.auth.getSession();
  if (error) throw error;
  return data.session;
}

export async function upsertMyProfile({ name }) {
  const session = await getCurrentSession();
  if (!session?.user) throw new Error('Not signed in');

  const { data, error } = await supabase
    .from('users')
    .update({ name: String(name || '').trim() })
    .eq('id', session.user.id)
    .select()
    .single();

  if (error) throw error;
  return data;
}

export async function recordLogin() {
  const { error } = await supabase.rpc('record_login', {
    p_user_agent: navigator.userAgent,
  });

  if (error) {
    console.warn('Login recorded locally but Supabase login audit failed', error);
  }
}
```

## 6. Add Auth Provider

Create `src/auth/AuthProvider.jsx`:

```jsx
import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { supabase } from '../lib/supabaseClient';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [session, setSession] = useState(null);
  const [loading, setLoading] = useState(true);
  const [passwordRecovery, setPasswordRecovery] = useState(false);

  useEffect(() => {
    let mounted = true;

    supabase.auth.getSession().then(({ data }) => {
      if (!mounted) return;
      setSession(data.session);
      setLoading(false);
    });

    const {
      data: { subscription },
    } = supabase.auth.onAuthStateChange((event, nextSession) => {
      if (event === 'PASSWORD_RECOVERY') setPasswordRecovery(true);
      if (event === 'SIGNED_OUT') setPasswordRecovery(false);
      setSession(nextSession);
      setLoading(false);
    });

    return () => {
      mounted = false;
      subscription.unsubscribe();
    };
  }, []);

  const value = useMemo(
    () => ({
      session,
      user: session?.user ?? null,
      loading,
      passwordRecovery,
      setPasswordRecovery,
    }),
    [session, loading, passwordRecovery]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth must be used inside AuthProvider');
  return value;
}
```

Wrap the app once, usually in `src/main.jsx`:

```jsx
import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import { AuthProvider } from './auth/AuthProvider';

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <AuthProvider>
      <App />
    </AuthProvider>
  </React.StrictMode>
);
```

## 7. Replace Old Auth Calls

Remove old backend imports and API calls such as:

```js
import { User } from 'base44/entities';
import { createClient } from '../oldBackend';
await User.login();
await User.signup();
await api.post('/login');
await api.post('/signup');
await api.post('/forgot-password');
```

Replace them with:

```js
import {
  signInWithEmail,
  signUpWithEmail,
  sendPasswordReset,
  updatePassword,
  signOut,
} from './lib/authService';
```

Example login submit:

```js
async function handleLoginSubmit(event) {
  event.preventDefault();
  setError('');
  setLoading(true);

  try {
    await signInWithEmail({ email, password });
    navigate('/');
  } catch (error) {
    setError(error.message || 'Login failed. Please try again.');
  } finally {
    setLoading(false);
  }
}
```

Example signup submit:

```js
async function handleSignupSubmit(event) {
  event.preventDefault();
  setError('');
  setLoading(true);

  try {
    await signUpWithEmail({ email, password, name });
    setSuccess('Check your email to confirm your account.');
  } catch (error) {
    setError(error.message || 'Signup failed. Please try again.');
  } finally {
    setLoading(false);
  }
}
```

Example forgot-password submit:

```js
async function handleForgotPasswordSubmit(event) {
  event.preventDefault();
  setError('');
  setLoading(true);

  try {
    await sendPasswordReset(email);
    setSuccess('Password reset email sent.');
  } catch (error) {
    setError(error.message || 'Could not send reset email.');
  } finally {
    setLoading(false);
  }
}
```

Example reset-password submit:

```js
async function handleResetPasswordSubmit(event) {
  event.preventDefault();
  setError('');
  setLoading(true);

  try {
    await updatePassword(newPassword);
    setSuccess('Password updated.');
    navigate('/');
  } catch (error) {
    setError(error.message || 'Could not update password.');
  } finally {
    setLoading(false);
  }
}
```

## 8. Production Checklist

- Use only `VITE_SUPABASE_PUBLISHABLE_KEY` in the website.
- Keep RLS enabled on all user-owned tables.
- Add custom SMTP before production password reset/signup emails.
- Do not clear `localStorage` or WebView WebStorage on app startup, because Supabase Auth stores browser sessions there.
- Deploy the React/Base44 website first; the Android app will load the deployed site directly.
