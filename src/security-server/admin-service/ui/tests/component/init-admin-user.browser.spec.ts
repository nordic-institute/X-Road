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
 * 0090 — Initial admin user creation (bootstrap) UI integration scenarios.
 *
 * Bootstrap state setup:
 *   - The "Bootstrap view on login URL" and "Bootstrap view on arbitrary URL" specs use
 *     renderRoute({ bootstrap: true }), which installs the real createXrdRouter beforeEach
 *     guard and boots unauthenticated. The guard-redirect chain is exercised end-to-end:
 *     /login → guard detects isAdminUserCreationRequired → redirects to /initial-admin-user;
 *     /clients → guard finds unauthenticated → redirects to /login → guard on /login detects
 *     isAdminUserCreationRequired → redirects to /initial-admin-user.
 *   - The "Bootstrap view on admin user URL" spec navigates directly; no guard redirect needed.
 *   - The remaining specs (password validation, success flow) use normal renderRoute (authenticated)
 *     so the form renders without guard interference.
 *
 * Password validation — client vs server (FINDING):
 *   - The vee-validate schema on InitialAdminUserView is:
 *       password: 'required|min:6|max:255|confirmed:@passwordConfirm'
 *     "secret" (6 chars) satisfies min:6, so the submit button is ENABLED and the form
 *     submits. The weak-password rejection comes from the SERVER (400 response with ErrorInfo).
 *     There is no client-side password STRENGTH check beyond min:6 length.
 *   - This spec asserts only the error DISPLAY on a mocked 400. The real complexity rule
 *     lives at the API tier.
 *   - API coverage gap: password complexity policy is not validated by any UI-integration
 *     test. It belongs at the API tier (see audit: 0090 API slice, slice 42 area).
 *
 * Confirmation mismatch:
 *   - vee-validate confirmed:@passwordConfirm fires client-side when password ≠
 *     passwordConfirm. meta.valid is false → submit button is disabled. Pure UI, no
 *     server call required.
 *
 * Strong password succeeds:
 *   - POST /initialization/admin-user returns 201 (mocked). InitialAdminUserView calls
 *     router.replace({ name: RouteName.Login }) on success. Real bootstrap validation is
 *     a facade/hurl concern — not tested here.
 *
 * 4xx rejection suppression:
 *   - createInitialAdminUser() ends with .catch(addError). addError() shows the
 *     notification but also returns Promise.reject(err), which propagates and surfaces as
 *     an unhandled rejection. The error-path specs suppress these expected 4xx rejections
 *     to keep the suite exit code clean.
 */
import { describe, it, expect } from 'vitest';
import { page } from 'vitest/browser';
import { HttpResponse } from 'msw';
import { renderRoute } from '../setup/render-route';
import { specHttp, validateBody } from '../setup/spec-http';
import { RouteName } from '@/global';

const adminUserCreationRequiredHandler = specHttp.get('/initialization/admin-user/status', ({ response }) =>
  response(200).json({ admin_user_creation_required: true }),
);

const adminUserCreatedHandler = specHttp.post('/initialization/admin-user', ({ response }) =>
  response(201).empty(),
);

const adminUserWeakPasswordHandler = specHttp.post('/initialization/admin-user', ({ response }) =>
  response(400).json({ status: 400, error: { code: 'weak_password' } }),
);

validateBody(
  {
    type: 'object',
    required: ['admin_user_creation_required'],
    properties: { admin_user_creation_required: { type: 'boolean' } },
  },
  { admin_user_creation_required: true },
);

function suppressAxios4xxRejection(): void {
  const handler = (e: PromiseRejectionEvent) => {
    const status: number | undefined = (e.reason as { response?: { status?: number } })?.response?.status;
    if (status !== undefined && status >= 400 && status < 500) {
      e.preventDefault();
      window.removeEventListener('unhandledrejection', handler);
    }
  };
  window.addEventListener('unhandledrejection', handler);
}

