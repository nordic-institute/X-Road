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
import { renderRoute } from '../setup/render-route';
import { specHttp, validateBody } from '../setup/spec-http';
import { clientWithSubsystemNameSchema } from '../setup/schemas';
import { Permissions } from '@/global';
import type { Client } from '@/openapi-types';
import { ClientStatus, RenameStatus } from '@/openapi-types';
import { useSystem } from '@/store/modules/system';

// ── Fixtures ──────────────────────────────────────────────────────────────────

const CLIENT_ID = 'DEV:COM:1234:named-random-sub-3';
const CLIENT_ID_REGISTERED = 'DEV:COM:1234:test-service';

const savedClientFixture: Client = {
  id: CLIENT_ID,
  instance_id: 'DEV',
  member_class: 'COM',
  member_code: '1234',
  subsystem_code: 'named-random-sub-3',
  subsystem_name: 'OriginalName',
  status: ClientStatus.SAVED,
  rename_status: RenameStatus.NAME_SET,
};

const registeredClientFixture: Client = {
  id: CLIENT_ID_REGISTERED,
  instance_id: 'DEV',
  member_class: 'COM',
  member_code: '1234',
  subsystem_code: 'test-service',
  subsystem_name: 'Test service',
  status: ClientStatus.REGISTERED,
  rename_status: RenameStatus.NAME_SET,
};

validateBody(clientWithSubsystemNameSchema, savedClientFixture);
validateBody(clientWithSubsystemNameSchema, registeredClientFixture);

// ── Permissions ───────────────────────────────────────────────────────────────

const clientDetailPermissions = [
  Permissions.VIEW_CLIENTS,
  Permissions.VIEW_CLIENT_DETAILS,
  Permissions.RENAME_SUBSYSTEM,
  Permissions.SEND_CLIENT_DEL_REQ,
];

// ── Helper ────────────────────────────────────────────────────────────────────

async function enableSubsystemNameSupport(): Promise<void> {
  // doesSupportSubsystemNames returns true when global_configuration_version >= 5.
  useSystem().$patch({ securityServerVersion: { global_configuration_version: 5 } as never });
}

// ── Specs ─────────────────────────────────────────────────────────────────────

describe('Client Details — subsystem rename save-button state (Browser Mode)', () => {
  it('rename button disabled on empty name, enabled on valid name, disabled again after save, works a second time', async () => {
    const encodedId = encodeURIComponent(CLIENT_ID);
    let callCount = 0;
    const clientHandler = specHttp.get('/clients/{id}', ({ response }) => response(200).json(savedClientFixture));
    const signCertsHandler = specHttp.get('/clients/{id}/sign-certificates', ({ response }) => response(200).json([]));
    const renameHandler = specHttp.put('/clients/{id}/rename', ({ response }) => {
      callCount++;
      return response(204).empty();
    });

    await renderRoute(`/clients/subsystem/${encodedId}/details`, {
      permissions: clientDetailPermissions,
      msw: [clientHandler, signCertsHandler, renameHandler],
    });

    enableSubsystemNameSupport();

    await expect.element(page.getByTestId('rename-client-button')).toBeVisible();

    // ── First rename cycle ────────────────────────────────────────────────────

    await page.getByTestId('rename-client-button').click();
    await expect.element(page.getByTestId('subsystem-name-input')).toBeVisible();

    const saveBtn = page.getByTestId('dialog-save-button');
    await expect.element(saveBtn).toBeDisabled();

    await page.getByTestId('subsystem-name-input').getByRole('textbox').fill('');
    await expect.element(saveBtn).toBeDisabled();

    await page.getByTestId('subsystem-name-input').getByRole('textbox').fill('Updated1');
    await expect.element(saveBtn).not.toBeDisabled();

    await saveBtn.click();
    await expect.poll(() => page.getByTestId('subsystem-name-input').query()).toBeNull();

    // ── Second rename cycle ───────────────────────────────────────────────────

    await page.getByTestId('rename-client-button').click();
    await expect.element(page.getByTestId('subsystem-name-input')).toBeVisible();
    await expect.element(saveBtn).toBeDisabled();

    await page.getByTestId('subsystem-name-input').getByRole('textbox').fill('Updated2');
    await expect.element(saveBtn).not.toBeDisabled();

    await saveBtn.click();
    await expect.poll(() => page.getByTestId('subsystem-name-input').query()).toBeNull();

    expect(callCount).toBe(2);
  });
});

describe('Client Details — subsystem rename button state and error display (Browser Mode)', () => {
  it('save button disabled with empty name, enabled with name, mocked server error shows error notification', async () => {
    const encodedId = encodeURIComponent(CLIENT_ID_REGISTERED);
    const clientHandler = specHttp.get('/clients/{id}', ({ response }) => response(200).json(registeredClientFixture));
    const signCertsHandler = specHttp.get('/clients/{id}/sign-certificates', ({ response }) => response(200).json([]));
    const renameErrorHandler = specHttp.put('/clients/{id}/rename', ({ response }) => response(400).json({ status: 400, error: { code: 'management_request_sending_failed' } }));

    await renderRoute(`/clients/subsystem/${encodedId}/details`, {
      permissions: clientDetailPermissions,
      msw: [clientHandler, signCertsHandler, renameErrorHandler],
    });

    await enableSubsystemNameSupport();

    await expect.element(page.getByTestId('rename-client-button')).toBeVisible();

    await page.getByTestId('rename-client-button').click();
    await expect.element(page.getByTestId('subsystem-name-input')).toBeVisible();

    const saveBtn = page.getByTestId('dialog-save-button');

    await expect.element(saveBtn).toBeDisabled();

    await page.getByTestId('subsystem-name-input').getByRole('textbox').fill('');
    await expect.element(saveBtn).toBeDisabled();

    await page.getByTestId('subsystem-name-input').getByRole('textbox').fill('Updated1');
    await expect.element(saveBtn).not.toBeDisabled();

    await saveBtn.click();

    await expect.poll(() => page.getByTestId('subsystem-name-input').query()).toBeNull();

    await expect.element(page.getByTestId('contextual-alert')).toBeVisible();
  });
});
