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
import { http, HttpResponse } from 'msw';
import { Client, ClientStatus, ConnectionType, Service, ServiceDescription, ServiceType } from '@/openapi-types';

export const clientsFixture: Client[] = [
  {
    id: 'CS:GOV:1234',
    instance_id: 'CS',
    member_name: 'ACME',
    member_class: 'GOV',
    member_code: '1234',
    owner: true,
    has_valid_local_sign_cert: true,
    connection_type: ConnectionType.HTTPS,
    status: ClientStatus.REGISTERED,
  },
  {
    id: 'CS:GOV:1234:SUBS1',
    instance_id: 'CS',
    member_name: 'ACME',
    member_class: 'GOV',
    member_code: '1234',
    subsystem_code: 'SUBS1',
    owner: false,
    has_valid_local_sign_cert: false,
    connection_type: ConnectionType.HTTPS,
    status: ClientStatus.REGISTERED,
  },
  {
    id: 'CS:GOV:5678:BETA',
    instance_id: 'CS',
    member_name: 'BETA',
    member_class: 'GOV',
    member_code: '5678',
    subsystem_code: 'BETA',
    owner: false,
    has_valid_local_sign_cert: false,
    connection_type: ConnectionType.HTTPS,
    status: ClientStatus.SAVED,
  },
  {
    id: 'CS:GOV:9000:ALPHA',
    instance_id: 'CS',
    member_name: 'ALPHA',
    member_class: 'GOV',
    member_code: '9000',
    subsystem_code: 'ALPHA',
    owner: false,
    has_valid_local_sign_cert: false,
    connection_type: ConnectionType.HTTPS,
    status: ClientStatus.REGISTERED,
  },
];

export const subsystemClientFixture: Client = clientsFixture[1];

const createdServiceFixture: Service = {
  id: 'CS:GOV:1234:SUBS1:MY-API',
  service_code: 'MY-API',
  full_service_code: 'MY-API',
  url: 'https://example.com/rest-api',
  timeout: 60,
  ssl_auth: false,
  subjects_count: 0,
};

export const createdServiceDescriptionFixture: ServiceDescription = {
  id: '42',
  url: 'https://example.com/rest-api',
  type: ServiceType.REST,
  disabled: false,
  disabled_notice: '',
  refreshed_at: '2024-01-01T00:00:00Z',
  services: [createdServiceFixture],
  client_id: 'CS:GOV:1234:SUBS1',
};

export const serviceDescriptionFixture: ServiceDescription = createdServiceDescriptionFixture;

export const handlers = [
  http.get('/api/v1/clients', () => {
    return HttpResponse.json(clientsFixture);
  }),

  http.get('/api/v1/clients/:clientId', ({ params }) => {
    const id = decodeURIComponent(params.clientId as string);
    const client = clientsFixture.find((c) => c.id === id) ?? subsystemClientFixture;
    return HttpResponse.json(client);
  }),

  http.get('/api/v1/clients/:clientId/service-descriptions', () => {
    return HttpResponse.json([]);
  }),

  http.post('/api/v1/clients/:clientId/service-descriptions', () => {
    return HttpResponse.json(createdServiceDescriptionFixture, { status: 201 });
  }),

  http.get('/api/v1/notifications/session-status', () => {
    return HttpResponse.json({ valid: true });
  }),

  http.get('/api/v1/notifications/alerts', () => {
    return HttpResponse.json({
      backup_restore_in_progress: false,
      current_time: new Date().toISOString(),
      global_configuration_expired: false,
      soft_token_pin_entered: true,
    });
  }),
];