describe('0090 — Initial admin user (bootstrap) (Browser Mode)', () => {
  // MIGRATED-FROM: 0090-ss-initial-admin-user.feature :: "Bootstrap view is shown when navigating to login URL"
  //
  // bootstrap:true installs the real createXrdRouter guard + boots unauthenticated.
  // Guard path: to.name === Login → isAdminUserCreationRequired() → true → redirect to InitialAdminUser.
  it('Bootstrap view on login URL — guard redirects /login to InitialAdminUser', async () => {
    const { router } = await renderRoute('/login', {
      msw: [adminUserCreationRequiredHandler],
      bootstrap: true,
    });

    expect(router.currentRoute.value.name).toBe(RouteName.InitialAdminUser);
    await expect.element(page.getByText('Create Administrator Account')).toBeVisible();
    await expect.element(page.getByTestId('admin-username-input')).toBeVisible();
  });

  // MIGRATED-FROM: 0090-ss-initial-admin-user.feature :: "Bootstrap view is shown when navigating to an arbitrary URL"
  //
  // bootstrap:true installs the real createXrdRouter guard + boots unauthenticated.
  // Guard path: /clients → unauthenticated → redirect to Login → Login guard →
  // isAdminUserCreationRequired() → true → redirect to InitialAdminUser.
  it('Bootstrap view on arbitrary URL — guard redirects /clients to InitialAdminUser', async () => {
    const { router } = await renderRoute('/clients', {
      msw: [adminUserCreationRequiredHandler],
      bootstrap: true,
    });

    expect(router.currentRoute.value.name).toBe(RouteName.InitialAdminUser);
    await expect.element(page.getByText('Create Administrator Account')).toBeVisible();
    expect(page.getByTestId('client-name').query()).toBeNull();
  });

  // MIGRATED-FROM: 0090-ss-initial-admin-user.feature :: "Bootstrap view is shown when navigating directly to admin user URL"
  it('Bootstrap view on admin user URL — form renders on direct navigation', async () => {
    await renderRoute('/initial-admin-user', {
      msw: [adminUserCreationRequiredHandler],
    });

    await expect.element(page.getByText('Create Administrator Account')).toBeVisible();
    await expect.element(page.getByTestId('admin-username-input')).toBeVisible();
    await expect.element(page.getByTestId('admin-password-input')).toBeVisible();
    await expect.element(page.getByTestId('admin-confirm-password-input')).toBeVisible();
    await expect.element(page.getByTestId('admin-user-save-button')).toBeVisible();
  });

  // MIGRATED-FROM: 0090-ss-initial-admin-user.feature :: "Weak password is rejected"
  //
  // See module-level comment: validation is SERVER-SIDE. "secret" (6 chars) passes
  // the client-side min:6 rule, the form submits, and the mocked 400 surfaces an error banner.
  // API coverage gap: password complexity policy belongs at the API tier.
  it('Weak password rejected — server 400 triggers error banner', async () => {
    suppressAxios4xxRejection();

    await renderRoute('/initial-admin-user', {
      msw: [adminUserCreationRequiredHandler, adminUserWeakPasswordHandler],
    });

    await expect.element(page.getByTestId('admin-username-input')).toBeVisible();

    await page.getByTestId('admin-username-input').getByRole('textbox').fill('xrd');
    await page.getByTestId('admin-password-input').getByRole('textbox').fill('secret');
    await page.getByTestId('admin-confirm-password-input').getByRole('textbox').fill('secret');

    const saveBtn = page.getByTestId('admin-user-save-button');
    await expect.element(saveBtn).not.toBeDisabled();
    await saveBtn.click();

    await expect.element(page.getByTestId('contextual-alert')).toBeVisible();
  });

  // MIGRATED-FROM: 0090-ss-initial-admin-user.feature :: "Password confirmation mismatch blocks submission"
  it('Confirmation mismatch blocks submit — button disabled when passwords differ', async () => {
    await renderRoute('/initial-admin-user', {
      msw: [adminUserCreationRequiredHandler],
    });

    await expect.element(page.getByTestId('admin-username-input')).toBeVisible();

    await page.getByTestId('admin-username-input').getByRole('textbox').fill('xrd');
    await page.getByTestId('admin-password-input').getByRole('textbox').fill('secret123!');
    await page.getByTestId('admin-confirm-password-input').getByRole('textbox').fill('different456!');

    await page.getByTestId('admin-username-input').click();

    await expect.element(page.getByTestId('admin-user-save-button')).toBeDisabled();
  });

  // MIGRATED-FROM: 0090-ss-initial-admin-user.feature :: "Strong password succeeds and redirects to login"
  it('Strong password succeeds — POST 201 causes redirect to login route', async () => {
    const { router } = await renderRoute('/initial-admin-user', {
      msw: [adminUserCreationRequiredHandler, adminUserCreatedHandler],
    });

    await expect.element(page.getByTestId('admin-username-input')).toBeVisible();

    await page.getByTestId('admin-username-input').getByRole('textbox').fill('xrd');
    await page.getByTestId('admin-password-input').getByRole('textbox').fill('secret123!');
    await page.getByTestId('admin-confirm-password-input').getByRole('textbox').fill('secret123!');
    await page.getByTestId('admin-user-save-button').click();

    await expect.element(page.getByTestId('login-username-input')).toBeVisible();
    expect(router.currentRoute.value.name).toBe(RouteName.Login);
  });
});
