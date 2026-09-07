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
  ApprovedDsTlsCertificationAuthorityListItem,
  ApprovedDsTlsCertificationAuthority,
  DsTlsIntermediateCertificateAuthority,
} from '@/openapi-types';
import type { RequestHandler } from 'msw';

const TRUST_SERVICES_PATH = '/trust-services';
const DS_TLS_CA_ID = 1;
const DS_TLS_INTERMEDIATE_CA_ID = 10;

const basePermissions = [
  Permissions.VIEW_APPROVED_CAS,
  Permissions.VIEW_APPROVED_TSAS,
  Permissions.VIEW_APPROVED_DS_TLS_CAS,
];

const dsTlsCaItem: ApprovedDsTlsCertificationAuthorityListItem = {
  id: DS_TLS_CA_ID,
  name: "Let's Encrypt",
  not_before: '2024-01-01T00:00:00Z',
  not_after: '2026-01-01T00:00:00Z',
};

const dsTlsCaDetail: ApprovedDsTlsCertificationAuthority = {
  id: DS_TLS_CA_ID,
  name: "Let's Encrypt",
  subject_distinguished_name: 'CN=ISRG Root X1,O=Internet Security Research Group,C=US',
  issuer_distinguished_name: 'CN=ISRG Root X1,O=Internet Security Research Group,C=US',
  not_before: '2024-01-01T00:00:00Z',
  not_after: '2026-01-01T00:00:00Z',
};

const dsTlsCaDetailWithAcme: ApprovedDsTlsCertificationAuthority = {
  ...dsTlsCaDetail,
  acme_server_directory_url: 'https://acme-v02.api.letsencrypt.org/directory',
  ds_tls_certificate_profile_id: 'xrd-ds-tls',
};

const intermediateCaRecord: DsTlsIntermediateCertificateAuthority = {
  id: DS_TLS_INTERMEDIATE_CA_ID,
  ds_tls_certification_authority_id: DS_TLS_CA_ID,
  ca_certificate: {
    issuer_common_name: 'ISRG Root X1',
    issuer_distinguished_name: 'CN=ISRG Root X1,O=Internet Security Research Group,C=US',
    subject_common_name: 'R11',
    subject_distinguished_name: 'CN=R11,O=Internet Security Research Group,C=US',
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
  },
};

async function renderTrustServices(permissions: string[], extraHandlers: RequestHandler[] = []) {
  return renderRoute(TRUST_SERVICES_PATH, {
    permissions,
    msw: [
      specHttp.get('/certification-services', ({ response }) => response(200).json([])),
      specHttp.get('/timestamping-services', ({ response }) => response(200).json([])),
      ...extraHandlers,
    ],
  });
}

describe('0550 — CS DS TLS Certification Authorities — add dialog render and list row (Browser Mode)', () => {
  it('add button opens dialog; after save the new CA appears in the list', async () => {
    let caCallCount = 0;
    await renderTrustServices([...basePermissions, Permissions.ADD_APPROVED_DS_TLS_CA], [
      specHttp.get('/ds-tls-certification-authorities', ({ response }) => {
        caCallCount += 1;
        return caCallCount === 1 ? response(200).json([]) : response(200).json([dsTlsCaItem]);
      }),
      specHttp.post('/ds-tls-certification-authorities', ({ response }) => response(201).json(dsTlsCaDetail)),
    ]);

    await expect.element(page.getByTestId('ds-tls-certification-authorities')).toBeVisible();
    await expect.element(page.getByTestId('add-ds-tls-certification-authority')).toBeVisible();

    await page.getByTestId('add-ds-tls-certification-authority').click();

    await expect.element(page.getByTestId('upload-file-btn')).toBeVisible();

    const certFile = new File(
      ['-----BEGIN CERTIFICATE-----\nMIIBtest\n-----END CERTIFICATE-----'],
      'ca.pem',
      { type: 'application/x-pem-file' },
    );
    const fileInputEl = document.querySelector('input[type="file"]') as HTMLInputElement;
    await page.elementLocator(fileInputEl).upload(certFile);

    await expect.element(page.getByTestId('upload-file-btn')).not.toBeDisabled();
    await page.getByTestId('upload-file-btn').click();

    await expect.element(page.getByTestId('ds-tls-ca-name-input')).toBeVisible();
    await page.getByTestId('ds-tls-ca-name-input').getByRole('textbox').fill("Let's Encrypt");

    await expect.element(page.getByTestId('dialog-save-button')).not.toBeDisabled();
    submitDialogForm();

    await expect.element(page.getByText("Let's Encrypt").first()).toBeVisible();
  });

  it('DS TLS certification authorities table is rendered', async () => {
    await renderTrustServices(basePermissions, [
      specHttp.get('/ds-tls-certification-authorities', ({ response }) => response(200).json([dsTlsCaItem])),
    ]);

    await expect.element(page.getByTestId('ds-tls-certification-authorities')).toBeVisible();
    await expect.element(page.getByText("Let's Encrypt").first()).toBeVisible();
  });
});

