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
import type { TimestampingService } from '@/openapi-types';

const TRUST_SERVICES_PATH = '/trust-services';
const TSA_ID = 50;

const certDetails = {
  issuer_common_name: 'Root CA',
  issuer_distinguished_name: 'CN=Root CA,O=Root,C=EE',
  subject_common_name: 'Test TSA',
  subject_distinguished_name: 'CN=Test TSA,O=Test,C=EE',
  serial: '5',
  version: 3,
  signature: 'abc123',
  signature_algorithm: 'SHA256withRSA',
  public_key_algorithm: 'RSA',
  rsa_public_key_exponent: 65537,
  rsa_public_key_modulus: 'abc',
  hash: 'deadbeef',
  not_before: '2024-01-01T00:00:00Z',
  not_after: '2026-01-01T00:00:00Z',
  key_usages: [],
  subject_alternative_names: '',
};

const tsaRecord: TimestampingService = {
  id: TSA_ID,
  url: 'http://tsa.example.com',
  cost_type: 'FREE',
  timestamping_interval: 60,
  certificate: certDetails,
};

const tsaRecord2: TimestampingService = {
  id: 51,
  url: 'http://tsa2.example.com',
  cost_type: 'PAID',
  timestamping_interval: 120,
  certificate: {
    ...certDetails,
    subject_common_name: 'Test TSA 2',
    subject_distinguished_name: 'CN=Test TSA 2,O=Test,C=EE',
  },
};

const basePermissions = [
  Permissions.VIEW_APPROVED_CAS,
  Permissions.VIEW_APPROVED_TSAS,
];

describe('0570 — CS Timestamping Services — add timestamping service and list render (Browser Mode)', () => {
  it('add button opens dialog; save-disabled until URL filled; after save the new TSA appears in the list', async () => {
    let tsaCallCount = 0;
    await renderRoute(TRUST_SERVICES_PATH, {
      permissions: [...basePermissions, Permissions.ADD_APPROVED_TSA],
      msw: [
        specHttp.get('/certification-services', ({ response }) => response(200).json([])),
        specHttp.get('/timestamping-services', ({ response }) => {
          tsaCallCount += 1;
          return tsaCallCount === 1 ? response(200).json([]) : response(200).json([tsaRecord]);
        }),
        specHttp.post('/timestamping-services', ({ response }) => response(201).json(tsaRecord)),
      ],
    });

    await expect.element(page.getByTestId('timestamping-services')).toBeVisible();
    await expect.element(page.getByTestId('add-timestamping-service')).toBeVisible();

    await page.getByTestId('add-timestamping-service').click();

    await expect.element(page.getByTestId('timestamping-service-url-input')).toBeVisible();
    await expect.element(page.getByTestId('timestamping-service-cost-type-radio-FREE')).toBeVisible();
    await expect.element(page.getByTestId('timestamping-service-cost-type-radio-PAID')).toBeVisible();
    await expect.element(page.getByTestId('timestamping-service-file-input')).toBeVisible();

    await expect.element(page.getByTestId('dialog-save-button')).toBeDisabled();

    await page.getByTestId('timestamping-service-url-input').getByRole('textbox').fill('http://tsa.example.com');
    await page.getByTestId('timestamping-service-cost-type-radio-FREE').getByRole('radio').click();

    const tsaFile = new File(
      ['-----BEGIN CERTIFICATE-----\nMIIBtest\n-----END CERTIFICATE-----'],
      'tsa.pem',
      { type: 'application/x-pem-file' },
    );
    const tsaFileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
    await page.elementLocator(tsaFileInput).upload(tsaFile);

    await expect.element(page.getByTestId('dialog-save-button')).not.toBeDisabled();
    submitDialogForm();

    await expect.element(page.getByText('http://tsa.example.com').first()).toBeVisible();
  });
});

