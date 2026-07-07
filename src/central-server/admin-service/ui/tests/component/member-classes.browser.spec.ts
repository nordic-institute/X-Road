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
  Permissions.ADD_MEMBER_CLASS,
  Permissions.EDIT_MEMBER_CLASS,
  Permissions.DELETE_MEMBER_CLASS,
];

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

async function renderSystemSettings() {
  return renderRoute('/settings/system-settings', {
    permissions: systemSettingsPermissions,
    msw: [emptyMemberClassesHandler, managementServicesHandler],
  });
}

describe('0300 — CS Member Classes — invalid description on add (Browser Mode)', () => {
  it('entering invalid description characters in add dialog shows inline validation error', async () => {
    await renderSystemSettings();

    await expect.element(page.getByTestId('member-classes-list')).toBeVisible();

    await page.getByTestId('system-settings-add-member-class-button').click();

    await expect.element(page.getByTestId('system-settings-member-class-edit-dialog')).toBeVisible();

    await page
      .getByTestId('system-settings-member-class-code-edit-field')
      .getByRole('textbox')
      .fill('E2E-TC0400-3');

    // Type invalid characters into description
    await page
      .getByTestId('system-settings-member-class-description-edit-field')
      .getByRole('textbox')
      .fill('invalid-desc$€');

    submitDialogForm();

    await expect.element(page.getByText('Use valid description characters only')).toBeVisible();
  });
});

describe('0300 — CS Member Classes — invalid description on edit (Browser Mode)', () => {
  it('entering invalid description characters in edit dialog shows inline validation error', async () => {
    const existingClass: MemberClass = { code: 'E2E-TC0400-4', description: 'initial-desc' };

    // Override the member-classes handler to return one existing class so the edit button renders.
    await renderRoute('/settings/system-settings', {
      permissions: systemSettingsPermissions,
      msw: [
        specHttp.get('/member-classes', ({ response }) => response(200).json([existingClass])),
        managementServicesHandler,
      ],
    });

    await expect.element(page.getByTestId('member-classes-list')).toBeVisible();

    // Open edit dialog for the existing member class
    await page.getByTestId('system-settings-edit-member-class-button').click();

    await expect.element(page.getByTestId('system-settings-member-class-edit-dialog')).toBeVisible();

    // Clear description and type invalid characters
    const descField = page.getByTestId('system-settings-member-class-description-edit-field').getByRole('textbox');
    await descField.fill('invalid-desc$€');

    submitDialogForm();

    await expect.element(page.getByText('Use valid description characters only')).toBeVisible();
  });
});
