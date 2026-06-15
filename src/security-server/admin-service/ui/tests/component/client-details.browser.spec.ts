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
import { Permissions } from '@/global';
import type { Client } from '@/openapi-types';
import { ClientStatus, RenameStatus } from '@/openapi-types';
import { useSystem } from '@/store/modules/system';

// ── AJV schemas ───────────────────────────────────────────────────────────────

const clientSchema = {
  type: 'object',
  required: ['member_class', 'member_code'],
  properties: {
    id: { type: 'string' },
    instance_id: { type: 'string' },
    member_class: { type: 'string' },
    member_code: { type: 'string' },
    subsystem_code: { type: 'string' },
    subsystem_name: { type: 'string' },
    status: { type: 'string' },
    rename_status: { type: 'string' },
  },
};

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

validateBody(clientSchema, savedClientFixture);
validateBody(clientSchema, registeredClientFixture);

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

// MIGRATED-FROM: 0520-ss-client-details.feature :: "Subsystem rename allowed multiple times on saved client"
// Split slice — API/persistence assertions DONE (ClientDetailsTest#subsystemRenamedMultipleTimesPersists).
// This spec covers the UI slice: save-button state behaviour in the rename dialog.
// CLIENT-SIDE gating: the save button enable/disable is driven by vee-validate form state
// (required + dirty), so it is entirely client-side. No server mock needed for the
// disable/enable transitions; a PUT mock is needed only to close the dialog on save.
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

    // Enable subsystem name support BEFORE render so SubsystemView.showRename
    // returns true on the initial mount (doesSupportSubsystemNames is reactive but
    // pre-seeding avoids the race on the first render pass).
    await renderRoute(`/clients/subsystem/${encodedId}/details`, {
      permissions: clientDetailPermissions,
      msw: [clientHandler, signCertsHandler, renameHandler],
    });

    enableSubsystemNameSupport();

    // Wait for the client to load — the Edit button is rendered by SubsystemView.
    await expect.element(page.getByTestId('rename-client-button')).toBeVisible();

    // ── First rename cycle ────────────────────────────────────────────────────

    // Open the rename dialog.
    await page.getByTestId('rename-client-button').click();
    await expect.element(page.getByTestId('subsystem-name-input')).toBeVisible();

    // Save button must start disabled (form not dirty yet).
    const saveBtn = page.getByTestId('dialog-save-button');
    await expect.element(saveBtn).toBeDisabled();

    // Clear the field — still disabled (required + empty).
    await page.getByTestId('subsystem-name-input').getByRole('textbox').fill('');
    await expect.element(saveBtn).toBeDisabled();

    // Enter a valid name — save button becomes enabled.
    await page.getByTestId('subsystem-name-input').getByRole('textbox').fill('Updated1');
    await expect.element(saveBtn).not.toBeDisabled();

    // Save — dialog closes.
    await saveBtn.click();
    await expect.poll(() => page.getByTestId('subsystem-name-input').query()).toBeNull();

    // ── Second rename cycle ───────────────────────────────────────────────────

    // Open again — save button must start disabled (form is reset).
    await page.getByTestId('rename-client-button').click();
    await expect.element(page.getByTestId('subsystem-name-input')).toBeVisible();
    await expect.element(saveBtn).toBeDisabled();

    // Enter a different valid name.
    await page.getByTestId('subsystem-name-input').getByRole('textbox').fill('Updated2');
    await expect.element(saveBtn).not.toBeDisabled();

    // Save again.
    await saveBtn.click();
    await expect.poll(() => page.getByTestId('subsystem-name-input').query()).toBeNull();

    // Both rename calls went through.
    expect(callCount).toBe(2);
  });
});

// MIGRATED-FROM: 0520-ss-client-details.feature :: "Subsystem rename request is sent imidiately"
// Split slice — API/mgmt-request assertions DONE (ClientDetailsTest#renameRequestSetsRenameStatus).
// This spec covers the UI slice: button-state behaviour — save disabled until name typed,
// and dialog reports an error on server 400.
// SERVER-SIDE failure display: mock the PUT to 400; assert error notification shows and dialog
// closes (the component calls addError + closes the dialog on any error).
describe('Client Details — subsystem rename button state and error display (Browser Mode)', () => {
  it('save button disabled with empty name, enabled with name, mocked server error shows error notification', async () => {
    const encodedId = encodeURIComponent(CLIENT_ID_REGISTERED);
    const clientHandler = specHttp.get('/clients/{id}', ({ response }) => response(200).json(registeredClientFixture));
    const signCertsHandler = specHttp.get('/clients/{id}/sign-certificates', ({ response }) => response(200).json([]));
    // Mock the rename to return 400 to simulate "Sending of management request failed".
    const renameErrorHandler = specHttp.put('/clients/{id}/rename', ({ response }) => response(400).json({ status: 400, error: { code: 'management_request_sending_failed' } }));

    await renderRoute(`/clients/subsystem/${encodedId}/details`, {
      permissions: clientDetailPermissions,
      msw: [clientHandler, signCertsHandler, renameErrorHandler],
    });

    await enableSubsystemNameSupport();

    await expect.element(page.getByTestId('rename-client-button')).toBeVisible();

    // Open dialog.
    await page.getByTestId('rename-client-button').click();
    await expect.element(page.getByTestId('subsystem-name-input')).toBeVisible();

    const saveBtn = page.getByTestId('dialog-save-button');

    // Initially disabled (form not dirty).
    await expect.element(saveBtn).toBeDisabled();

    // Clear field — still disabled.
    await page.getByTestId('subsystem-name-input').getByRole('textbox').fill('');
    await expect.element(saveBtn).toBeDisabled();

    // Enter a valid name — button becomes enabled.
    await page.getByTestId('subsystem-name-input').getByRole('textbox').fill('Updated1');
    await expect.element(saveBtn).not.toBeDisabled();

    // Save — server returns error; dialog should close and an error notification should appear.
    await saveBtn.click();

    // Dialog closes even on error (component always sets showDialog = false in finally).
    await expect.poll(() => page.getByTestId('subsystem-name-input').query()).toBeNull();

    // Error notification banner should be visible (addError emits XrdErrorNotifications banner).
    await expect.element(page.getByTestId('contextual-alert')).toBeVisible();
  });
});
