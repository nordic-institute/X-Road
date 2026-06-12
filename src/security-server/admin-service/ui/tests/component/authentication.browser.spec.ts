/*
 * The MIT License
 *
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

/**
 * 0800 — Authentication UI integration scenarios.
 *
 * Login state setup:
 *   - renderRoute does NOT install the createXrdRouter beforeEach guard. Navigating
 *     to /login renders AppLogin directly without any guard-level redirect. The
 *     /initialization/admin-user/status mock is NOT needed for login-form render —
 *     the route is always reachable.
 *   - renderRoute patches authenticated: true + session alive, but the /login route
 *     sits outside the authenticated AppBase shell. AppLogin always renders regardless
 *     of store state.
 *
 * Login error flow:
 *   - AppLogin.submit() calls loginUser(), which calls axiosAuth.post('/login').
 *     axiosAuth uses VITE_VUE_APP_AUTH_URL (= https://localhost:8888 in test mode).
 *     A 401 response triggers loginForm.addErrors($t('login.errorMsg401')), which calls
 *     setFieldError('password', ...) in XrdAppLogin. The error renders as the
 *     :error-messages prop on the v-text-field password input.
 *
 * Logout flow:
 *   - logoutUser() calls axiosAuth.post('/logout') then location.reload() in .finally().
 *     The logout endpoint is mocked with delay('infinite') so reload never fires during
 *     the assertion window. router.replace(Login) fires before the POST resolves.
 *
 * Session timeout:
 *   - renderRoute with session:'expired' boots the app with appState.sessionAlive=false.
 *     XrdApp renders XrdLogoutDialog because !loginView && !initialUserView && !isSessionAlive().
 *     Clicking OK emits 'logout' → XrdApp → AppShell onLogout, which calls
 *     logoutUser(false) + router.replace(Login) — the real chain from App.vue.logout().
 *     logoutUser(false) POSTs /logout (intercepted by logoutHangHandler so reload never fires)
 *     and router.replace resolves before the POST completes.
 *
 * Auth URL:
 *   - axiosAuth uses VITE_VUE_APP_AUTH_URL=https://localhost:8888 (from .env in test mode).
 *     Login and logout are intercepted at their absolute URLs via specHttp.untyped.
 */
import { describe, it, expect } from 'vitest';
import { page } from 'vitest/browser';
import { delay, HttpResponse } from 'msw';
import { renderRoute } from '../setup/render-route';
import { specHttp } from '../setup/spec-http';
import { worker } from '../setup/browser-setup';
import { RouteName } from '@/global';

const authBaseUrl = 'https://localhost:8888';

const loginErrorHandler = specHttp.untyped.post(`${authBaseUrl}/login`, () =>
  HttpResponse.json({ error: 'invalid credentials' }, { status: 401 }),
);

const logoutHangHandler = specHttp.untyped.post(`${authBaseUrl}/logout`, async () => {
  await delay('infinite');
  return HttpResponse.json({});
});

// Returns session-invalid so AppBase.fetchSessionStatus() does not flip sessionAlive back to true
// during the spec window (the default handler returns valid:true which would dismiss the dialog).
const sessionExpiredStatusHandler = specHttp.untyped.get('/api/v1/notifications/session-status', () =>
  HttpResponse.json({ valid: false }),
);

describe('0800 — Authentication (Browser Mode)', () => {
  // MIGRATED-FROM: 0800-ss-authentication.feature :: "Invalid password is rejected"
  it('Invalid password rejected — login error message renders, form stays visible', async () => {
    worker.use(loginErrorHandler);

    await renderRoute('/login');

    await expect.element(page.getByTestId('login-username-input')).toBeVisible();

    await page.getByTestId('login-username-input').getByRole('textbox').fill('xrd');
    await page.getByTestId('login-password-input').getByRole('textbox').fill('INVALID');
    await page.getByTestId('login-button').click();

    await expect.element(page.getByText('Wrong username or password')).toBeVisible();
    await expect.element(page.getByTestId('login-username-input')).toBeVisible();
  });

  // MIGRATED-FROM: 0800-ss-authentication.feature :: "Invalid username is rejected"
  it('Invalid username rejected — login error message renders, form stays visible', async () => {
    worker.use(loginErrorHandler);

    await renderRoute('/login');

    await expect.element(page.getByTestId('login-username-input')).toBeVisible();

    await page.getByTestId('login-username-input').getByRole('textbox').fill('INVALID');
    await page.getByTestId('login-password-input').getByRole('textbox').fill('secret123!');
    await page.getByTestId('login-button').click();

    await expect.element(page.getByText('Wrong username or password')).toBeVisible();
    await expect.element(page.getByTestId('login-username-input')).toBeVisible();
  });

  // MIGRATED-FROM: 0800-ss-authentication.feature :: "User is able to log out from security server"
  it('User can log out — logout button navigates to login route', async () => {
    worker.use(logoutHangHandler);

    const { router } = await renderRoute('/clients');

    await page.getByTestId('user-menu').click();
    await page.getByTestId('logout-button').click();

    await expect.element(page.getByTestId('login-username-input')).toBeVisible();
    expect(router.currentRoute.value.name).toBe(RouteName.Login);
  });

  // MIGRATED-FROM: 0800-ss-authentication.feature :: "Automatic logout happens when timeout passes"
  //
  // renderRoute({ session: 'expired' }) boots with appState.sessionAlive=false.
  // XrdApp renders XrdLogoutDialog. Clicking OK fires the logout event → AppShell onLogout
  // (the real default: logoutUser(false) + router.replace(Login)). logoutHangHandler
  // keeps the /logout POST pending so location.reload() never fires during the assertion.
  it('Automatic logout on timeout — OK-click navigates to login route', async () => {
    worker.use(logoutHangHandler, sessionExpiredStatusHandler);

    const { router } = await renderRoute('/clients', { session: 'expired' });

    await expect.element(page.getByTestId('dialog-title')).toBeVisible();
    await expect.element(page.getByText('Session expired')).toBeVisible();

    await page.getByTestId('dialog-save-button').click();

    await expect.element(page.getByTestId('login-username-input')).toBeVisible();
    expect(router.currentRoute.value.name).toBe(RouteName.Login);
  });
});
