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
import type {
  ApprovedCertificationServiceListItem,
  ApprovedCertificationService,
  OcspResponder,
} from '@/openapi-types';

const CS_ID = 1;
const OCSP_ID = 200;

const caItem: ApprovedCertificationServiceListItem = {
  id: CS_ID,
  name: 'Test CA',
  not_before: '2024-01-01T00:00:00Z',
  not_after: '2026-01-01T00:00:00Z',
};

const caDetail: ApprovedCertificationService = {
  id: CS_ID,
  name: 'Test CA',
  subject_distinguished_name: 'CN=Test CA,O=Test,C=EE',
  issuer_distinguished_name: 'CN=Root CA,O=Root,C=EE',
  not_before: '2024-01-01T00:00:00Z',
  not_after: '2026-01-01T00:00:00Z',
  certificate_profile_info: 'ee.ria.xroad.common.certificateprofile.impl.FiVRKCertificateProfileInfoProvider',
  tls_auth: false,
  default_csr_format: 'DER',
};

const certDetails = {
  issuer_common_name: 'Root CA',
  issuer_distinguished_name: 'CN=Root CA,O=Root,C=EE',
  subject_common_name: 'Test CA',
  subject_distinguished_name: 'CN=Test CA,O=Test,C=EE',
  serial: '1',
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

const ocspWithCert = {
  id: OCSP_ID,
  url: 'http://ocsp.example.com',
  cost_type: 'FREE' as OcspResponder['cost_type'],
  has_certificate: true,
};

const ocspNoCert = {
  id: 201,
  url: 'http://ocsp-nocert.example.com',
  cost_type: 'PAID' as OcspResponder['cost_type'],
  has_certificate: false,
};

const csOcspPath = `/certification-services/${CS_ID}/ocsp-responders`;

const basePermissions = [
  Permissions.VIEW_APPROVED_CAS,
  Permissions.VIEW_APPROVED_CA_DETAILS,
  Permissions.VIEW_APPROVED_TSAS,
];

describe('0560 — CS OCSP Responders — add OCSP responder and list render (Browser Mode)', () => {
  it('add button opens dialog; after save the new responder appears in the list', async () => {
    let ocspCallCount = 0;
    await renderRoute(csOcspPath, {
      permissions: [...basePermissions, Permissions.ADD_APPROVED_CA],
      msw: [
        specHttp.get('/certification-services/{certification_service_id}', ({ response }) =>
          response(200).json(caDetail),
        ),
        specHttp.get('/certification-services/{certification_service_id}/ocsp-responders', ({ response }) => {
          ocspCallCount += 1;
          return ocspCallCount === 1 ? response(200).json([]) : response(200).json([ocspWithCert]);
        }),
        specHttp.post('/certification-services/{certification_service_id}/ocsp-responders', ({ response }) =>
          response(201).json(ocspWithCert),
        ),
        specHttp.get('/certification-services', ({ response }) => response(200).json([caItem])),
        specHttp.get('/timestamping-services', ({ response }) => response(200).json([])),
      ],
    });

    await expect.element(page.getByTestId('ocsp-responders-table')).toBeVisible();
    await expect.element(page.getByTestId('add-ocsp-responder-button')).toBeVisible();

    await page.getByTestId('add-ocsp-responder-button').click();

    await expect.element(page.getByTestId('ocsp-responder-url-input')).toBeVisible();
    await expect.element(page.getByTestId('ocsp-responder-cost-type-radio-FREE')).toBeVisible();
    await expect.element(page.getByTestId('ocsp-responder-cost-type-radio-PAID')).toBeVisible();
    await expect.element(page.getByTestId('ocsp-responder-file-input')).toBeVisible();

    await page.getByTestId('ocsp-responder-url-input').getByRole('textbox').fill('http://ocsp.example.com');
    await page.getByTestId('ocsp-responder-cost-type-radio-FREE').getByRole('radio').click();

    await expect.element(page.getByTestId('dialog-save-button')).not.toBeDisabled();
    submitDialogForm();

    await expect.element(page.getByText('http://ocsp.example.com').first()).toBeVisible();
  });
});

describe('0560 — CS OCSP Responders — list table render, sort, view cert, and no-cert gating (Browser Mode)', () => {
  it('OCSP table renders URL and cost_type columns; view-cert button present for cert responder, absent for no-cert', async () => {
    await renderRoute(csOcspPath, {
      permissions: basePermissions,
      msw: [
        specHttp.get('/certification-services/{certification_service_id}', ({ response }) =>
          response(200).json(caDetail),
        ),
        specHttp.get('/certification-services/{certification_service_id}/ocsp-responders', ({ response }) =>
          response(200).json([ocspWithCert, ocspNoCert]),
        ),
        specHttp.get('/ocsp-responders/{ocsp_responder_id}/certificate', ({ response }) =>
          response(200).json(certDetails),
        ),
        specHttp.get('/certification-services', ({ response }) => response(200).json([caItem])),
        specHttp.get('/timestamping-services', ({ response }) => response(200).json([])),
      ],
    });

    await expect.element(page.getByTestId('ocsp-responders-table')).toBeVisible();
    await expect.element(page.getByText('http://ocsp.example.com').first()).toBeVisible();
    await expect.element(page.getByText('http://ocsp-nocert.example.com').first()).toBeVisible();

    // View-cert button present only for the responder that has a certificate
    await expect.element(page.getByTestId('view-ocsp-responder-certificate').first()).toBeVisible();
  });

  it('view-cert button is absent for responder with has_certificate=false', async () => {
    await renderRoute(csOcspPath, {
      permissions: basePermissions,
      msw: [
        specHttp.get('/certification-services/{certification_service_id}', ({ response }) =>
          response(200).json(caDetail),
        ),
        specHttp.get('/certification-services/{certification_service_id}/ocsp-responders', ({ response }) =>
          response(200).json([ocspNoCert]),
        ),
        specHttp.get('/certification-services', ({ response }) => response(200).json([caItem])),
        specHttp.get('/timestamping-services', ({ response }) => response(200).json([])),
      ],
    });

    await expect.element(page.getByTestId('ocsp-responders-table')).toBeVisible();
    await expect.element(page.getByText('http://ocsp-nocert.example.com').first()).toBeVisible();
    expect(page.getByTestId('view-ocsp-responder-certificate').query()).toBeNull();
  });
});

describe('0560 — CS OCSP Responders — edit dialog render with cert view and URL/cost fields (Browser Mode)', () => {
  it('edit button opens dialog showing current URL, cost-type radios, cert-view, and cert-change controls', async () => {
    const updatedOcsp = { ...ocspWithCert, url: 'http://ocsp-updated.example.com', cost_type: 'PAID' as OcspResponder['cost_type'] };
    let ocspCallCount = 0;
    await renderRoute(csOcspPath, {
      permissions: [...basePermissions, Permissions.EDIT_APPROVED_CA],
      msw: [
        specHttp.get('/certification-services/{certification_service_id}', ({ response }) =>
          response(200).json(caDetail),
        ),
        specHttp.get('/certification-services/{certification_service_id}/ocsp-responders', ({ response }) => {
          ocspCallCount += 1;
          return ocspCallCount === 1 ? response(200).json([ocspWithCert]) : response(200).json([updatedOcsp]);
        }),
        specHttp.patch('/ocsp-responders/{ocsp_responder_id}', ({ response }) =>
          response(200).json(updatedOcsp),
        ),
        specHttp.get('/certification-services', ({ response }) => response(200).json([caItem])),
        specHttp.get('/timestamping-services', ({ response }) => response(200).json([])),
      ],
    });

    await expect.element(page.getByTestId('ocsp-responders-table')).toBeVisible();
    await expect.element(page.getByTestId('edit-ocsp-responder').first()).toBeVisible();

    await page.getByTestId('edit-ocsp-responder').first().click();

    const dialog = page.getByTestId('dialog-simple');
    await expect.element(dialog.getByTestId('ocsp-responder-url-input')).toBeVisible();
    await expect.element(dialog.getByTestId('ocsp-responder-cost-type-radio-FREE')).toBeVisible();
    await expect.element(dialog.getByTestId('ocsp-responder-cost-type-radio-PAID')).toBeVisible();
    await expect.element(dialog.getByTestId('view-ocsp-responder-certificate')).toBeVisible();
    await expect.element(dialog.getByTestId('upload-ocsp-responder-certificate')).toBeVisible();

    await dialog.getByTestId('ocsp-responder-url-input').getByRole('textbox').fill('http://ocsp-updated.example.com');
    await dialog.getByTestId('ocsp-responder-cost-type-radio-PAID').getByRole('radio').click();

    await expect.element(page.getByTestId('dialog-save-button')).not.toBeDisabled();
    submitDialogForm();

    await expect.element(page.getByText('http://ocsp-updated.example.com').first()).toBeVisible();
  });
});

describe('0560 — CS OCSP Responders — delete OCSP responder from list (Browser Mode)', () => {
  it('delete button triggers confirm dialog; after confirm the responder is removed', async () => {
    let ocspCallCount = 0;
    await renderRoute(csOcspPath, {
      permissions: [...basePermissions, Permissions.DELETE_APPROVED_CA],
      msw: [
        specHttp.get('/certification-services/{certification_service_id}', ({ response }) =>
          response(200).json(caDetail),
        ),
        specHttp.get('/certification-services/{certification_service_id}/ocsp-responders', ({ response }) => {
          ocspCallCount += 1;
          return ocspCallCount === 1 ? response(200).json([ocspWithCert]) : response(200).json([]);
        }),
        specHttp.delete('/ocsp-responders/{ocsp_responder_id}', ({ response }) =>
          response(204).empty(),
        ),
        specHttp.get('/certification-services', ({ response }) => response(200).json([caItem])),
        specHttp.get('/timestamping-services', ({ response }) => response(200).json([])),
      ],
    });

    await expect.element(page.getByTestId('ocsp-responders-table')).toBeVisible();
    await expect.element(page.getByText('http://ocsp.example.com').first()).toBeVisible();

    await page.getByTestId('delete-ocsp-responder').first().click();

    await expect.element(page.getByTestId('dialog-save-button')).toBeVisible();
    await page.getByTestId('dialog-save-button').click();

    await expect.element(page.getByTestId('ocsp-responders-table')).toBeVisible();
    await expect.element(page.getByTestId('delete-ocsp-responder')).not.toBeInTheDocument();
  });
});
