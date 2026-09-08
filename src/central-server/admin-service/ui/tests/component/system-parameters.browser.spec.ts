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
import { specHttp } from '../setup/spec-http';
import { submitDialogForm } from '../setup/dialog-helpers';
import { Permissions } from '@/global';
import type { MemberClass } from '@/openapi-types';

const systemSettingsPermissions = [
  Permissions.VIEW_SYSTEM_SETTINGS,
  Permissions.VIEW_MEMBER_CLASSES,
  Permissions.EDIT_MEMBER_CLASS,
];

// Baseline handlers needed for the SystemSettings page to render without errors.
const emptyMemberClassesHandler = specHttp.get('/member-classes', ({ response }) =>
  response(200).json([] as MemberClass[]),
);

const managementServicesHandler = specHttp.get('/management-services-configuration', ({ response }) =>
  response(200).json({
    service_provider_id: null,
    service_provider_name: null,
    security_server_id: null,
    wsdl_address: null,
    services_address: null,
    security_server_owners_global_group_code: 'OWNERS',
  } as never),
);

describe('0350 — CS System Parameters — invalid address inline error (Browser Mode)', () => {
  it('entering an invalid address in the edit dialog shows inline validation error', async () => {
    await renderRoute('/settings/system-settings', {
      permissions: systemSettingsPermissions,
      msw: [emptyMemberClassesHandler, managementServicesHandler],
    });

    // Two elements share the data-test id (ManagementServices reuses the same value);
    // navigate to the edit button directly without asserting the ambiguous card.
    await expect.element(page.getByTestId('system-settings-central-server-address-edit-button')).toBeVisible();

    await page.getByTestId('system-settings-central-server-address-edit-button').click();

    await expect.element(page.getByTestId('system-settings-central-server-address-edit-dialog')).toBeVisible();

    const addressField = page
      .getByTestId('system-settings-central-server-address-edit-field')
      .getByRole('textbox');

    await addressField.fill('invalid-edited.example.org%');

    submitDialogForm();

    // Client-side 'address' rule fires for chars outside [a-zA-Z0-9-.].
    await expect.element(page.getByText('The Central Server address field contains invalid characters')).toBeVisible();
  });
});
