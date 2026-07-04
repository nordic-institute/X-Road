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
import type { RequestHandler } from 'msw';
import { renderRoute } from '../setup/render-route';
import { specHttp } from '../setup/spec-http';
import { submitDialogForm } from '../setup/dialog-helpers';
import { Permissions } from '@/global';
import type { SecurityServer } from '@/openapi-types';

const SERVER_ID = 'CS:E2E-TC1:e2e-tc1-member-subsystem:E2E-SS1';
const SERVER_CODE = 'E2E-SS1';
const OWNER_NAME = 'E2E TC1 Member with Subsystems';
const OWNER_CLASS = 'E2E-TC1';
const OWNER_CODE = 'e2e-tc1-member-subsystem';
const ADDRESS = 'security-server-address-E2E-SS1';

const serverFixture: SecurityServer = {
  server_id: {
    instance_id: 'CS',
    member_class: OWNER_CLASS,
    member_code: OWNER_CODE,
    server_code: SERVER_CODE,
    type: 'SecurityServerId',
    encoded_id: SERVER_ID,
  },
  owner_name: OWNER_NAME,
  server_address: ADDRESS,
  created_at: '2024-01-15T10:00:00Z',
  in_maintenance_mode: false,
};

const detailsPermissions = [
  Permissions.VIEW_SECURITY_SERVERS,
  Permissions.VIEW_SECURITY_SERVER_DETAILS,
];

const editPermissions = [
  ...detailsPermissions,
  Permissions.EDIT_SECURITY_SERVER_ADDRESS,
];

const deletePermissions = [
  ...detailsPermissions,
  Permissions.DELETE_SECURITY_SERVER,
];

function baseHandlers(fixture: SecurityServer = serverFixture): RequestHandler[] {
  return [
    specHttp.get('/security-servers/{server_id}', ({ response }) => response(200).json(fixture as never)),
  ];
}

describe('1000 — CS Security Server Details — detail fields render (Browser Mode)', () => {
  it('renders owner name, class, code, server code, and address from API response', async () => {
    await renderRoute(`/security-servers/${encodeURIComponent(SERVER_ID)}/details`, {
      permissions: detailsPermissions,
      msw: baseHandlers(),
    });

    await expect.element(page.getByTestId('security-server-details-view')).toBeVisible();
    await expect.element(page.getByTestId('security-server-owner-name')).toHaveTextContent(OWNER_NAME);
    await expect.element(page.getByTestId('security-server-owner-class')).toHaveTextContent(OWNER_CLASS);
    await expect.element(page.getByTestId('security-server-owner-code')).toHaveTextContent(OWNER_CODE);
    await expect.element(page.getByTestId('security-server-server-code')).toHaveTextContent(SERVER_CODE);
    await expect.element(page.getByTestId('security-server-address')).toHaveTextContent(ADDRESS);
    await expect.element(page.getByTestId('security-server-registered')).toBeVisible();
  });
});

describe('1000 — CS Security Server Details — edit address dialog cancel and save (Browser Mode)', () => {
  it('cancel keeps the old address; save updates the rendered address', async () => {
    const updatedAddress = 'security-server-address-E2E-SS1-updated';

    let serverCallCount = 0;
    await renderRoute(`/security-servers/${encodeURIComponent(SERVER_ID)}/details`, {
      permissions: editPermissions,
      msw: [
        specHttp.get('/security-servers/{server_id}', ({ response }) => {
          serverCallCount += 1;
          return serverCallCount === 1
            ? response(200).json(serverFixture as never)
            : response(200).json({ ...serverFixture, server_address: updatedAddress } as never);
        }),
        specHttp.patch('/security-servers/{server_id}', ({ response }) =>
          response(200).json({ ...serverFixture, server_address: updatedAddress } as never),
        ),
      ],
    });

    await expect.element(page.getByTestId('security-server-address')).toHaveTextContent(ADDRESS);

    await page.getByTestId('security-server-address').getByRole('button').click();

    const editDialog = page.getByTestId('security-server-address-edit-dialog');
    await expect.element(editDialog).toBeVisible();

    const addressField = editDialog.getByTestId('security-server-address-edit-field').getByRole('textbox');
    await addressField.fill(updatedAddress);

    await page.getByTestId('dialog-cancel-button').click();

    await expect.element(page.getByTestId('security-server-address-edit-dialog')).not.toBeInTheDocument();
    await expect.element(page.getByTestId('security-server-address')).toHaveTextContent(ADDRESS);

    await page.getByTestId('security-server-address').getByRole('button').click();

    const editDialog2 = page.getByTestId('security-server-address-edit-dialog');
    await expect.element(editDialog2).toBeVisible();

    const addressField2 = editDialog2.getByTestId('security-server-address-edit-field').getByRole('textbox');
    await addressField2.fill(updatedAddress);

    await expect.element(page.getByTestId('dialog-save-button')).not.toBeDisabled();
    submitDialogForm();

    await expect.element(page.getByTestId('security-server-address-edit-dialog')).not.toBeInTheDocument();
    await expect.element(page.getByTestId('security-server-address')).toHaveTextContent(updatedAddress);
  });
});

describe('1000 — CS Security Server Details — delete dialog confirm-code gating (Browser Mode)', () => {
  it('save disabled with no/wrong code, enabled with exact server code, server removed after submit', async () => {
    await renderRoute(`/security-servers/${encodeURIComponent(SERVER_ID)}/details`, {
      permissions: deletePermissions,
      msw: [
        ...baseHandlers(),
        specHttp.delete('/security-servers/{server_id}', ({ response }) => response(204).empty()),
        specHttp.get('/security-servers', ({ response }) =>
          response(200).json({ security_servers: [], paging_metadata: { total_items: 0, items: 0, limit: 25, offset: 0 } } as never),
        ),
      ],
    });

    await expect.element(page.getByTestId('security-server-view')).toBeVisible();
    await expect.element(page.getByTestId('btn-delete-security-server')).toBeVisible();

    await page.getByTestId('btn-delete-security-server').click();

    const deleteDialog = page.getByTestId('security-server-delete-dialog');
    await expect.element(deleteDialog).toBeVisible();

    const codeInput = deleteDialog.getByTestId('verify-server-code').getByRole('textbox');
    await expect.element(page.getByTestId('dialog-save-button')).toBeDisabled();

    await codeInput.fill('invalid-code');
    await expect.element(page.getByTestId('dialog-save-button')).toBeDisabled();

    await codeInput.fill(SERVER_CODE);
    await expect.element(page.getByTestId('dialog-save-button')).not.toBeDisabled();

    submitDialogForm();

    await expect.element(page.getByTestId('security-server-view')).not.toBeInTheDocument();
  });
});
