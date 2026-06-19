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
import type { Client, XRoadInstance, SecurityServer, GlobalConfConnectionStatus, ConnectionStatus } from '@/openapi-types';

// ── AJV schemas ───────────────────────────────────────────────────────────────

const xRoadInstanceSchema = {
  type: 'object',
  required: ['identifier', 'local'],
  properties: {
    identifier: { type: 'string' },
    local: { type: 'boolean' },
  },
};

const clientSchema = {
  type: 'object',
  required: ['member_class', 'member_code'],
  properties: {
    id: { type: 'string' },
    instance_id: { type: 'string' },
    member_name: { type: 'string' },
    member_class: { type: 'string' },
    member_code: { type: 'string' },
    subsystem_code: { type: 'string' },
    owner: { type: 'boolean' },
    has_valid_local_sign_cert: { type: 'boolean' },
    connection_type: { type: 'string', enum: ['HTTP', 'HTTPS', 'HTTPS_NO_AUTH'] },
    status: {
      type: 'string',
      enum: ['REGISTERED', 'SAVED', 'GLOBAL_ERROR', 'REGISTRATION_IN_PROGRESS', 'DELETION_IN_PROGRESS'],
    },
  },
};

const securityServerSchema = {
  type: 'object',
  required: ['id'],
  properties: {
    id: { type: 'string' },
    instance_id: { type: 'string' },
    member_class: { type: 'string' },
    member_code: { type: 'string' },
    server_code: { type: 'string' },
    server_address: { type: 'string' },
  },
};

const connectionStatusSchema = {
  type: 'object',
  required: ['status_class', 'error'],
  properties: {
    status_class: { type: 'string', enum: ['OK', 'WAITING', 'FAIL'] },
    error: {
      type: 'object',
      required: ['code'],
      properties: {
        code: { type: 'string' },
      },
    },
  },
};

const globalConfConnectionStatusSchema = {
  type: 'object',
  required: ['download_url', 'connection_status'],
  properties: {
    download_url: { type: 'string' },
    connection_status: connectionStatusSchema,
  },
};

// ── Fixtures ──────────────────────────────────────────────────────────────────

const xRoadInstancesFixture: XRoadInstance[] = [
  { identifier: 'DEV', local: true },
];

const localClientsFixture: Client[] = [
  {
    id: 'DEV:COM:1234',
    instance_id: 'DEV',
    member_name: 'Test member',
    member_class: 'COM',
    member_code: '1234',
    owner: true,
    has_valid_local_sign_cert: true,
    connection_type: 'HTTPS',
    status: 'REGISTERED',
  },
  {
    id: 'DEV:COM:1234:TestSubs',
    instance_id: 'DEV',
    member_name: 'Test member',
    member_class: 'COM',
    member_code: '1234',
    subsystem_code: 'TestSubs',
    owner: false,
    has_valid_local_sign_cert: true,
    connection_type: 'HTTPS',
    status: 'REGISTERED',
  },
];

const subsystemsFixture: Client[] = [
  {
    id: 'DEV:COM:1234:MANAGEMENT',
    instance_id: 'DEV',
    member_name: 'Test member',
    member_class: 'COM',
    member_code: '1234',
    subsystem_code: 'MANAGEMENT',
    owner: false,
    has_valid_local_sign_cert: false,
    connection_type: 'HTTPS',
    status: 'REGISTERED',
  },
];

const securityServersFixture: SecurityServer[] = [
  {
    id: 'DEV:COM:1234:SS0',
    instance_id: 'DEV',
    member_class: 'COM',
    member_code: '1234',
    server_code: 'SS0',
  },
];

const failedConnectionStatusFixture: ConnectionStatus = {
  status_class: 'FAIL',
  error: { code: 'server.connection.failed' },
};

const globalConfStatusesFixture: GlobalConfConnectionStatus[] = [
  {
    download_url: 'http://cs:80/internalconf',
    connection_status: failedConnectionStatusFixture,
  },
  {
    download_url: 'https://cs:443/internalconf',
    connection_status: failedConnectionStatusFixture,
  },
];

// ── AJV validation ────────────────────────────────────────────────────────────

validateBody({ type: 'array', items: xRoadInstanceSchema }, xRoadInstancesFixture);
validateBody({ type: 'array', items: clientSchema }, localClientsFixture);
validateBody({ type: 'array', items: clientSchema }, subsystemsFixture);
validateBody({ type: 'array', items: securityServerSchema }, securityServersFixture);
validateBody(connectionStatusSchema, failedConnectionStatusFixture);
validateBody({ type: 'array', items: globalConfConnectionStatusSchema }, globalConfStatusesFixture);

// ── Permissions ───────────────────────────────────────────────────────────────

