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
import type { ServiceClient } from '@/openapi-types';

// ── AJV schemas ───────────────────────────────────────────────────────────────

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
    status: { type: 'string', enum: ['REGISTERED', 'SAVED', 'GLOBAL_ERROR', 'REGISTRATION_IN_PROGRESS', 'DELETION_IN_PROGRESS'] },
  },
};

const serviceClientSchema = {
  type: 'object',
  required: ['id'],
  properties: {
    id: { type: 'string' },
    name: { type: 'string' },
    local_group_code: { type: 'string' },
    service_client_type: { type: 'string' },
    rights_given_at: { type: 'string', format: 'date-time' },
  },
};

// ── Fixtures ──────────────────────────────────────────────────────────────────
//
// Sort fixture design:
//   The service-clients table sorts by the "Name" column (key: "name") by default
//   (Vuetify must-sort).  Sort by "ID" column (key: "id") is triggered by clicking
//   the column header.
//
//   Three items with IDs that sort in a different order by name vs by id:
//     By name asc:  "Security Server owners" < "Test client" < "Test consumer"
//     By id  asc:   "DEV:COM:1234:test-consumer" < "DEV:COM:4321:TestClient" < "DEV:security-server-owners"
//
//   After an id-asc sort click:
//     row[0] = "Test consumer"  (DEV:COM:1234:test-consumer)
//     row[1] = "Test client"    (DEV:COM:4321:TestClient)
//     row[2] = "Security Server owners" (DEV:security-server-owners)
//
//   consumerIdx < clientIdx proves sort fired (they are adjacent and in a different
//   relative order from their name-sort order).  Removing the column header click
//   leaves name-sort: clientIdx(1) < consumerIdx(2) → consumerIdx < clientIdx fails.
//
// Filter fixture design:
//   Filtering by "consumer" (client-side :search on VDataTable):
//     present: "Test consumer" (id contains "test-consumer", name contains "consumer")
//     absent:  "Security Server owners", "Test client"
//   Two-sided: filtered-out absent AND match present.
//   Removing the fill() call leaves search empty → all rows visible → the absence
//   assertion fails.

const clientId = 'DEV:COM:1234:test-service';
const encodedClientId = encodeURIComponent(clientId);

const serviceClientsFixture: ServiceClient[] = [
  {
    id: 'DEV:security-server-owners',
    name: 'Security Server owners',
    service_client_type: 'GLOBALGROUP',
    rights_given_at: '2024-01-01T00:00:00.000Z',
  },
  {
    id: 'DEV:COM:4321:TestClient',
    name: 'Test client',
    service_client_type: 'SUBSYSTEM',
    rights_given_at: '2024-01-02T00:00:00.000Z',
  },
  {
    id: 'DEV:COM:1234:test-consumer',
    name: 'Test consumer',
    service_client_type: 'SUBSYSTEM',
    rights_given_at: '2024-01-03T00:00:00.000Z',
  },
];

const clientFixture = {
  id: clientId,
  instance_id: 'DEV',
  member_name: 'Test Service',
  member_class: 'COM',
  member_code: '1234',
  subsystem_code: 'test-service',
  owner: false,
  has_valid_local_sign_cert: true,
  connection_type: 'HTTPS' as const,
  status: 'REGISTERED' as const,
};

validateBody({ type: 'array', items: serviceClientSchema }, serviceClientsFixture);
validateBody(clientSchema, clientFixture);

// ── Permissions ───────────────────────────────────────────────────────────────

const serviceClientPermissions = [
  Permissions.VIEW_CLIENTS,
  Permissions.VIEW_CLIENT_DETAILS,
  Permissions.VIEW_CLIENT_ACL_SUBJECTS,
];

// ── Route ─────────────────────────────────────────────────────────────────────

const serviceClientsRoute = `/clients/subsystem/${encodedClientId}/service-clients`;

// ── Handlers ──────────────────────────────────────────────────────────────────

const clientHandler = specHttp.get('/clients/{id}', ({ response }) => response(200).json(clientFixture));
const serviceClientsHandler = specHttp.get('/clients/{id}/service-clients', ({ response }) =>
  response(200).json(serviceClientsFixture),
);

// ── Helper ────────────────────────────────────────────────────────────────────

function getTableRowTexts(): string[] {
  return page
    .getByTestId('service-clients-main-view-table')
    .getByRole('row')
    .elements()
    .slice(1)
    .map((el) => el.textContent?.trim() ?? '');
}

// ─────────────────────────────────────────────────────────────────────────────
// Spec 1: Filter — two-sided assertion
// ─────────────────────────────────────────────────────────────────────────────

// MIGRATED-FROM: 0570-ss-client-service-clients.feature :: "Service client list can be filtered and sorted"

