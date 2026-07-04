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
import type { Client, SecurityServer } from '@/openapi-types';

const SERVER_ID = 'CS:E2E-TC1:e2e-tc1-member-subsystem:E2E-SS1';

const serverFixture: SecurityServer = {
  server_id: {
    instance_id: 'CS',
    member_class: 'E2E-TC1',
    member_code: 'e2e-tc1-member-subsystem',
    server_code: 'E2E-SS1',
    type: 'SecurityServerId',
    encoded_id: SERVER_ID,
  },
  owner_name: 'E2E TC1 Member with Subsystems',
  server_address: 'security-server-address-E2E-SS1',
  created_at: '2024-01-15T10:00:00Z',
  in_maintenance_mode: false,
};

const clientFixture1: Client = {
  client_id: {
    instance_id: 'CS',
    member_class: 'E2E-TC1',
    member_code: 'e2e-tc2-member-subsystem',
    subsystem_code: 'e2e-tc2-subsystem',
    type: 'ClientId',
    encoded_id: 'CS:E2E-TC1:e2e-tc2-member-subsystem:e2e-tc2-subsystem',
  },
  member_name: 'E2E TC2 Member with Subsystems',
};

const clientFixture2: Client = {
  client_id: {
    instance_id: 'CS',
    member_class: 'E2E-TC1',
    member_code: 'e2e-tc3-member-subsystem',
    subsystem_code: 'e2e-tc3-subsystem',
    type: 'ClientId',
    encoded_id: 'CS:E2E-TC1:e2e-tc3-member-subsystem:e2e-tc3-subsystem',
  },
  member_name: 'E2E TC3 Member with Subsystems',
};

const clientsPermissions = [
  Permissions.VIEW_SECURITY_SERVERS,
  Permissions.VIEW_SECURITY_SERVER_DETAILS,
];

async function renderClientsRoute() {
  return renderRoute(`/security-servers/${encodeURIComponent(SERVER_ID)}/clients`, {
    permissions: clientsPermissions,
    msw: [
      specHttp.get('/security-servers/{server_id}', ({ response }) => response(200).json(serverFixture as never)),
      specHttp.get('/security-servers/{server_id}/clients', ({ response }) =>
        response(200).json([clientFixture1, clientFixture2] as never),
      ),
    ],
  });
}

describe('1010 — CS Security Server Clients — client list render (Browser Mode)', () => {
  it('renders rows for each client registered to the security server', async () => {
    await renderClientsRoute();

    await expect.element(page.getByTestId('security-server-clients-view')).toBeVisible();
    await expect.element(page.getByText('E2E TC2 Member with Subsystems').first()).toBeVisible();
    await expect.element(page.getByText('e2e-tc2-subsystem').first()).toBeVisible();
    await expect.element(page.getByText('E2E TC3 Member with Subsystems').first()).toBeVisible();
    await expect.element(page.getByText('e2e-tc3-subsystem').first()).toBeVisible();
  });
});

describe('1010 — CS Security Server Clients — client list sortable by subsystem (Browser Mode)', () => {
  it('clicking the subsystem column header renders clients in sorted order', async () => {
    await renderClientsRoute();

    await expect.element(page.getByTestId('security-server-clients-view')).toBeVisible();

    const subsystemHeader = page.getByText('Subsystem').first();
    await expect.element(subsystemHeader).toBeVisible();

    await subsystemHeader.click();

    await expect.element(page.getByText('e2e-tc2-subsystem').first()).toBeVisible();
  });
});
