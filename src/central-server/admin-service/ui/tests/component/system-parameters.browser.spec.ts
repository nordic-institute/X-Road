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
import type { ConfigurablePropertyDto } from '@niis/shared-ui';

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

const configurablePropertiesFixture: ConfigurablePropertyDto[] = [
  {
    property_name: 'xroad.admin-service.some-param',
    default_value: '10',
    effective_value: '10',
    value_source: 'DEFAULT',
    scope: 'admin-service',
    restart_policy: 'SERVICES',
    restart_services: ['xroad-center'],
  },
  {
    property_name: 'xroad.signer.some-param',
    default_value: '1',
    effective_value: '1',
    value_source: 'DEFAULT',
    scope: 'signer',
    restart_policy: 'SERVICES',
    restart_services: ['xroad-signer'],
  },
  {
    property_name: 'xroad.common.some-param',
    default_value: '5',
    effective_value: '5',
    value_source: 'DEFAULT',
    restart_policy: 'ALL_SERVICES',
    restart_services: [],
  },
];

describe('CS System Parameters — restart warning lists service names (Browser Mode)', () => {
  const rowOf = (scope: string, propertyName: string) =>
    page
      .getByTestId(`configurable-properties-table-body-${scope}`)
      .getByTestId('configurable-property-row')
      .filter({ hasText: propertyName });

  const editProperty = async (scope: string, propertyName: string, value: string) => {
    await rowOf(scope, propertyName).getByTestId('edit-configurable-property-button').click();
    await expect.element(page.getByTestId('configurable-property-value-field')).toBeVisible();
    const input = page.getByTestId('configurable-property-value-field').getByRole('textbox');
    await input.clear();
    await input.fill(value);
    await page.getByRole('button', { name: /save/i }).click();
    await expect.element(page.getByTestId('configurable-property-value-field')).not.toBeInTheDocument();
  };

  const renderPage = () =>
    renderRoute('/settings/system-parameters', {
      permissions: [Permissions.CHANGE_CONFIGURATION_PROPERTY],
      msw: [
        specHttp.get('/system/property', ({ response }) =>
          response(200).json(configurablePropertiesFixture),
        ),
        specHttp.patch('/system/property', ({ response }) => response(204).empty()),
      ],
    });

  it('shows xroad-center after an admin-service property is edited', async () => {
    await renderPage();
    await page.getByTestId('configurable-properties-panel-title-admin-service').click();

    expect(page.getByTestId('configurable-properties-restart-warning').query()).toBeNull();

    await editProperty('admin-service', 'xroad.admin-service.some-param', '20');

    await expect
      .element(page.getByTestId('configurable-properties-restart-services'))
      .toMatchTextContent(/: xroad-center\.$/);
  });

  it('lists the service names of two edited categories', async () => {
    await renderPage();
    await page.getByTestId('configurable-properties-panel-title-admin-service').click();
    await page.getByTestId('configurable-properties-panel-title-signer').click();

    await editProperty('admin-service', 'xroad.admin-service.some-param', '20');
    await editProperty('signer', 'xroad.signer.some-param', '2');

    await expect
      .element(page.getByTestId('configurable-properties-restart-services'))
      .toMatchTextContent(/: xroad-center, xroad-signer\.$/);
  });

  it('shows the restart-all line for a shared property edit', async () => {
    await renderPage();
    await page.getByTestId('configurable-properties-panel-title-common').click();

    await editProperty('common', 'xroad.common.some-param', '6');

    await expect.element(page.getByTestId('configurable-properties-restart-all')).toBeVisible();
    expect(page.getByTestId('configurable-properties-restart-services').query()).toBeNull();
  });
});
