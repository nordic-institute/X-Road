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
  CertificateAuthority,
  OcspResponder,
} from '@/openapi-types';

const CS_ID = 1;
const INT_CA_ID = 10;
const OCSP_ID = 100;

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
  subject_common_name: 'Intermediate CA',
  subject_distinguished_name: 'CN=Intermediate CA,O=Test,C=EE',
  serial: '2',
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

const intCaRecord: CertificateAuthority = {
  id: INT_CA_ID,
  certification_service_id: CS_ID,
  ca_certificate: certDetails,
};

const ocspResponder = {
  id: OCSP_ID,
  url: 'http://ocsp.example.com',
  cost_type: 'FREE' as OcspResponder['cost_type'],
  has_certificate: true,
};

const ocspResponder2 = {
  id: 101,
  url: 'http://ocsp2.example.com',
  cost_type: 'PAID' as OcspResponder['cost_type'],
  has_certificate: false,
};

const intCaOcspPath = `/intermediate-cas/${INT_CA_ID}/ocsp-responders`;

const basePermissions = [
  Permissions.VIEW_APPROVED_CAS,
  Permissions.VIEW_APPROVED_CA_DETAILS,
  Permissions.VIEW_APPROVED_TSAS,
];

describe('0550 — CS Intermediate CA OCSP — add OCSP responder and list render (Browser Mode)', () => {
  it('add button opens dialog; after save the new OCSP responder appears in the list', async () => {
    let ocspCallCount = 0;
    await renderRoute(intCaOcspPath, {
      permissions: [...basePermissions, Permissions.ADD_APPROVED_CA],
      msw: [
        specHttp.get('/intermediate-cas/{intermediate_ca_id}', ({ response }) =>
          response(200).json(intCaRecord),
        ),
        specHttp.get('/certification-services/{certification_service_id}', ({ response }) =>
          response(200).json(caDetail),
        ),
        specHttp.get('/intermediate-cas/{intermediate_ca_id}/ocsp-responders', ({ response }) => {
          ocspCallCount += 1;
          return ocspCallCount === 1 ? response(200).json([]) : response(200).json([ocspResponder]);
        }),
        specHttp.post('/intermediate-cas/{intermediate_ca_id}/ocsp-responders', ({ response }) =>
          response(201).json(ocspResponder),
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

    await page.getByTestId('ocsp-responder-url-input').getByRole('textbox').fill('http://ocsp.example.com');
    await page.getByTestId('ocsp-responder-cost-type-radio-FREE').getByRole('radio').click();

    await expect.element(page.getByTestId('dialog-save-button')).not.toBeDisabled();
    submitDialogForm();

    await expect.element(page.getByText('http://ocsp.example.com').first()).toBeVisible();
  });
});

describe('0550 — CS Intermediate CA OCSP — list table render, sort by URL, view cert (Browser Mode)', () => {
  it('OCSP table renders entries; view-cert button present for responder with certificate', async () => {
    await renderRoute(intCaOcspPath, {
      permissions: basePermissions,
      msw: [
        specHttp.get('/intermediate-cas/{intermediate_ca_id}', ({ response }) =>
          response(200).json(intCaRecord),
        ),
        specHttp.get('/certification-services/{certification_service_id}', ({ response }) =>
          response(200).json(caDetail),
        ),
        specHttp.get('/intermediate-cas/{intermediate_ca_id}/ocsp-responders', ({ response }) =>
          response(200).json([ocspResponder, ocspResponder2]),
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
    await expect.element(page.getByText('http://ocsp2.example.com').first()).toBeVisible();

    // First responder has certificate — button visible
    await expect.element(page.getByTestId('view-ocsp-responder-certificate').first()).toBeVisible();
    // Second responder has no certificate — button not present
    expect(page.getByTestId('view-ocsp-responder-certificate').nth(1).query()).toBeNull();
  });
});

describe('0550 — CS Intermediate CA OCSP — edit OCSP responder dialog render (Browser Mode)', () => {
  it('edit button opens dialog with URL, cost-type, and cert-change fields; save updates the list', async () => {
    const updatedOcsp = { ...ocspResponder, url: 'http://ocsp-updated.example.com', cost_type: 'PAID' as OcspResponder['cost_type'] };
    let ocspCallCount = 0;
    await renderRoute(intCaOcspPath, {
      permissions: [...basePermissions, Permissions.EDIT_APPROVED_CA],
      msw: [
        specHttp.get('/intermediate-cas/{intermediate_ca_id}', ({ response }) =>
          response(200).json(intCaRecord),
        ),
        specHttp.get('/certification-services/{certification_service_id}', ({ response }) =>
          response(200).json(caDetail),
        ),
        specHttp.get('/intermediate-cas/{intermediate_ca_id}/ocsp-responders', ({ response }) => {
          ocspCallCount += 1;
          return ocspCallCount === 1 ? response(200).json([ocspResponder]) : response(200).json([updatedOcsp]);
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

describe('0550 — CS Intermediate CA OCSP — delete OCSP responder from list (Browser Mode)', () => {
  it('delete button triggers confirm dialog; after confirm the responder is removed from the list', async () => {
    let ocspCallCount = 0;
    await renderRoute(intCaOcspPath, {
      permissions: [...basePermissions, Permissions.DELETE_APPROVED_CA],
      msw: [
        specHttp.get('/intermediate-cas/{intermediate_ca_id}', ({ response }) =>
          response(200).json(intCaRecord),
        ),
        specHttp.get('/certification-services/{certification_service_id}', ({ response }) =>
          response(200).json(caDetail),
        ),
        specHttp.get('/intermediate-cas/{intermediate_ca_id}/ocsp-responders', ({ response }) => {
          ocspCallCount += 1;
          return ocspCallCount === 1 ? response(200).json([ocspResponder]) : response(200).json([]);
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
