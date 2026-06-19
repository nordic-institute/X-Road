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

import { describe, it, expect } from 'vitest';
import { page } from 'vitest/browser';
import { HttpResponse } from 'msw';
import { renderRoute } from '../setup/render-route';
import { specHttp, validateBody } from '../setup/spec-http';
import { Permissions } from '@/global';
import { useUser } from '@/store/modules/user';
import type { AdminUser } from '@niis/shared-ui/src/openapi-types/types.gen';
import type { RequestHandler } from 'msw';

// ── AJV schema for /users list ───────────────────────────────────────────────

const adminUserSchema = {
  type: 'object',
  required: ['username', 'roles'],
  properties: {
    username: { type: 'string' },
    password: { type: 'string' },
    roles: { type: 'array', items: { type: 'string' } },
  },
};

// ── Fixtures ─────────────────────────────────────────────────────────────────

const testUserFixture: AdminUser = {
  username: 'test',
  roles: ['XROAD_REGISTRATION_OFFICER', 'XROAD_SYSTEM_ADMINISTRATOR'],
};

validateBody(adminUserSchema, testUserFixture);
validateBody({ type: 'array', items: adminUserSchema }, [testUserFixture]);

// ── Untyped handlers for /users (not in OpenAPI spec) ───────────────────────

const getUsersHandler = specHttp.untyped.get('/api/v1/users', () => HttpResponse.json([testUserFixture]));

const postUserWeakPwHandler = specHttp.untyped.post('/api/v1/users', () =>
  HttpResponse.json(
    { error: { code: 'user_weak_password' } },
    { status: 400 },
  ),
);

const postUserInvalidCharsHandler = specHttp.untyped.post('/api/v1/users', () =>
  HttpResponse.json(
    { error: { code: 'user_password_invalid_characters' } },
    { status: 400 },
  ),
);

const putPasswordWeakHandler = (username: string) =>
  specHttp.untyped.put(`/api/v1/users/${encodeURIComponent(username)}/password`, () =>
    HttpResponse.json(
      { error: { code: 'user_weak_password' } },
      { status: 400 },
    ),
  );

const putPasswordInvalidCharsHandler = (username: string) =>
  specHttp.untyped.put(`/api/v1/users/${encodeURIComponent(username)}/password`, () =>
    HttpResponse.json(
      { error: { code: 'user_password_invalid_characters' } },
      { status: 400 },
    ),
  );

// ── Permissions for a user with only REGISTRATION_OFFICER + SYSTEM_ADMINISTRATOR ──
// VIEW_SYS_PARAMS ensures the /settings redirect resolves to a valid tab (system-parameters).
// Without it, useSettingsTabs().firstAllowedTab is undefined when navigating to /settings/users.

const limitedRolePermissions = [
  Permissions.VIEW_ADMIN_USERS,
  Permissions.ADD_ADMIN_USER,
  Permissions.UPDATE_ADMIN_USER,
  Permissions.VIEW_SYS_PARAMS,
];

const adminUsersEditPermissions = [
  Permissions.VIEW_ADMIN_USERS,
  Permissions.UPDATE_ADMIN_USER,
  Permissions.VIEW_SYS_PARAMS,
];

// ── Error-path rejection suppression ─────────────────────────────────────────
//
// XrdAdminUserPasswordChangeDialog and XrdAddAdminUser call adminUsersHandler methods
// that end with .catch((err) => addError(err)). addError() (shared-ui) shows the
// notification but also returns Promise.reject(err), so the rejection propagates through
// the component's .finally() and surfaces as an unhandled rejection. The tests ARE
// green (assertions pass), but vitest exits with code 1 when unhandled rejections occur.
// Suppress only AxiosError 4xx rejections that are expected in these error-path specs.

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

// ── Shared add-user wizard helpers ───────────────────────────────────────────

