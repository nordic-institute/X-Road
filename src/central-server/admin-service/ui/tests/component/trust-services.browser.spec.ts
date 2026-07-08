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
import type { ApprovedCertificationServiceListItem, ApprovedCertificationService } from '@/openapi-types';
import type { RequestHandler } from 'msw';

const TRUST_SERVICES_PATH = '/trust-services';

const basePermissions = [
  Permissions.VIEW_APPROVED_CAS,
  Permissions.VIEW_APPROVED_TSAS,
];

const caItem: ApprovedCertificationServiceListItem = {
  id: 1,
  name: 'Test CA',
  not_before: '2024-01-01T00:00:00Z',
  not_after: '2026-01-01T00:00:00Z',
};

const caDetail: ApprovedCertificationService = {
  id: 1,
  name: 'Test CA',
  subject_distinguished_name: 'CN=Test CA,O=Test,C=EE',
  issuer_distinguished_name: 'CN=Root CA,O=Root,C=EE',
  not_before: '2024-01-01T00:00:00Z',
  not_after: '2026-01-01T00:00:00Z',
  certificate_profile_info: 'ee.ria.xroad.common.certificateprofile.impl.FiVRKCertificateProfileInfoProvider',
  tls_auth: false,
  default_csr_format: 'DER',
};

const caDetailWithAcme: ApprovedCertificationService = {
  ...caDetail,
  acme_server_directory_url: 'https://acme.example.com/directory',
  acme_server_ip_address: '192.0.2.1',
  authentication_certificate_profile_id: 'auth-profile',
  signing_certificate_profile_id: 'sign-profile',
};

async function renderTrustServices(
  permissions: string[],
  extraHandlers: RequestHandler[] = [],
) {
  return renderRoute(TRUST_SERVICES_PATH, {
    permissions,
    msw: [
      specHttp.get('/certification-services', ({ response }) => response(200).json([])),
      specHttp.get('/timestamping-services', ({ response }) => response(200).json([])),
      ...extraHandlers,
    ],
  });
}

describe('0500 — CS Trust Services — add certification service dialog render and list row (Browser Mode)', () => {
  it('add button opens dialog; after save the new CA appears in the list', async () => {
    let caCallCount = 0;
    await renderRoute(TRUST_SERVICES_PATH, {
      permissions: [...basePermissions, Permissions.ADD_APPROVED_CA],
      msw: [
        specHttp.get('/certification-services', ({ response }) => {
          caCallCount += 1;
          return caCallCount === 1 ? response(200).json([]) : response(200).json([caItem]);
        }),
        specHttp.get('/timestamping-services', ({ response }) => response(200).json([])),
        specHttp.post('/certification-services', ({ response }) => response(201).json(caDetail)),
      ],
    });

    await expect.element(page.getByTestId('certification-services')).toBeVisible();
    await expect.element(page.getByTestId('add-certification-service')).toBeVisible();

    await page.getByTestId('add-certification-service').click();

    await expect.element(page.getByTestId('upload-file-btn')).toBeVisible();

    // Select a cert file to enable the Upload button, then click Upload to reveal step-2 fields
    const certFile = new File(
      ['-----BEGIN CERTIFICATE-----\nMIIBtest\n-----END CERTIFICATE-----'],
      'ca.pem',
      { type: 'application/x-pem-file' },
    );
    const fileInputEl = document.querySelector('input[type="file"]') as HTMLInputElement;
    await page.elementLocator(fileInputEl).upload(certFile);

    await expect.element(page.getByTestId('upload-file-btn')).not.toBeDisabled();
    await page.getByTestId('upload-file-btn').click();

    await expect.element(page.getByTestId('cert-profile-input')).toBeVisible();

    await page.getByTestId('cert-profile-input').getByRole('textbox')
      .fill('ee.ria.xroad.common.certificateprofile.impl.FiVRKCertificateProfileInfoProvider');

    await expect.element(page.getByTestId('dialog-save-button')).not.toBeDisabled();
    submitDialogForm();

    await expect.element(page.getByText('Test CA').first()).toBeVisible();
  });

  it('certification services table sortable columns are rendered', async () => {
    await renderRoute(TRUST_SERVICES_PATH, {
      permissions: [...basePermissions, Permissions.ADD_APPROVED_CA],
      msw: [
        specHttp.get('/certification-services', ({ response }) => response(200).json([caItem])),
        specHttp.get('/timestamping-services', ({ response }) => response(200).json([])),
      ],
    });

    await expect.element(page.getByTestId('certification-services')).toBeVisible();
    await expect.element(page.getByText('Test CA').first()).toBeVisible();
  });
});

