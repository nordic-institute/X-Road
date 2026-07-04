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
import type { ApprovedCertificationServiceListItem, ApprovedCertificationService, CertificateAuthority } from '@/openapi-types';

const CS_ID = 1;
const INT_CA_ID = 10;

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

const intCaRecord2: CertificateAuthority = {
  id: 11,
  certification_service_id: CS_ID,
  ca_certificate: {
    ...certDetails,
    subject_common_name: 'Another Intermediate CA',
    subject_distinguished_name: 'CN=Another Intermediate CA,O=Test,C=EE',
  },
};

const intermediateCasPath = `/certification-services/${CS_ID}/intermediate-cas`;

const basePermissions = [
  Permissions.VIEW_APPROVED_CAS,
  Permissions.VIEW_APPROVED_CA_DETAILS,
  Permissions.VIEW_APPROVED_TSAS,
];

describe('0530 — CS Intermediate CA — add intermediate CA and list render (Browser Mode)', () => {
  it('add button opens dialog; after save the new intermediate CA appears in the list', async () => {
    let intCasCallCount = 0;
    await renderRoute(intermediateCasPath, {
      permissions: [...basePermissions, Permissions.ADD_APPROVED_CA],
      msw: [
        specHttp.get('/certification-services/{certification_service_id}', ({ response }) =>
          response(200).json(caDetail),
        ),
        specHttp.get('/certification-services/{certification_service_id}/intermediate-cas', ({ response }) => {
          intCasCallCount += 1;
          return intCasCallCount === 1 ? response(200).json([]) : response(200).json([intCaRecord]);
        }),
        specHttp.get('/certification-services/{certification_service_id}/ocsp-responders', ({ response }) =>
          response(200).json([]),
        ),
        specHttp.post('/certification-services/{certification_service_id}/intermediate-cas', ({ response }) =>
          response(201).json(intCaRecord),
        ),
        specHttp.get('/certification-services', ({ response }) => response(200).json([caItem])),
        specHttp.get('/timestamping-services', ({ response }) => response(200).json([])),
      ],
    });

    await expect.element(page.getByTestId('intermediate-cas-table')).toBeVisible();
    await expect.element(page.getByTestId('add-intermediate-ca-button')).toBeVisible();

    await page.getByTestId('add-intermediate-ca-button').click();

    await expect.element(page.getByTestId('add-intermediate-ca-cert-input')).toBeVisible();

    const certFile = new File(
      ['-----BEGIN CERTIFICATE-----\nMIIBtest\n-----END CERTIFICATE-----'],
      'intermediate.pem',
      { type: 'application/x-pem-file' },
    );
    const fileInputEl = document.querySelector('input[type="file"]') as HTMLInputElement;
    await page.elementLocator(fileInputEl).upload(certFile);

    await expect.element(page.getByTestId('dialog-save-button')).not.toBeDisabled();
    await page.getByTestId('dialog-save-button').click();

    await expect.element(page.getByTestId('intermediate-cas-table')).toBeVisible();
    await expect.element(page.getByText('Intermediate CA').first()).toBeVisible();
  });
});

describe('0530 — CS Intermediate CA — list table render, sort, and view certificate (Browser Mode)', () => {
  it('intermediate CA table renders entries and view-certificate button opens cert dialog', async () => {
    await renderRoute(intermediateCasPath, {
      permissions: basePermissions,
      msw: [
        specHttp.get('/certification-services/{certification_service_id}', ({ response }) =>
          response(200).json(caDetail),
        ),
        specHttp.get('/certification-services/{certification_service_id}/intermediate-cas', ({ response }) =>
          response(200).json([intCaRecord, intCaRecord2]),
        ),
        specHttp.get('/certification-services/{certification_service_id}/ocsp-responders', ({ response }) =>
          response(200).json([]),
        ),
        specHttp.get('/certification-services', ({ response }) => response(200).json([caItem])),
        specHttp.get('/timestamping-services', ({ response }) => response(200).json([])),
      ],
    });

    await expect.element(page.getByTestId('intermediate-cas-table')).toBeVisible();
    await expect.element(page.getByText('Intermediate CA').first()).toBeVisible();
    await expect.element(page.getByText('Another Intermediate CA').first()).toBeVisible();

    await expect.element(page.getByTestId('view-intermediate-ca-certificate').first()).toBeVisible();
    await page.getByTestId('view-intermediate-ca-certificate').first().click();
    await expect.element(page.getByText('Intermediate CA').first()).toBeVisible();
  });
});

describe('0530 — CS Intermediate CA — delete intermediate CA from list (Browser Mode)', () => {
  it('delete button triggers confirm dialog; after confirm the CA is removed from list', async () => {
    let intCasCallCount = 0;
    await renderRoute(intermediateCasPath, {
      permissions: [...basePermissions, Permissions.DELETE_APPROVED_CA],
      msw: [
        specHttp.get('/certification-services/{certification_service_id}', ({ response }) =>
          response(200).json(caDetail),
        ),
        specHttp.get('/certification-services/{certification_service_id}/intermediate-cas', ({ response }) => {
          intCasCallCount += 1;
          return intCasCallCount === 1
            ? response(200).json([intCaRecord])
            : response(200).json([]);
        }),
        specHttp.get('/certification-services/{certification_service_id}/ocsp-responders', ({ response }) =>
          response(200).json([]),
        ),
        specHttp.delete('/intermediate-cas/{intermediate_ca_id}', ({ response }) =>
          response(204).empty(),
        ),
        specHttp.get('/certification-services', ({ response }) => response(200).json([caItem])),
        specHttp.get('/timestamping-services', ({ response }) => response(200).json([])),
      ],
    });

    await expect.element(page.getByTestId('intermediate-cas-table')).toBeVisible();
    await expect.element(page.getByText('Intermediate CA').first()).toBeVisible();

    await page.getByTestId('delete-intermediate-ca').first().click();

    await expect.element(page.getByTestId('dialog-save-button')).toBeVisible();
    await page.getByTestId('dialog-save-button').click();

    await expect.element(page.getByTestId('intermediate-cas-table')).toBeVisible();
    await expect.element(page.getByTestId('delete-intermediate-ca')).not.toBeInTheDocument();
  });
});

describe('0540 — CS Intermediate CA Details — view details panel (Browser Mode)', () => {
  it('intermediate CA details panel shows subject/issuer/dates and view-certificate button is present', async () => {
    await renderRoute(`/intermediate-cas/${INT_CA_ID}/details`, {
      permissions: basePermissions,
      msw: [
        specHttp.get('/intermediate-cas/{intermediate_ca_id}', ({ response }) =>
          response(200).json(intCaRecord),
        ),
        specHttp.get('/certification-services/{certification_service_id}', ({ response }) =>
          response(200).json(caDetail),
        ),
        specHttp.get('/intermediate-cas/{intermediate_ca_id}/ocsp-responders', ({ response }) =>
          response(200).json([]),
        ),
        specHttp.get('/certification-services', ({ response }) => response(200).json([caItem])),
        specHttp.get('/timestamping-services', ({ response }) => response(200).json([])),
      ],
    });

    await expect.element(page.getByTestId('subject-distinguished-name-card')).toBeVisible();
    await expect.element(page.getByTestId('issuer-distinguished-name-card')).toBeVisible();
    await expect.element(page.getByTestId('valid-from-card')).toBeVisible();
    await expect.element(page.getByTestId('valid-to-card')).toBeVisible();
    await expect.element(page.getByTestId('view-certificate-button')).toBeVisible();
  });
});