describe('Service clients — search filter: two-sided assertion (Browser Mode)', () => {
  it('shows matching service client and hides non-matching ones when filter is applied', async () => {
    await renderRoute(serviceClientsRoute, {
      permissions: serviceClientPermissions,
      msw: [clientHandler, serviceClientsHandler],
    });

    await expect.element(page.getByTestId('service-clients-main-view-table')).toBeVisible();

    const searchField = page.getByTestId('search-service-client').getByRole('textbox');
    await searchField.fill('consumer');

    await expect.element(page.getByTestId('service-clients-main-view-table')).toBeVisible();

    const rows = getTableRowTexts();
    const joined = rows.join('\n');

    expect(joined).toContain('Test consumer');
    expect(joined).not.toContain('Security Server owners');
    expect(joined).not.toContain('Test client');
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// Spec 2: Sort — relative row positions assert
// ─────────────────────────────────────────────────────────────────────────────
//
// Sort fixture proof (see top comment):
//   Default (name asc): Security Server owners(0) < Test client(1) < Test consumer(2)
//   After ID-asc click: test-consumer(0) < TestClient(1) < security-server-owners(2)
//   consumerIdx(0) < clientIdx(1): assertion holds.
//   Without the click the name-sort applies:
//     Test client is at index ~1, Test consumer at index ~2 → consumerIdx > clientIdx → FAIL.

// MIGRATED-FROM: 0570-ss-client-service-clients.feature :: "Service client list can be filtered and sorted"

describe('Service clients — sort by Name descending: row positions change (Browser Mode, 2 clicks)', () => {
  //
  // Sort fixture proof:
  //   Default (no explicit sort but must-sort triggers asc on first click):
  //     After 1st click (name asc):  Security Server owners(0) < Test client(1) < Test consumer(2)
  //     After 2nd click (name desc): Test consumer(0) > Test client(1) > Security Server owners(2)
  //   Removing the second click leaves name-asc: consumerIdx(2) > clientIdx(1) → FAIL.
  //
  it('reorders rows by Name descending when the Name column header is clicked twice', async () => {
    await renderRoute(serviceClientsRoute, {
      permissions: serviceClientPermissions,
      msw: [clientHandler, serviceClientsHandler],
    });

    await expect.element(page.getByTestId('service-clients-main-view-table')).toBeVisible();

    const nameHeader = page.getByRole('columnheader', { name: /subsystem name/i });
    await nameHeader.click();
    await nameHeader.click();

    await expect.element(page.getByTestId('service-clients-main-view-table')).toBeVisible();

    const rows = getTableRowTexts();

    const consumerIdx = rows.findIndex((r) => r.includes('Test consumer'));
    const clientIdx = rows.findIndex((r) => r.includes('Test client'));
    const ownersIdx = rows.findIndex((r) => r.includes('Security Server owners'));

    expect(consumerIdx).toBeGreaterThanOrEqual(0);
    expect(clientIdx).toBeGreaterThanOrEqual(0);
    expect(ownersIdx).toBeGreaterThanOrEqual(0);

    // By Name desc: Test consumer > Test client > Security Server owners
    expect(consumerIdx).toBeLessThan(clientIdx);
    expect(clientIdx).toBeLessThan(ownersIdx);
  });
});

// MIGRATED-FROM: 0570-ss-client-service-clients.feature :: "Service client list can be filtered and sorted"

describe('Service clients — sort by ID ascending: row positions change (Browser Mode)', () => {
  it('reorders rows by ID ascending when the ID column header is clicked', async () => {
    await renderRoute(serviceClientsRoute, {
      permissions: serviceClientPermissions,
      msw: [clientHandler, serviceClientsHandler],
    });

    await expect.element(page.getByTestId('service-clients-main-view-table')).toBeVisible();

    const idHeader = page.getByRole('columnheader', { name: /^id$/i });
    await idHeader.click();

    await expect.element(page.getByTestId('service-clients-main-view-table')).toBeVisible();

    const rows = getTableRowTexts();

    const consumerIdx = rows.findIndex((r) => r.includes('Test consumer'));
    const clientIdx = rows.findIndex((r) => r.includes('Test client'));
    const ownersIdx = rows.findIndex((r) => r.includes('Security Server owners'));

    expect(consumerIdx).toBeGreaterThanOrEqual(0);
    expect(clientIdx).toBeGreaterThanOrEqual(0);
    expect(ownersIdx).toBeGreaterThanOrEqual(0);

    // By ID asc: DEV:COM:1234:test-consumer < DEV:COM:4321:TestClient < DEV:security-server-owners
    expect(consumerIdx).toBeLessThan(clientIdx);
    expect(clientIdx).toBeLessThan(ownersIdx);
  });
});