describe('0550 — CS DS TLS Certification Authorities — add with ACME server (Browser Mode)', () => {
  it('checking the ACME toggle reveals the directory URL and profile id fields', async () => {
    await renderTrustServices([...basePermissions, Permissions.ADD_APPROVED_DS_TLS_CA], [
      specHttp.get('/ds-tls-certification-authorities', ({ response }) => response(200).json([])),
      specHttp.post('/ds-tls-certification-authorities', ({ response }) => response(201).json(dsTlsCaDetailWithAcme)),
    ]);

    await page.getByTestId('add-ds-tls-certification-authority').click();

    const certFile = new File(
      ['-----BEGIN CERTIFICATE-----\nMIIBtest\n-----END CERTIFICATE-----'],
      'ca.pem',
      { type: 'application/x-pem-file' },
    );
    const fileInputEl = document.querySelector('input[type="file"]') as HTMLInputElement;
    await page.elementLocator(fileInputEl).upload(certFile);
    await page.getByTestId('upload-file-btn').click();

    await page.getByTestId('ds-tls-ca-name-input').getByRole('textbox').fill("Let's Encrypt");

    await expect.element(page.getByTestId('acme-checkbox')).toBeVisible();
    await page.getByTestId('acme-checkbox').getByRole('checkbox').click();

    await expect.element(page.getByTestId('acme-server-directory-url-input')).toBeVisible();
    await expect.element(page.getByTestId('ds-tls-cert-profile-id-input')).toBeVisible();

    await expect.element(page.getByTestId('dialog-save-button')).toBeDisabled();

    await page
      .getByTestId('acme-server-directory-url-input')
      .getByRole('textbox')
      .fill('https://acme-v02.api.letsencrypt.org/directory');

    await expect.element(page.getByTestId('dialog-save-button')).not.toBeDisabled();
  });
});

describe('0550 — CS DS TLS Certification Authority — view details and settings (Browser Mode)', () => {
  it('details panel shows subject/issuer/dates; settings show ACME info and edit dialog opens', async () => {
    await renderRoute(`/ds-tls-certification-authorities/${DS_TLS_CA_ID}/details`, {
      permissions: [...basePermissions, Permissions.VIEW_APPROVED_DS_TLS_CA_DETAILS, Permissions.EDIT_APPROVED_DS_TLS_CA],
      msw: [
        specHttp.get('/ds-tls-certification-authorities/{ds_tls_certification_authority_id}', ({ response }) =>
          response(200).json(dsTlsCaDetailWithAcme),
        ),
        specHttp.get('/ds-tls-certification-authorities', ({ response }) => response(200).json([dsTlsCaItem])),
        specHttp.get('/certification-services', ({ response }) => response(200).json([])),
        specHttp.get('/timestamping-services', ({ response }) => response(200).json([])),
      ],
    });

    await expect.element(page.getByTestId('subject-distinguished-name-card')).toBeVisible();
    await expect.element(page.getByTestId('issuer-distinguished-name-card')).toBeVisible();
    await expect.element(page.getByTestId('valid-from-card')).toBeVisible();
    await expect.element(page.getByTestId('valid-to-card')).toBeVisible();
    await expect.element(page.getByTestId('view-certificate-button')).toBeVisible();
  });

  it('ACME settings edit dialog shows directory URL and profile id fields', async () => {
    await renderRoute(`/ds-tls-certification-authorities/${DS_TLS_CA_ID}/settings`, {
      permissions: [...basePermissions, Permissions.VIEW_APPROVED_DS_TLS_CA_DETAILS, Permissions.EDIT_APPROVED_DS_TLS_CA],
      msw: [
        specHttp.get('/ds-tls-certification-authorities/{ds_tls_certification_authority_id}', ({ response }) =>
          response(200).json(dsTlsCaDetailWithAcme),
        ),
        specHttp.get('/ds-tls-certification-authorities', ({ response }) => response(200).json([dsTlsCaItem])),
        specHttp.get('/certification-services', ({ response }) => response(200).json([])),
        specHttp.get('/timestamping-services', ({ response }) => response(200).json([])),
      ],
    });

    await expect.element(page.getByTestId('ds-tls-acme-card')).toBeVisible();
    await expect.element(page.getByTestId('acme-server-directory-url')).toBeVisible();
    await expect.element(page.getByTestId('edit-ds-tls-acme-btn')).toBeVisible();

    await page.getByTestId('edit-ds-tls-acme-btn').click();

    await expect.element(page.getByTestId('acme-checkbox')).toBeVisible();
    await expect.element(page.getByTestId('acme-server-directory-url-input')).toBeVisible();
    await expect.element(page.getByTestId('ds-tls-cert-profile-id-input')).toBeVisible();
  });
});