describe('0500 — CS Trust Services — view certification service details (Browser Mode)', () => {
  it('details panel shows subject/issuer/dates; view-certificate button opens cert dialog', async () => {
    await renderRoute(`/certification-services/1/details`, {
      permissions: [...basePermissions, Permissions.VIEW_APPROVED_CA_DETAILS],
      msw: [
        specHttp.get('/certification-services/{certification_service_id}', ({ response }) =>
          response(200).json(caDetail),
        ),
        specHttp.get('/certification-services/{certification_service_id}/certificate', ({ response }) =>
          response(200).json({
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
          }),
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
    await page.getByTestId('view-certificate-button').click();
    await expect.element(page.getByText('Test CA').first()).toBeVisible();
  });
});

describe('0500 — CS Trust Services — view and change CA settings (Browser Mode)', () => {
  it('settings fields (TLS-auth, cert-profile, CSR-format) render; edit dialog opens and fields are editable', async () => {
    const updatedCa: ApprovedCertificationService = { ...caDetail, tls_auth: true };

    let caDetailCallCount = 0;
    await renderRoute(`/certification-services/1/settings`, {
      permissions: [...basePermissions, Permissions.VIEW_APPROVED_CA_DETAILS, Permissions.EDIT_APPROVED_CA],
      msw: [
        specHttp.get('/certification-services/{certification_service_id}', ({ response }) => {
          caDetailCallCount += 1;
          return caDetailCallCount === 1 ? response(200).json(caDetail) : response(200).json(updatedCa);
        }),
        specHttp.patch('/certification-services/{certification_service_id}', ({ response }) =>
          response(200).json(updatedCa),
        ),
        specHttp.get('/certification-services', ({ response }) => response(200).json([caItem])),
        specHttp.get('/timestamping-services', ({ response }) => response(200).json([])),
      ],
    });

    await expect.element(page.getByTestId('tls-auth-card')).toBeVisible();
    await expect.element(page.getByTestId('cert-profile-card')).toBeVisible();
    await expect.element(page.getByTestId('default-csr-format-card')).toBeVisible();
    await expect.element(page.getByTestId('edit-ca-btn')).toBeVisible();

    await page.getByTestId('edit-ca-btn').click();

    await expect.element(page.getByTestId('tls-auth-checkbox')).toBeVisible();
    await expect.element(page.getByTestId('cert-profile-input')).toBeVisible();
    await expect.element(page.getByTestId('default-csr-format-select')).toBeVisible();

    await page.getByTestId('tls-auth-checkbox').getByRole('checkbox').click();

    submitDialogForm();

    await expect.element(page.getByTestId('tls-auth-card')).toBeVisible();
  });
});

describe('0500 — CS Trust Services — add ACME certification service (Browser Mode)', () => {
  it('checking ACME toggle reveals ACME fields; ACME CA appears in list after save', async () => {
    const acmeItem: ApprovedCertificationServiceListItem = { ...caItem, id: 2, name: 'ACME Test CA' };
    let caCallCount = 0;
    await renderRoute(TRUST_SERVICES_PATH, {
      permissions: [...basePermissions, Permissions.ADD_APPROVED_CA],
      msw: [
        specHttp.get('/certification-services', ({ response }) => {
          caCallCount += 1;
          return caCallCount === 1 ? response(200).json([]) : response(200).json([acmeItem]);
        }),
        specHttp.get('/timestamping-services', ({ response }) => response(200).json([])),
        specHttp.post('/certification-services', ({ response }) => response(201).json(caDetailWithAcme)),
      ],
    });

    await expect.element(page.getByTestId('add-certification-service')).toBeVisible();
    await page.getByTestId('add-certification-service').click();

    await expect.element(page.getByTestId('upload-file-btn')).toBeVisible();

    const certFile2 = new File(
      ['-----BEGIN CERTIFICATE-----\nMIIBtest\n-----END CERTIFICATE-----'],
      'ca.pem',
      { type: 'application/x-pem-file' },
    );
    const fileInput2 = document.querySelector('input[type="file"]') as HTMLInputElement;
    await page.elementLocator(fileInput2).upload(certFile2);

    await expect.element(page.getByTestId('upload-file-btn')).not.toBeDisabled();
    await page.getByTestId('upload-file-btn').click();

    await expect.element(page.getByTestId('cert-profile-input')).toBeVisible();
    await page.getByTestId('cert-profile-input').getByRole('textbox')
      .fill('ee.ria.xroad.common.certificateprofile.impl.FiVRKCertificateProfileInfoProvider');

    await expect.element(page.getByTestId('acme-checkbox')).toBeVisible();
    await page.getByTestId('acme-checkbox').getByRole('checkbox').click();

    await expect.element(page.getByTestId('acme-server-directory-url-input')).toBeVisible();
    await expect.element(page.getByTestId('acme-server-ip-address-input')).toBeVisible();
    await expect.element(page.getByTestId('auth-cert-profile-id-input')).toBeVisible();
    await expect.element(page.getByTestId('sign-cert-profile-id-input')).toBeVisible();

    await page.getByTestId('acme-server-directory-url-input').getByRole('textbox')
      .fill('https://acme.example.com/directory');

    await expect.element(page.getByTestId('dialog-save-button')).not.toBeDisabled();
    submitDialogForm();

    await expect.element(page.getByText('ACME Test CA').first()).toBeVisible();
  });
});

describe('0500 — CS Trust Services — view and change ACME CA settings (Browser Mode)', () => {
  it('ACME settings card renders with ACME fields; edit dialog shows ACME toggle and fields', async () => {
    const updatedNonAcme: ApprovedCertificationService = {
      ...caDetail,
      acme_server_directory_url: undefined,
      acme_server_ip_address: undefined,
    };

    let caDetailCallCount = 0;
    await renderRoute(`/certification-services/1/settings`, {
      permissions: [...basePermissions, Permissions.VIEW_APPROVED_CA_DETAILS, Permissions.EDIT_APPROVED_CA],
      msw: [
        specHttp.get('/certification-services/{certification_service_id}', ({ response }) => {
          caDetailCallCount += 1;
          return caDetailCallCount === 1
            ? response(200).json(caDetailWithAcme)
            : response(200).json(updatedNonAcme);
        }),
        specHttp.patch('/certification-services/{certification_service_id}', ({ response }) =>
          response(200).json(updatedNonAcme),
        ),
        specHttp.get('/certification-services', ({ response }) => response(200).json([caItem])),
        specHttp.get('/timestamping-services', ({ response }) => response(200).json([])),
      ],
    });

    await expect.element(page.getByTestId('cert-acme-card')).toBeVisible();
    await expect.element(page.getByTestId('acme-server-directory-url')).toBeVisible();
    await expect.element(page.getByTestId('acme-server-ip-address')).toBeVisible();
    await expect.element(page.getByTestId('edit-ca-acme-btn')).toBeVisible();

    await page.getByTestId('edit-ca-acme-btn').click();

    await expect.element(page.getByTestId('acme-checkbox')).toBeVisible();
    await expect.element(page.getByTestId('acme-server-directory-url-input')).toBeVisible();
    await expect.element(page.getByTestId('acme-server-ip-address-input')).toBeVisible();

    // Remove ACME by unchecking the toggle
    await page.getByTestId('acme-checkbox').getByRole('checkbox').click();

    submitDialogForm();

    await expect.element(page.getByTestId('cert-acme-card')).toBeVisible();
  });
});

describe('0500 — CS Trust Services — add ACME: invalid directory URL and invalid IP block save (Browser Mode)', () => {
  it('save button is disabled when ACME directory URL is malformed or IP address is invalid', async () => {
    await renderRoute(TRUST_SERVICES_PATH, {
      permissions: [...basePermissions, Permissions.ADD_APPROVED_CA],
      msw: [
        specHttp.get('/certification-services', ({ response }) => response(200).json([])),
        specHttp.get('/timestamping-services', ({ response }) => response(200).json([])),
      ],
    });

    await expect.element(page.getByTestId('add-certification-service')).toBeVisible();
    await page.getByTestId('add-certification-service').click();

    const certFile = new File(
      ['-----BEGIN CERTIFICATE-----\nMIIBtest\n-----END CERTIFICATE-----'],
      'ca.pem',
      { type: 'application/x-pem-file' },
    );
    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
    await page.elementLocator(fileInput).upload(certFile);
    await page.getByTestId('upload-file-btn').click();

    await expect.element(page.getByTestId('cert-profile-input')).toBeVisible();
    await page.getByTestId('cert-profile-input').getByRole('textbox')
      .fill('ee.ria.xroad.common.certificateprofile.impl.FiVRKCertificateProfileInfoProvider');

    await page.getByTestId('acme-checkbox').getByRole('checkbox').click();

    await expect.element(page.getByTestId('acme-server-directory-url-input')).toBeVisible();
    await expect.element(page.getByTestId('dialog-save-button')).toBeDisabled();

    await page.getByTestId('acme-server-directory-url-input').getByRole('textbox')
      .fill('httpss://new-test-ca/acme');
    await page.getByTestId('acme-server-ip-address-input').getByRole('textbox')
      .fill('198.7.6.X');

    await expect.element(page.getByTestId('dialog-save-button')).toBeDisabled();

    await page.getByTestId('acme-server-directory-url-input').getByRole('textbox')
      .fill('https://test-ca/acme');
    await page.getByTestId('acme-server-ip-address-input').getByRole('textbox')
      .fill('');

    await expect.element(page.getByTestId('dialog-save-button')).not.toBeDisabled();
  });
});

describe('0500 — CS Trust Services — edit ACME: invalid directory URL and invalid IP block save (Browser Mode)', () => {
  it('save button is disabled when ACME directory URL is malformed or IP address is invalid in edit dialog', async () => {
    await renderRoute('/certification-services/1/settings', {
      permissions: [...basePermissions, Permissions.VIEW_APPROVED_CA_DETAILS, Permissions.EDIT_APPROVED_CA],
      msw: [
        specHttp.get('/certification-services/{certification_service_id}', ({ response }) =>
          response(200).json(caDetailWithAcme),
        ),
        specHttp.get('/certification-services', ({ response }) => response(200).json([caItem])),
        specHttp.get('/timestamping-services', ({ response }) => response(200).json([])),
      ],
    });

    await expect.element(page.getByTestId('edit-ca-acme-btn')).toBeVisible();
    await page.getByTestId('edit-ca-acme-btn').click();

    await expect.element(page.getByTestId('acme-server-directory-url-input')).toBeVisible();

    await page.getByTestId('acme-server-directory-url-input').getByRole('textbox')
      .fill('httpss://new-test-ca/acme');
    await page.getByTestId('acme-server-ip-address-input').getByRole('textbox')
      .fill('198.7.6.X');

    await expect.element(page.getByTestId('dialog-save-button')).toBeDisabled();

    await page.getByTestId('acme-server-directory-url-input').getByRole('textbox')
      .fill('https://new-test-ca/acme');
    await page.getByTestId('acme-server-ip-address-input').getByRole('textbox')
      .fill('198.7.6.5');

    await expect.element(page.getByTestId('dialog-save-button')).not.toBeDisabled();
  });
});
