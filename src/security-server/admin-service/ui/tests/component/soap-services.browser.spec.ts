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
import type { Service, ServiceDescription, ServiceClient } from '@/openapi-types';

// ── AJV schemas ───────────────────────────────────────────────────────────────

const serviceSchema = {
  type: 'object',
  required: ['id', 'service_code', 'url', 'timeout', 'service_description_id', 'client_id'],
  properties: {
    id: { type: 'string' },
    service_code: { type: 'string' },
    full_service_code: { type: 'string' },
    url: { type: 'string' },
    timeout: { type: 'integer' },
    ssl_auth: { type: 'boolean' },
    service_description_id: { type: 'string' },
    client_id: { type: 'string' },
  },
};

const serviceDescriptionSchema = {
  type: 'object',
  required: ['id', 'url', 'type', 'disabled', 'disabled_notice', 'refreshed_at', 'services', 'client_id'],
  properties: {
    id: { type: 'string' },
    url: { type: 'string' },
    type: { type: 'string', enum: ['WSDL', 'REST', 'OPENAPI3'] },
    disabled: { type: 'boolean' },
    disabled_notice: { type: 'string' },
    refreshed_at: { type: 'string', format: 'date-time' },
    services: { type: 'array' },
    client_id: { type: 'string' },
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

const clientId = 'DEV:COM:1234:test-service';
const serviceDescriptionId = 'wsdl-desc-1';
const serviceId = 'DEV:COM:1234:test-service:testOp1';

const serviceFixture: Service = {
  id: serviceId,
  service_code: 'testOp1',
  full_service_code: 'testOp1',
  url: 'http://mock-server:1080/test-services/testservice1.wsdl',
  timeout: 60,
  ssl_auth: false,
  service_description_id: serviceDescriptionId,
  client_id: clientId,
};

const serviceDescriptionFixture: ServiceDescription = {
  id: serviceDescriptionId,
  url: 'http://mock-server:1080/test-services/testservice1.wsdl',
  type: 'WSDL',
  disabled: false,
  disabled_notice: '',
  refreshed_at: '2024-01-01T00:00:00.000Z',
  services: [],
  client_id: clientId,
};

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

// Full candidate set returned when no filter is applied (3 items).
// Two-sided requirement: "Beta Corp" is filtered OUT by "Alpha" query;
// "Alpha Corp" is present while filtered; all 3 are back after clear.
const allCandidates: ServiceClient[] = [
  { id: 'DEV:COM:1234:test-service', name: 'Alpha Corp', service_client_type: 'SUBSYSTEM', rights_given_at: undefined },
  { id: 'DEV:COM:4321:other-service', name: 'Beta Corp', service_client_type: 'SUBSYSTEM', rights_given_at: undefined },
  { id: 'DEV:security-server-owners', name: 'Security Server owners', service_client_type: 'GLOBALGROUP', rights_given_at: undefined },
];

// Filtered set returned when member_name_group_description contains "Alpha".
const filteredCandidates: ServiceClient[] = [
  { id: 'DEV:COM:1234:test-service', name: 'Alpha Corp', service_client_type: 'SUBSYSTEM', rights_given_at: undefined },
];

validateBody(serviceSchema, serviceFixture);
validateBody(serviceDescriptionSchema, serviceDescriptionFixture);
validateBody(clientSchema, clientFixture);
validateBody({ type: 'array', items: serviceClientSchema }, allCandidates);
validateBody({ type: 'array', items: serviceClientSchema }, filteredCandidates);

// ── Permissions ───────────────────────────────────────────────────────────────

const servicePermissions = [
  Permissions.VIEW_CLIENTS,
  Permissions.VIEW_CLIENT_DETAILS,
  Permissions.VIEW_CLIENT_SERVICES,
  Permissions.VIEW_CLIENT_ACL_SUBJECTS,
  Permissions.EDIT_SERVICE_PARAMS,
  Permissions.EDIT_ACL_SUBJECT_OPEN_SERVICES,
  Permissions.ADD_WSDL,
];

// ── Route ─────────────────────────────────────────────────────────────────────

const encodedServiceId = encodeURIComponent(serviceId);
const serviceParametersRoute = `/service/${encodedServiceId}/parameters`;

// ── Helpers ───────────────────────────────────────────────────────────────────

function getDialogRowTexts(): string[] {
  return page
    .getByTestId('add-subjects-dialog')
    .getByRole('row')
    .elements()
    .slice(1)
    .map((el) => el.textContent?.trim() ?? '');
}

// ─────────────────────────────────────────────────────────────────────────────
// Subjects search filter: fill → two-sided assert → clear → restore assert
// ─────────────────────────────────────────────────────────────────────────────
//
// The AccessRightsDialog performs a server-side search via
//   GET /clients/{id}/service-client-candidates?member_name_group_description=...
// The MSW handler inspects the query param and returns either the full set or
// the filtered set.  Two-sided assertion:
//   - while filtered: "Alpha Corp" present, "Beta Corp" absent
//   - after clear:    "Beta Corp" present, "Alpha Corp" present
// Removing the name fill (skip the filter step) leaves member_name_group_description
// empty on the first search → the handler returns allCandidates → "Beta Corp"
// is present → the absence assertion fails.
//
// MIGRATED-FROM: 0560-ss-client-soap-services.feature :: "Client service access rights subjects search filter clearing restore initial state"

describe('SOAP services — subjects search filter: fill, two-sided assert, clear, restore (Browser Mode)', () => {
  it('filters subjects when a name is entered and restores all subjects after the filter is cleared', async () => {
    const candidatesHandler = specHttp.get('/clients/{id}/service-client-candidates', ({ request, response }) => {
      const url = new URL(request.url);
      const nameFilter = url.searchParams.get('member_name_group_description') ?? '';
      if (nameFilter.length > 0) {
        return response(200).json(filteredCandidates);
      }
      return response(200).json(allCandidates);
    });

    await renderRoute(serviceParametersRoute, {
      permissions: servicePermissions,
      msw: [
        specHttp.get('/services/{id}', ({ response }) => response(200).json(serviceFixture)),
        specHttp.get('/service-descriptions/{id}', ({ response }) => response(200).json(serviceDescriptionFixture)),
        specHttp.get('/clients/{id}', ({ response }) => response(200).json(clientFixture)),
        specHttp.get('/services/{id}/service-clients', ({ response }) => response(200).json([])),
        specHttp.get('/member-classes', ({ response }) => response(200).json(['COM', 'GOV'])),
        specHttp.get('/xroad-instances', ({ response }) => response(200).json([])),
        candidatesHandler,
      ],
    });

    await expect.element(page.getByTestId('show-add-subjects')).toBeVisible();
    await page.getByTestId('show-add-subjects').click();

    await expect.element(page.getByTestId('add-subjects-dialog')).toBeVisible();

    // First search: no filter — full set expected.
    await page.getByTestId('search-button').click();

    await expect.element(page.getByTestId('add-subjects-dialog').getByRole('row').nth(1)).toBeVisible();

    const beforeFilter = getDialogRowTexts();
    const beforeJoined = beforeFilter.join('\n');
    expect(beforeJoined).toContain('Alpha Corp');
    expect(beforeJoined).toContain('Beta Corp');
    expect(beforeJoined).toContain('Security Server owners');

    // Fill filter name → search → two-sided assert (filtered-out absent, match present).
    const nameField = page.getByTestId('name-text-field').getByRole('textbox');
    await nameField.fill('Alpha');
    await page.getByTestId('search-button').click();

    await expect.element(page.getByTestId('add-subjects-dialog').getByRole('row').nth(1)).toBeVisible();

    const filtered = getDialogRowTexts();
    const filteredJoined = filtered.join('\n');
    expect(filteredJoined).toContain('Alpha Corp');
    expect(filteredJoined).not.toContain('Beta Corp');

    // Clear the name field → search → assert all subjects are restored.
    await nameField.clear();
    await page.getByTestId('search-button').click();

    await expect.element(page.getByTestId('add-subjects-dialog').getByRole('row').nth(1)).toBeVisible();

    const afterClear = getDialogRowTexts();
    const afterJoined = afterClear.join('\n');
    expect(afterJoined).toContain('Alpha Corp');
    expect(afterJoined).toContain('Beta Corp');
    expect(afterJoined).toContain('Security Server owners');
  });
});