async function openAddUserWizardStep2(): Promise<void> {
  await expect.element(page.getByTestId('add-admin-user-stepper-view')).toBeVisible();
  await page.getByTestId('role-XROAD_REGISTRATION_OFFICER-checkbox').getByRole('checkbox').click();
  await page.getByTestId('next-button').click();
  await expect.element(page.getByTestId('add-admin-user-step-2')).toBeVisible();
}

async function fillStep2Credentials(password: string): Promise<void> {
  await page.getByTestId('username-input').getByRole('textbox').fill('testuser');
  await page.getByTestId('password-input').getByRole('textbox').fill(password);
  await page.getByTestId('confirm-password-input').getByRole('textbox').fill(password);
  await page.getByTestId('username-input').getByRole('textbox').click();
}

// ── Scenarios 1–2 ────────────────────────────────────────────────────────────

describe('Admin Users — add user: server-side password rejection (Browser Mode)', () => {
  it.each<[string, RequestHandler, string, string]>([
    ['too weak', postUserWeakPwHandler, 't0pSecret', 'The provided password was too weak'],
    ['invalid characters', postUserInvalidCharsHandler, 't0pSecretä', 'The provided password contains invalid characters'],
  ])('%s password rejected on add', async (_label, handler, password, expectedMessage) => {
    await renderRoute('/settings/users/add', {
      permissions: [Permissions.ADD_ADMIN_USER, Permissions.VIEW_ADMIN_USERS, Permissions.VIEW_SYS_PARAMS],
      msw: [handler],
    });

    useUser().$patch({ roles: ['XROAD_REGISTRATION_OFFICER', 'XROAD_SYSTEM_ADMINISTRATOR'] });

    await openAddUserWizardStep2();
    await fillStep2Credentials(password);

    const addBtn = page.getByTestId('add-button');
    await expect.element(addBtn).not.toBeDisabled();
    suppressAxios4xxRejection();
    await addBtn.click();

    await expect.element(page.getByTestId('contextual-alert')).toBeVisible();
    await expect.element(page.getByText(expectedMessage)).toBeVisible();
  });
});

// ── Scenario 3 ───────────────────────────────────────────────────────────────

describe('Admin Users — role gating: user sees only own roles (Browser Mode)', () => {
  it('hides roles the current user lacks in the add wizard and edit dialog', async () => {
    await renderRoute('/settings/users', {
      permissions: limitedRolePermissions,
      msw: [getUsersHandler],
    });

    useUser().$patch({ roles: ['XROAD_REGISTRATION_OFFICER', 'XROAD_SYSTEM_ADMINISTRATOR'] });

    await expect.element(page.getByTestId('add-admin-user-button')).toBeVisible();
    await page.getByTestId('add-admin-user-button').click();

    await expect.element(page.getByTestId('add-admin-user-step-1')).toBeVisible();

    await expect.element(page.getByTestId('role-XROAD_REGISTRATION_OFFICER-checkbox')).toBeVisible();
    await expect.element(page.getByTestId('role-XROAD_SYSTEM_ADMINISTRATOR-checkbox')).toBeVisible();

    expect(page.getByTestId('role-XROAD_SECURITY_OFFICER-checkbox').query()).toBeNull();
    expect(page.getByTestId('role-XROAD_SERVICE_ADMINISTRATOR-checkbox').query()).toBeNull();
    expect(page.getByTestId('role-XROAD_SECURITYSERVER_OBSERVER-checkbox').query()).toBeNull();

    await page.getByTestId('cancel-button').click();

    await expect.element(page.getByTestId('admin-user-row-test-edit-button')).toBeVisible();
    await page.getByTestId('admin-user-row-test-edit-button').click();
    await expect.element(page.getByTestId('dialog-simple')).toBeVisible();

    await expect.element(page.getByTestId('role-XROAD_REGISTRATION_OFFICER-checkbox')).toBeVisible();
    await expect.element(page.getByTestId('role-XROAD_SYSTEM_ADMINISTRATOR-checkbox')).toBeVisible();

    expect(page.getByTestId('role-XROAD_SECURITY_OFFICER-checkbox').query()).toBeNull();
    expect(page.getByTestId('role-XROAD_SERVICE_ADMINISTRATOR-checkbox').query()).toBeNull();
    expect(page.getByTestId('role-XROAD_SECURITYSERVER_OBSERVER-checkbox').query()).toBeNull();

    await page.getByTestId('dialog-cancel-button').click();
  });
});