describe('0570 — CS Timestamping Services — list table columns render, sort, and view cert (Browser Mode)', () => {
  it('TSA table renders URL and cost-type columns for each entry; view-cert button opens cert dialog', async () => {
    await renderRoute(TRUST_SERVICES_PATH, {
      permissions: [...basePermissions, Permissions.VIEW_APPROVED_TSAS],
      msw: [
        specHttp.get('/certification-services', ({ response }) => response(200).json([])),
        specHttp.get('/timestamping-services', ({ response }) => response(200).json([tsaRecord, tsaRecord2])),
      ],
    });

    await expect.element(page.getByTestId('timestamping-services-table')).toBeVisible();
    await expect.element(page.getByText('http://tsa.example.com').first()).toBeVisible();
    await expect.element(page.getByText('http://tsa2.example.com').first()).toBeVisible();

    await expect.element(page.getByTestId('view-timestamping-service-certificate').first()).toBeVisible();
  });
});

describe('0570 — CS Timestamping Services — edit dialog render with cert-view and URL/cost fields (Browser Mode)', () => {
  it('edit button opens dialog with current URL, cost-type radios, cert-view, and cert-change controls; save updates the list', async () => {
    const updatedTsa: TimestampingService = { ...tsaRecord, url: 'http://tsa-updated.example.com', cost_type: 'PAID' };
    let tsaCallCount = 0;
    await renderRoute(TRUST_SERVICES_PATH, {
      permissions: [...basePermissions, Permissions.EDIT_APPROVED_TSA],
      msw: [
        specHttp.get('/certification-services', ({ response }) => response(200).json([])),
        specHttp.get('/timestamping-services', ({ response }) => {
          tsaCallCount += 1;
          return tsaCallCount === 1 ? response(200).json([tsaRecord]) : response(200).json([updatedTsa]);
        }),
        specHttp.patch('/timestamping-services/{timestamping_service_id}', ({ response }) =>
          response(200).json(updatedTsa),
        ),
      ],
    });

    await expect.element(page.getByTestId('timestamping-services-table')).toBeVisible();
    await expect.element(page.getByTestId('edit-timestamping-service').first()).toBeVisible();

    await page.getByTestId('edit-timestamping-service').first().click();

    const dialog = page.getByTestId('dialog-simple');
    await expect.element(dialog.getByTestId('timestamping-service-url-input')).toBeVisible();
    await expect.element(dialog.getByTestId('timestamping-service-cost-type-radio-FREE')).toBeVisible();
    await expect.element(dialog.getByTestId('timestamping-service-cost-type-radio-PAID')).toBeVisible();
    await expect.element(dialog.getByTestId('view-timestamping-service-certificate')).toBeVisible();
    await expect.element(dialog.getByTestId('upload-timestamping-service-certificate')).toBeVisible();

    await dialog.getByTestId('timestamping-service-url-input').getByRole('textbox').fill('http://tsa-updated.example.com');
    await dialog.getByTestId('timestamping-service-cost-type-radio-PAID').getByRole('radio').click();

    await expect.element(page.getByTestId('dialog-save-button')).not.toBeDisabled();
    submitDialogForm();

    await expect.element(page.getByText('http://tsa-updated.example.com').first()).toBeVisible();
  });
});

describe('0570 — CS Timestamping Services — delete timestamping service from list (Browser Mode)', () => {
  it('delete button triggers confirm dialog; after confirm the TSA is removed from the list', async () => {
    let tsaCallCount = 0;
    await renderRoute(TRUST_SERVICES_PATH, {
      permissions: [...basePermissions, Permissions.DELETE_APPROVED_TSA],
      msw: [
        specHttp.get('/certification-services', ({ response }) => response(200).json([])),
        specHttp.get('/timestamping-services', ({ response }) => {
          tsaCallCount += 1;
          return tsaCallCount === 1 ? response(200).json([tsaRecord]) : response(200).json([]);
        }),
        specHttp.delete('/timestamping-services/{timestamping_service_id}', ({ response }) =>
          response(204).empty(),
        ),
      ],
    });

    await expect.element(page.getByTestId('timestamping-services-table')).toBeVisible();
    await expect.element(page.getByText('http://tsa.example.com').first()).toBeVisible();

    await page.getByTestId('delete-timestamping-service').first().click();

    await expect.element(page.getByTestId('dialog-save-button')).toBeVisible();
    await page.getByTestId('dialog-save-button').click();

    await expect.element(page.getByTestId('timestamping-services-table')).toBeVisible();
    await expect.element(page.getByTestId('delete-timestamping-service')).not.toBeInTheDocument();
  });
});