const diagnosticsPermissions = [Permissions.DIAGNOSTICS];

// ── Base handlers for ConnectionContainer mount ───────────────────────────────

function baseHandlers() {
  return [
    specHttp.get('/xroad-instances', ({ response }) => response(200).json(xRoadInstancesFixture)),
    specHttp.get('/clients', ({ request, response }) => {
      const url = new URL(request.url);
      if (url.searchParams.get('show_members') === 'false') {
        return response(200).json(subsystemsFixture);
      }
      return response(200).json(localClientsFixture);
    }),
    specHttp.get('/diagnostics/auth-cert-req-status', ({ response }) =>
      response(200).json(failedConnectionStatusFixture),
    ),
    specHttp.get('/diagnostics/global-conf-status', ({ response }) =>
      response(200).json(globalConfStatusesFixture),
    ),
  ];
}

// ─────────────────────────────────────────────────────────────────────────────

describe('Diagnostics connection — Central Server Test buttons always enabled (Browser Mode)', () => {
  it('global conf test button and auth cert test button are enabled on page load', async () => {
    await renderRoute('/diagnostics/connection', {
      permissions: diagnosticsPermissions,
      msw: baseHandlers(),
    });

    await expect.element(page.getByTestId('central-server-global-conf-test-button')).toBeVisible();
    await expect
      .element(page.getByTestId('central-server-global-conf-test-button'))
      .not.toBeDisabled();

    await expect.element(page.getByTestId('central-server-auth-cert-test-button')).toBeVisible();
    await expect
      .element(page.getByTestId('central-server-auth-cert-test-button'))
      .not.toBeDisabled();
  });
});

describe('Diagnostics connection — Other-SS Test button gating (Browser Mode)', () => {
  it('Other-SS Test button is disabled until client, protocol and server are selected', async () => {
    await renderRoute('/diagnostics/connection', {
      permissions: diagnosticsPermissions,
      msw: [
        ...baseHandlers(),
        specHttp.get('/clients/{id}/security-servers', ({ response }) =>
          response(200).json(securityServersFixture),
        ),
      ],
    });

    await expect.element(page.getByTestId('other-security-server-test-button')).toBeVisible();
    await expect.element(page.getByTestId('other-security-server-test-button')).toBeDisabled();

    await page.getByTestId('other-security-server-client-id').click();
    await expect.element(page.getByRole('option', { name: 'DEV:COM:1234:TestSubs' })).toBeVisible();
    await page.getByRole('option', { name: 'DEV:COM:1234:TestSubs' }).click();
    await expect.element(page.getByTestId('other-security-server-test-button')).toBeDisabled();

    await page.getByTestId('other-security-server-rest-radio-button').getByRole('radio').click();
    await expect.element(page.getByTestId('other-security-server-test-button')).toBeDisabled();

    await page.getByTestId('other-security-server-target-client-id').click();
    await expect.element(page.getByRole('option', { name: 'DEV:COM:1234:MANAGEMENT' })).toBeVisible();
    await page.getByRole('option', { name: 'DEV:COM:1234:MANAGEMENT' }).click();

    await expect.element(page.getByTestId('other-security-server-test-button')).not.toBeDisabled();
  });
});

describe('Diagnostics connection — target instance prefilling (Browser Mode)', () => {
  it('target instance v-select is prefilled with the local instance identifier', async () => {
    await renderRoute('/diagnostics/connection', {
      permissions: diagnosticsPermissions,
      msw: baseHandlers(),
    });

    const instanceSelect = page.getByTestId('other-security-server-target-instance');
    await expect.element(instanceSelect).toBeVisible();

    await expect.element(instanceSelect.getByText('DEV')).toBeVisible();
  });
});

describe('Diagnostics connection — target server auto-prefill on single result (Browser Mode)', () => {
  it('security server field is auto-filled when fetchSecurityServers returns exactly one result', async () => {
    await renderRoute('/diagnostics/connection', {
      permissions: diagnosticsPermissions,
      msw: [
        ...baseHandlers(),
        specHttp.get('/clients/{id}/security-servers', ({ response }) =>
          response(200).json(securityServersFixture),
        ),
      ],
    });

    await expect.element(page.getByTestId('other-security-server-target-client-id')).toBeVisible();

    await page.getByTestId('other-security-server-target-client-id').click();
    await expect.element(page.getByRole('option', { name: 'DEV:COM:1234:MANAGEMENT' })).toBeVisible();
    await page.getByRole('option', { name: 'DEV:COM:1234:MANAGEMENT' }).click();

    const serverCombo = page.getByTestId('other-security-server-id');
    await expect.element(serverCombo.getByText('SS0')).toBeVisible();
  });
});