// ── Scenarios 4–5 ────────────────────────────────────────────────────────────

describe("Admin Users — other user's password: server-side rejection (Browser Mode)", () => {
  it.each<[string, RequestHandler, string, string]>([
    ['too weak', putPasswordWeakHandler('test'), 't0pSecret', 'The provided password was too weak'],
    ['invalid characters', putPasswordInvalidCharsHandler('test'), 't0pSecretä', 'The provided password contains invalid characters'],
  ])('%s password rejected on other-user change', async (_label, handler, password, expectedMessage) => {
    await renderRoute('/settings/users', {
      permissions: adminUsersEditPermissions,
      msw: [getUsersHandler, handler],
    });

    await expect.element(page.getByTestId('admin-user-row-test-change-password-button')).toBeVisible();
    await page.getByTestId('admin-user-row-test-change-password-button').click();
    await expect.element(page.getByTestId('dialog-simple')).toBeVisible();

    expect(page.getByTestId('old-password-input').query()).toBeNull();

    await page.getByTestId('new-password-input').getByRole('textbox').fill(password);
    await page.getByTestId('new-password-confirm-input').getByRole('textbox').fill(password);
    await page.getByTestId('new-password-input').getByRole('textbox').click();

    const saveBtn = page.getByTestId('dialog-save-button');
    await expect.element(saveBtn).not.toBeDisabled();
    suppressAxios4xxRejection();
    await saveBtn.click();

    await expect.element(page.getByTestId('contextual-alert')).toBeVisible();
    await expect.element(page.getByText(expectedMessage)).toBeVisible();
  });
});

// ── Scenarios 6–7 ────────────────────────────────────────────────────────────

describe('Admin Users — own password: server-side rejection (Browser Mode)', () => {
  it.each<[string, RequestHandler, string, string]>([
    ['too weak', putPasswordWeakHandler('xrd'), 't0pSecret', 'The provided password was too weak'],
    ['invalid characters', putPasswordInvalidCharsHandler('xrd'), 't0pSecretä', 'The provided password contains invalid characters'],
  ])('%s password rejected on own-account change', async (_label, handler, password, expectedMessage) => {
    const xrdUser: AdminUser = { username: 'xrd', roles: ['XROAD_SYSTEM_ADMINISTRATOR'] };
    validateBody(adminUserSchema, xrdUser);

    const xrdUsersHandler = specHttp.untyped.get('/api/v1/users', () => HttpResponse.json([xrdUser]));

    await renderRoute('/settings/users', {
      permissions: adminUsersEditPermissions,
      msw: [xrdUsersHandler, handler],
    });

    useUser().$patch({ username: 'xrd' });

    await expect.element(page.getByTestId('admin-user-row-xrd-change-password-button')).toBeVisible();
    await page.getByTestId('admin-user-row-xrd-change-password-button').click();
    await expect.element(page.getByTestId('dialog-simple')).toBeVisible();

    await expect.element(page.getByTestId('old-password-input')).toBeVisible();
    await page.getByTestId('old-password-input').getByRole('textbox').fill('secret123!');
    await page.getByTestId('new-password-input').getByRole('textbox').fill(password);
    await page.getByTestId('new-password-confirm-input').getByRole('textbox').fill(password);
    await page.getByTestId('old-password-input').getByRole('textbox').click();

    const saveBtn = page.getByTestId('dialog-save-button');
    await expect.element(saveBtn).not.toBeDisabled();
    suppressAxios4xxRejection();
    await saveBtn.click();

    await expect.element(page.getByTestId('contextual-alert')).toBeVisible();
    await expect.element(page.getByText(expectedMessage)).toBeVisible();
  });
});