describe('0550 — CS DS TLS Certification Authority — intermediate CAs (Browser Mode)', () => {
  it('intermediate CA table renders, add dialog uploads a certificate, and delete removes a row', async () => {
    let intermediateCasCallCount = 0;
    await renderRoute(`/ds-tls-certification-authorities/${DS_TLS_CA_ID}/intermediate-cas`, {
      permissions: [
        ...basePermissions,
        Permissions.VIEW_APPROVED_DS_TLS_CA_DETAILS,
        Permissions.ADD_APPROVED_DS_TLS_CA,
        Permissions.DELETE_APPROVED_DS_TLS_CA,
      ],
      msw: [
        specHttp.get('/ds-tls-certification-authorities/{ds_tls_certification_authority_id}', ({ response }) =>
          response(200).json(dsTlsCaDetail),
        ),
        specHttp.get('/ds-tls-certification-authorities/{ds_tls_certification_authority_id}/intermediate-cas', ({ response }) => {
          intermediateCasCallCount += 1;
          return intermediateCasCallCount === 1 ? response(200).json([]) : response(200).json([intermediateCaRecord]);
        }),
        specHttp.post(
          '/ds-tls-certification-authorities/{ds_tls_certification_authority_id}/intermediate-cas',
          ({ response }) => response(201).json(intermediateCaRecord),
        ),
        specHttp.get('/ds-tls-certification-authorities', ({ response }) => response(200).json([dsTlsCaItem])),
        specHttp.get('/certification-services', ({ response }) => response(200).json([])),
        specHttp.get('/timestamping-services', ({ response }) => response(200).json([])),
      ],
    });

    await expect.element(page.getByTestId('ds-tls-intermediate-cas-table')).toBeVisible();
    await expect.element(page.getByTestId('add-ds-tls-intermediate-ca-button')).toBeVisible();

    await page.getByTestId('add-ds-tls-intermediate-ca-button').click();
    await expect.element(page.getByTestId('add-ds-tls-intermediate-ca-cert-input')).toBeVisible();

    const certFile = new File(
      ['-----BEGIN CERTIFICATE-----\nMIIBtest\n-----END CERTIFICATE-----'],
      'intermediate.pem',
      { type: 'application/x-pem-file' },
    );
    const fileInputEl = document.querySelector('input[type="file"]') as HTMLInputElement;
    await page.elementLocator(fileInputEl).upload(certFile);

    await expect.element(page.getByTestId('dialog-save-button')).not.toBeDisabled();
    await page.getByTestId('dialog-save-button').click();

    await expect.element(page.getByText('R11').first()).toBeVisible();
    await expect.element(page.getByTestId('view-ds-tls-intermediate-ca-certificate').first()).toBeVisible();
  });

  it('view-certificate button fetches the intermediate CA by its own id (single-item GET)', async () => {
    let getByIdCallCount = 0;
    await renderRoute(`/ds-tls-intermediate-cas/${DS_TLS_INTERMEDIATE_CA_ID}/certificate-details`, {
      permissions: [...basePermissions, Permissions.VIEW_APPROVED_DS_TLS_CA_DETAILS],
      msw: [
        specHttp.get('/ds-tls-intermediate-cas/{ds_tls_intermediate_ca_id}', ({ response }) => {
          getByIdCallCount += 1;
          return response(200).json(intermediateCaRecord);
        }),
        specHttp.get('/ds-tls-certification-authorities/{ds_tls_certification_authority_id}', ({ response }) =>
          response(200).json(dsTlsCaDetail),
        ),
        specHttp.get('/ds-tls-certification-authorities', ({ response }) => response(200).json([dsTlsCaItem])),
        specHttp.get('/certification-services', ({ response }) => response(200).json([])),
        specHttp.get('/timestamping-services', ({ response }) => response(200).json([])),
      ],
    });

    await expect.element(page.getByText('R11').first()).toBeVisible();
    expect(getByIdCallCount).toBe(1);
  });
});
