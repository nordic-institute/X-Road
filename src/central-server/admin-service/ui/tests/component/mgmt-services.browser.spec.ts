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
import type { ManagementServicesConfiguration, PagedSecurityServers } from '@/openapi-types';

const SYSTEM_SETTINGS_PATH = '/settings/system-settings';

// SystemSettingsView also renders MemberClasses; mock it to prevent 502 errors.
const memberClassesHandler = specHttp.get('/member-classes', ({ response }) => response(200).json([]));

// Provider is already selected but no security server registered yet.
const configWithProvider: ManagementServicesConfiguration = {
  service_provider_id: 'CS:E2E-TC1:e2e-member-management:e2e-sub-management',
  service_provider_name: 'E2E Management Member',
  security_server_id: '',
  wsdl_address: 'http://valid-edited.example.org/managementservices.wsdl',
  services_address: 'https://valid-edited.example.org:4002/managementservice/manage/',
  security_server_owners_global_group_code: 'security-server-owners',
};

const configAfterRegister: ManagementServicesConfiguration = {
  ...configWithProvider,
  security_server_id: 'SERVER:CS:E2E-TC1:e2e-tc1-member-subsystem:E2E-SS3',
};

const securityServersPage: PagedSecurityServers = {
  items: [
    {
      server_id: {
        instance_id: 'CS',
        member_class: 'E2E-TC1',
        member_code: 'e2e-tc1-member-subsystem',
        server_code: 'E2E-SS3',
        type: 'SecurityServerId',
        encoded_id: 'CS:E2E-TC1:e2e-tc1-member-subsystem:E2E-SS3',
      },
      owner_name: 'E2E TC1 Member',
      in_maintenance_mode: false,
      server_address: 'ss3.example.org',
      updated_at: '2024-01-01T00:00:00Z',
      created_at: '2024-01-01T00:00:00Z',
    },
  ],
  paging_metadata: { total_items: 1, items: 1, limit: 25, offset: 0 },
};

describe('0970 — CS Mgmt Services — select-SS dialog updates security server field + edit button hidden (Browser Mode)', () => {
  it('opening select-security-server dialog, picking a server sets the security_server_id field and hides the edit button', async () => {
    let configCallCount = 0;
    await renderRoute(SYSTEM_SETTINGS_PATH, {
      permissions: [Permissions.VIEW_SYSTEM_SETTINGS, Permissions.REGISTER_SERVICE_PROVIDER],
      msw: [
        memberClassesHandler,
        specHttp.get('/management-services-configuration', ({ response }) => {
          configCallCount += 1;
          return configCallCount === 1
            ? response(200).json(configWithProvider)
            : response(200).json(configAfterRegister);
        }),
        specHttp.post('/management-services-configuration/register-provider', ({ response }) =>
          response(200).json(configAfterRegister),
        ),
        specHttp.get('/security-servers', ({ response }) =>
          response(200).json(securityServersPage as never),
        ),
      ],
    });

    await expect.element(page.getByTestId('edit-management-security-server')).toBeVisible();
    await page.getByTestId('edit-management-security-server').click();

    await expect.element(page.getByTestId('management-security-server-search-field')).toBeVisible();

    // select the first server row — single-select strategy has no header checkbox, nth(0) = first data row
    await page.getByRole('dialog').getByRole('checkbox').nth(0).click();

    await expect.element(page.getByTestId('dialog-save-button')).not.toBeDisabled();
    await page.getByTestId('dialog-save-button').click();

    await expect.element(page.getByTestId('management-security-server-field')).toHaveTextContent(
      'SERVER:CS:E2E-TC1:e2e-tc1-member-subsystem:E2E-SS3',
    );

    // Edit button is hidden once provider is registered (canEditSecurityServer = false)
    expect(page.getByTestId('edit-management-security-server').query()).toBeNull();
  });
});
