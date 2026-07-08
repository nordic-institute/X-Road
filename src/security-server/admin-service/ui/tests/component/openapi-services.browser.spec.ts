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
import { serviceDescriptionSchema } from '../setup/schemas';
import { Permissions } from '@/global';
import type { Service, ServiceDescription } from '@/openapi-types';
import { ServiceType } from '@/openapi-types';

// ── AJV schemas ───────────────────────────────────────────────────────────────

const endpointSchema = {
  type: 'object',
  required: ['service_code', 'method', 'path'],
  properties: {
    id: { type: 'string' },
    service_code: { type: 'string' },
    method: { type: 'string' },
    path: { type: 'string' },
    generated: { type: 'boolean' },
  },
};

const serviceSchema = {
  type: 'object',
  required: ['id', 'service_description_id', 'client_id', 'service_code', 'timeout', 'url'],
  properties: {
    id: { type: 'string' },
    service_description_id: { type: 'string' },
    client_id: { type: 'string' },
    service_code: { type: 'string' },
    timeout: { type: 'number' },
    url: { type: 'string' },
    endpoints: { type: 'array', items: endpointSchema },
  },
};

// ── Fixtures ──────────────────────────────────────────────────────────────────

const SERVICE_ID = 'DEV:COM:1234:test-service:s4c2';
const SERVICE_DESCRIPTION_ID = 'sd-openapi3-1';

const serviceFixture: Service = {
  id: SERVICE_ID,
  service_description_id: SERVICE_DESCRIPTION_ID,
  client_id: 'DEV:COM:1234:test-service',
  service_code: 's4c2',
  timeout: 60,
  url: 'http://mock-server:1080/test-services/testopenapi2.json',
  endpoints: [
    {
      id: 'ep-generated-1',
      service_code: 's4c2',
      method: 'PUT',
      path: '/pet',
      generated: true,
    },
    {
      id: 'ep-manual-1',
      service_code: 's4c2',
      method: 'PATCH',
      path: '/new/path/',
      generated: false,
    },
  ],
};

const serviceDescriptionFixture: ServiceDescription = {
  id: SERVICE_DESCRIPTION_ID,
  url: 'http://mock-server:1080/test-services/testopenapi2.json',
  type: ServiceType.OPENAPI3,
  disabled: false,
  disabled_notice: '',
  refreshed_at: '2024-01-01T00:00:00.000Z',
  services: [serviceFixture],
  client_id: 'DEV:COM:1234:test-service',
};

validateBody(endpointSchema, serviceFixture.endpoints![0]);
validateBody(endpointSchema, serviceFixture.endpoints![1]);
validateBody(serviceSchema, serviceFixture);
validateBody(serviceDescriptionSchema, serviceDescriptionFixture);

// ── Permissions ───────────────────────────────────────────────────────────────

const endpointsPermissions = [
  Permissions.VIEW_CLIENTS,
  Permissions.VIEW_CLIENT_SERVICES,
  Permissions.VIEW_CLIENT_DETAILS,
  Permissions.ADD_OPENAPI3_ENDPOINT,
  Permissions.EDIT_OPENAPI3_ENDPOINT,
  Permissions.VIEW_ENDPOINT_ACL,
];

// ── Specs ─────────────────────────────────────────────────────────────────────

describe('OpenAPI Services — endpoint editability gating: generated vs manual (Browser Mode)', () => {
  it('generated endpoint has no edit button; manual endpoint has edit button', async () => {
    const encodedServiceId = encodeURIComponent(SERVICE_ID);

    const serviceHandler = specHttp.get('/services/{id}', ({ response }) => response(200).json(serviceFixture));
    const serviceDescriptionHandler = specHttp.get('/service-descriptions/{id}', ({ response }) =>
      response(200).json(serviceDescriptionFixture),
    );

    await renderRoute(`/service/${encodedServiceId}/endpoints`, {
      permissions: endpointsPermissions,
      msw: [serviceHandler, serviceDescriptionHandler],
    });

    await expect.element(page.getByRole('row').nth(1)).toBeVisible();

    const rows = page.getByRole('row');

    const generatedRow = rows.filter({ hasText: 'PUT' }).filter({ hasText: '/pet' });
    await expect.element(generatedRow).toBeVisible();
    expect(generatedRow.getByTestId('endpoint-edit').query()).toBeNull();

    const manualRow = rows.filter({ hasText: 'PATCH' }).filter({ hasText: '/new/path/' });
    await expect.element(manualRow).toBeVisible();
    await expect.element(manualRow.getByTestId('endpoint-edit')).toBeVisible();
  });
});
