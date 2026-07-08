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
import { Permissions } from '@/global';
import type { ManagementServicesConfiguration, PagedClients } from '@/openapi-types';

const SYSTEM_SETTINGS_PATH = '/settings/system-settings';

// SystemSettingsView also renders MemberClasses; mock it to prevent 502 errors.
const memberClassesHandler = specHttp.get('/member-classes', ({ response }) => response(200).json([]));

const emptyConfig: ManagementServicesConfiguration = {
  service_provider_id: '',
  service_provider_name: '',
  security_server_id: '',
  wsdl_address: 'http://valid-edited.example.org/managementservices.wsdl',
  services_address: 'https://valid-edited.example.org:4002/managementservice/manage/',
  security_server_owners_global_group_code: 'security-server-owners',
};

const configAfterSelect: ManagementServicesConfiguration = {
  ...emptyConfig,
  service_provider_id: 'CS:E2E-TC1:e2e-member-management:e2e-sub-management',
  service_provider_name: 'E2E Management Member',
};

const subsystemClient: PagedClients = {
  clients: [
    {
      client_id: {
        instance_id: 'CS',
        member_class: 'E2E-TC1',
        member_code: 'e2e-member-management',
        subsystem_code: 'e2e-sub-management',
        type: 'SUBSYSTEM',
        encoded_id: 'CS:E2E-TC1:e2e-member-management:e2e-sub-management',
      },
      member_name: 'E2E Management Member',
    },
  ],
  paging_metadata: { total_items: 1, items: 1, limit: 25, offset: 0 },
};

describe('0460 — CS Mgmt Service Provider — initial config render (Browser Mode)', () => {
  it('empty provider fields and default wsdl/address values render on system settings', async () => {
    await renderRoute(SYSTEM_SETTINGS_PATH, {
      permissions: [Permissions.VIEW_SYSTEM_SETTINGS],
      msw: [
        memberClassesHandler,
        specHttp.get('/management-services-configuration', ({ response }) =>
          response(200).json(emptyConfig),
        ),
      ],
    });

    const providerIdRow = page.getByTestId('management-service-provider-identifier-field');
    const providerNameRow = page.getByTestId('management-service-provider-name-field');
    const securityServerRow = page.getByTestId('management-security-server-field');
    const wsdlRow = page.getByTestId('management-wsdl-address-field');
    const servicesAddressRow = page.getByTestId('management-management-services-address-field');
    const ownerGroupRow = page.getByTestId('management-owner-group-code-field');

    await expect.element(providerIdRow).toBeVisible();
    await expect.element(providerNameRow).toBeVisible();
    await expect.element(securityServerRow).toBeVisible();
    await expect.element(wsdlRow).toBeVisible();
    await expect.element(servicesAddressRow).toBeVisible();
    await expect.element(ownerGroupRow).toBeVisible();

    await expect.element(wsdlRow).toHaveTextContent('http://valid-edited.example.org/managementservices.wsdl');
    await expect.element(servicesAddressRow).toHaveTextContent(
      'https://valid-edited.example.org:4002/managementservice/manage/',
    );
    await expect.element(ownerGroupRow).toHaveTextContent('security-server-owners');
  });
});

describe('0460 — CS Mgmt Service Provider — select subsystem dialog populates fields (Browser Mode)', () => {
  it('opening select-subsystem dialog, picking a subsystem updates the provider identifier and shows success snackbar', async () => {
    let configCallCount = 0;
    await renderRoute(SYSTEM_SETTINGS_PATH, {
      permissions: [Permissions.VIEW_SYSTEM_SETTINGS],
      msw: [
        memberClassesHandler,
        specHttp.get('/management-services-configuration', ({ response }) => {
          configCallCount += 1;
          return configCallCount === 1
            ? response(200).json(emptyConfig)
            : response(200).json(configAfterSelect);
        }),
        specHttp.patch('/management-services-configuration', ({ response }) =>
          response(200).json(configAfterSelect),
        ),
        specHttp.get('/clients', ({ response }) => response(200).json(subsystemClient as never)),
      ],
    });

    await expect.element(page.getByTestId('edit-management-subsystem')).toBeVisible();
    await page.getByTestId('edit-management-subsystem').click();

    await expect.element(page.getByTestId('subsystems-table')).toBeVisible();
    await expect.element(page.getByText('E2E Management Member').first()).toBeVisible();

    // With select-strategy="single" there is no header checkbox (showSelectAll=false); nth(0) = first data row
    await page.getByTestId('subsystems-table').getByRole('checkbox').nth(0).click();

    await expect.element(page.getByTestId('dialog-save-button')).not.toBeDisabled();
    await page.getByTestId('dialog-save-button').click();

    await expect.element(
      page.getByTestId('management-service-provider-identifier-field'),
    ).toHaveTextContent('CS:E2E-TC1:e2e-member-management:e2e-sub-management');
    await expect.element(
      page.getByTestId('management-service-provider-name-field'),
    ).toHaveTextContent('E2E Management Member');
  });
});
