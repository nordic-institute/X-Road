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
import { specHttp } from '../setup/spec-http';
import { submitDialogForm } from '../setup/dialog-helpers';
import { Permissions, Roles } from '@/global';
import { useUser } from '@/store/modules/user';

const CREATE_API_KEY_PATH = '/keys/apikey/create';
const API_KEYS_LIST_PATH = '/settings/api-keys';

const apiKeyPermissions = [
  Permissions.VIEW_API_KEYS,
  Permissions.CREATE_API_KEY,
  Permissions.UPDATE_API_KEY,
  Permissions.REVOKE_API_KEY,
];

describe('0360 — CS API Keys — creation wizard Next button and key created (Browser Mode)', () => {
  it('Next button is disabled before any role selected, enabled after selecting a role, and the created key is shown', async () => {
    await renderRoute(CREATE_API_KEY_PATH, {
      permissions: apiKeyPermissions,
      msw: [
        specHttp.untyped.post('/api/v1/api-keys', () =>
          HttpResponse.json({ id: 1, key: 'test-api-key-value', roles: ['XROAD_SECURITY_OFFICER'] }),
        ),
        specHttp.untyped.get('/api/v1/api-keys', () => HttpResponse.json([])),
      ],
    });

    useUser().$patch({ roles: [...Roles] });

    await expect.element(page.getByTestId('create-api-key-stepper-view')).toBeVisible();

    const nextBtn = page.getByTestId('next-button');
    await expect.element(nextBtn).toBeVisible();
    await expect.element(nextBtn).toBeDisabled();

    await page.getByTestId('role-XROAD_SECURITY_OFFICER-checkbox').getByRole('checkbox').click();

    await expect.element(nextBtn).not.toBeDisabled();

    await nextBtn.click();

    await expect.element(page.getByTestId('created-apikey')).toBeVisible();

    await page.getByTestId('finish-button').click();

    await expect.element(page.getByTestId('api-keys-view')).toBeVisible();
  });
});

describe('0360 — CS API Keys — key is revoked from list (Browser Mode)', () => {
  it('key appears in list and disappears after revoke confirmation', async () => {
    const createdKey = { id: 1, key: 'test-api-key-value', roles: ['XROAD_SECURITY_OFFICER'] };

    let keysCallCount = 0;
    await renderRoute(API_KEYS_LIST_PATH, {
      permissions: apiKeyPermissions,
      msw: [
        specHttp.untyped.get('/api/v1/api-keys', () => {
          keysCallCount += 1;
          return keysCallCount === 1 ? HttpResponse.json([createdKey]) : HttpResponse.json([]);
        }),
        specHttp.untyped.delete('/api/v1/api-keys/1', () => new HttpResponse(null, { status: 200 })),
      ],
    });

    useUser().$patch({ roles: [...Roles] });

    await expect.element(page.getByTestId('api-key-id').first()).toBeVisible();

    await page.getByTestId('api-key-row-1-revoke-button').click();

    await expect.element(page.getByTestId('api-key-row-1-revoke-confirmation')).toBeVisible();

    await page.getByTestId('dialog-save-button').click();

    await expect.element(page.getByTestId('api-key-id')).not.toBeInTheDocument();
  });
});

describe('0360 — CS API Keys — key roles are edited via edit dialog (Browser Mode)', () => {
  it('opening edit dialog, checking an additional role, and saving updates the roles shown in the list', async () => {
    const originalKey = { id: 1, key: 'test-api-key-value', roles: ['XROAD_REGISTRATION_OFFICER'] };
    const updatedKey = { id: 1, key: 'test-api-key-value', roles: ['XROAD_REGISTRATION_OFFICER', 'XROAD_SYSTEM_ADMINISTRATOR'] };

    let keysCallCount = 0;
    await renderRoute(API_KEYS_LIST_PATH, {
      permissions: apiKeyPermissions,
      msw: [
        specHttp.untyped.get('/api/v1/api-keys', () => {
          keysCallCount += 1;
          return keysCallCount === 1 ? HttpResponse.json([originalKey]) : HttpResponse.json([updatedKey]);
        }),
        specHttp.untyped.put('/api/v1/api-keys/1', () => HttpResponse.json(updatedKey)),
      ],
    });

    useUser().$patch({ roles: [...Roles] });

    await expect.element(page.getByTestId('api-key-row-1-roles')).toBeVisible();

    await page.getByTestId('api-key-row-1-edit-button').click();

    await expect.element(page.getByTestId('api-key-row-1-edit-dialog-content')).toBeVisible();

    await page.getByTestId('role-XROAD_SYSTEM_ADMINISTRATOR-checkbox').getByRole('checkbox').click();

    submitDialogForm();

    await expect.element(page.getByTestId('api-key-row-1-roles')).toHaveTextContent('System Administrator');
  });
});

describe('0360 — CS API Keys — role gating shows only held roles (Browser Mode)', () => {
  it('user with only SYSTEM_ADMINISTRATOR role sees only that checkbox; other role checkboxes are absent', async () => {
    await renderRoute(CREATE_API_KEY_PATH, {
      permissions: apiKeyPermissions,
    });

    useUser().$patch({ roles: ['XROAD_SYSTEM_ADMINISTRATOR'] });

    await expect.element(page.getByTestId('create-api-key-stepper-view')).toBeVisible();

    await expect.element(page.getByTestId('role-XROAD_SYSTEM_ADMINISTRATOR-checkbox')).toBeVisible();

    expect(page.getByTestId('role-XROAD_SECURITY_OFFICER-checkbox').query()).toBeNull();
    expect(page.getByTestId('role-XROAD_REGISTRATION_OFFICER-checkbox').query()).toBeNull();
  });
});
