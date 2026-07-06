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

import { HttpResponse } from 'msw';
import { specHttp, validateBody } from './spec-http';
import { clientSchema, serviceDescriptionSchema } from './schemas';
import type { Client, ServiceDescription } from '@/openapi-types';

import clientsGolden from './golden/clients.json';
import serviceDescriptionCreatedGolden from './golden/service-description-created.json';

// ── JSON Schema definitions (inlined from OpenAPI components/schemas) ────────

const serviceSchema = {
  type: 'object',
  required: ['id', 'service_code', 'url', 'timeout', 'service_description_id', 'client_id'],
  properties: {
    id: { type: 'string' },
    service_description_id: { type: 'string' },
    client_id: { type: 'string' },
    service_code: { type: 'string' },
    full_service_code: { type: 'string' },
    timeout: { type: 'integer' },
    ssl_auth: { type: 'boolean' },
    url: { type: 'string' },
  },
};

const tokenCertificateSchema = {
  type: 'object',
  required: ['ocsp_status', 'owner_id', 'active', 'saved_to_configuration', 'certificate_details', 'status'],
  properties: {
    ocsp_status: { type: 'string' },
    owner_id: { type: 'string' },
    active: { type: 'boolean' },
    saved_to_configuration: { type: 'boolean' },
    certificate_details: { type: 'object' },
    status: { type: 'string' },
  },
};

const alertsResponseSchema = {
  type: 'object',
  required: ['global_conf_valid', 'soft_token_pin_entered', 'current_time', 'certificate_renewal_job_success', 'auth_certificate_ids_with_errors', 'sign_certificate_ids_with_errors'],
  properties: {
    current_time: { type: 'string' },
    backup_restore_running_since: { type: 'string', nullable: true },
    global_conf_valid: { type: 'boolean' },
    soft_token_pin_entered: { type: 'boolean' },
    certificate_renewal_job_success: { type: 'boolean' },
    auth_certificate_ids_with_errors: { type: 'array', items: { type: 'string' } },
    sign_certificate_ids_with_errors: { type: 'array', items: { type: 'string' } },
  },
};

const sessionStatusSchema = {
  type: 'object',
  required: ['valid'],
  properties: {
    valid: { type: 'boolean' },
  },
};

// ── Golden fixture values ─────────────────────────────────────────────────────

export const clientsFixture = clientsGolden as Client[];
export const subsystemClientFixture = clientsGolden[1] as Client;
export const createdServiceDescriptionFixture = serviceDescriptionCreatedGolden as ServiceDescription;
export const serviceDescriptionFixture = serviceDescriptionCreatedGolden as ServiceDescription;

// ── Eager fixture validation (runs at module load time) ───────────────────────

validateBody({ type: 'array', items: clientSchema }, clientsFixture);
validateBody(serviceDescriptionSchema, createdServiceDescriptionFixture);
validateBody({ type: 'array', items: tokenCertificateSchema }, []);

const _alertsBody = {
  current_time: new Date().toISOString(),
  global_conf_valid: true,
  soft_token_pin_entered: true,
  certificate_renewal_job_success: true,
  auth_certificate_ids_with_errors: [],
  sign_certificate_ids_with_errors: [],
};
validateBody(alertsResponseSchema, _alertsBody);
validateBody(sessionStatusSchema, { valid: true });

// ── Spec-bound handlers ───────────────────────────────────────────────────────

export const handlers = [
  specHttp.get('/clients', ({ response }) => {
    return response(200).json(clientsFixture);
  }),

  specHttp.get('/clients/{id}', ({ params, response }) => {
    const id = decodeURIComponent(params.id);
    const client = clientsFixture.find((c) => c.id === id) ?? subsystemClientFixture;
    return response(200).json(client);
  }),

  specHttp.get('/clients/{id}/sign-certificates', ({ response }) => {
    return response(200).json([]);
  }),

  specHttp.get('/clients/{id}/service-descriptions', ({ response }) => {
    return response(200).json([]);
  }),

  specHttp.post('/clients/{id}/service-descriptions', ({ response }) => {
    return response(201).json(createdServiceDescriptionFixture);
  }),

  specHttp.untyped.get('/api/v1/notifications/session-status', () => {
    return HttpResponse.json({ valid: true });
  }),

  specHttp.untyped.get('/api/v1/notifications/alerts', () => {
    return HttpResponse.json(_alertsBody);
  }),
];
