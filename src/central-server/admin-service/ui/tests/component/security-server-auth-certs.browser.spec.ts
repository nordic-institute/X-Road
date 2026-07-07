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
import type { SecurityServer, SecurityServerAuthenticationCertificateDetails } from '@/openapi-types';

const SERVER_ID = 'CS:E2E-TC1:e2e-tc1-member-subsystem:E2E-SS1';
const SERVER_CODE = 'E2E-SS1';
const CERT_ID_1 = 1;
const CERT_ID_2 = 2;

const serverFixture: SecurityServer = {
  server_id: {
    instance_id: 'CS',
    member_class: 'E2E-TC1',
    member_code: 'e2e-tc1-member-subsystem',
    server_code: SERVER_CODE,
    type: 'SERVER',
    encoded_id: SERVER_ID,
  },
  owner_name: 'E2E TC1 Member with Subsystems',
  server_address: 'security-server-address-E2E-SS1',
  created_at: '2024-01-15T10:00:00Z',
  in_maintenance_mode: false,
};

const certFixture1: SecurityServerAuthenticationCertificateDetails = {
  id: CERT_ID_1,
  version: 3,
  issuer_common_name: 'Subject-E2e-test CA',
  issuer_distinguished_name: 'CN=Subject-E2e-test CA,O=Test,C=EE',
  subject_common_name: 'CN=Subject-E2E-SS1',
  subject_distinguished_name: 'CN=Subject-E2E-SS1,O=Test,C=EE',
  serial: '1',
  signature: 'abc123',
  signature_algorithm: 'SHA256withRSA',
  public_key_algorithm: 'RSA',
  rsa_public_key_exponent: 65537,
  rsa_public_key_modulus: 'abc',
  hash: 'deadbeef1',
  not_before: '2024-01-01T00:00:00Z',
  not_after: '2026-01-01T00:00:00Z',
  key_usages: [],
  subject_alternative_names: '',
};

const certFixture2: SecurityServerAuthenticationCertificateDetails = {
  id: CERT_ID_2,
  version: 3,
  issuer_common_name: 'Subject-E2e-test2 CA',
  issuer_distinguished_name: 'CN=Subject-E2e-test2 CA,O=Test,C=EE',
  subject_common_name: 'CN=Subject-2-E2E-SS1',
  subject_distinguished_name: 'CN=Subject-2-E2E-SS1,O=Test,C=EE',
  serial: '2',
  signature: 'abc123',
  signature_algorithm: 'SHA256withRSA',
  public_key_algorithm: 'RSA',
  rsa_public_key_exponent: 65537,
  rsa_public_key_modulus: 'abc',
  hash: 'deadbeef2',
  not_before: '2024-01-01T00:00:00Z',
  not_after: '2027-01-01T00:00:00Z',
  key_usages: [],
  subject_alternative_names: '',
};

const authCertsPermissions = [
  Permissions.VIEW_SECURITY_SERVERS,
  Permissions.VIEW_SECURITY_SERVER_DETAILS,
];

const deletePermissions = [
  ...authCertsPermissions,
  Permissions.DELETE_SECURITY_SERVER_AUTH_CERT,
];

async function renderAuthCertsRoute(certs = [certFixture1, certFixture2]) {
  return renderRoute(`/security-servers/${encodeURIComponent(SERVER_ID)}/authentication-certificates`, {
    permissions: authCertsPermissions,
    msw: [
      specHttp.get('/security-servers/{server_id}', ({ response }) => response(200).json(serverFixture as never)),
      specHttp.get('/security-servers/{server_id}/authentication-certificates', ({ response }) =>
        response(200).json(certs as never),
      ),
    ],
  });
}

describe('1020 — CS Security Server Auth Certs — cert list render and view-cert navigation (Browser Mode)', () => {
  it('renders rows for both auth certs and clicking an issuer navigates to cert detail page', async () => {
    await renderRoute(`/security-servers/${encodeURIComponent(SERVER_ID)}/authentication-certificates`, {
      permissions: authCertsPermissions,
      msw: [
        specHttp.get('/security-servers/{server_id}', ({ response }) => response(200).json(serverFixture as never)),
        specHttp.get('/security-servers/{server_id}/authentication-certificates', ({ response }) =>
          response(200).json([certFixture1, certFixture2] as never),
        ),
      ],
    });

    await expect.element(page.getByTestId('security-server-authentication-certificates-view')).toBeVisible();
    await expect.element(page.getByText('Subject-E2e-test CA').first()).toBeVisible();
    await expect.element(page.getByText('Subject-E2e-test2 CA').first()).toBeVisible();
    await expect.element(page.getByText('CN=Subject-E2E-SS1').first()).toBeVisible();
    await expect.element(page.getByText('CN=Subject-2-E2E-SS1').first()).toBeVisible();

    await page.getByText('Subject-E2e-test CA').first().click();

    await expect.element(page.getByTestId('security-server-authentication-certificates-view')).not.toBeInTheDocument();
  });
});

describe('1020 — CS Security Server Auth Certs — cert list sortable by columns (Browser Mode)', () => {
  it('clicking column headers renders certs in sorted order', async () => {
    await renderAuthCertsRoute();

    await expect.element(page.getByTestId('security-server-authentication-certificates-view')).toBeVisible();

    const caHeader = page.getByText('Certification Authority').first();
    await expect.element(caHeader).toBeVisible();
    await caHeader.click();

    await expect.element(page.getByText('Subject-E2e-test CA').first()).toBeVisible();

    const serialHeader = page.getByText('Serial Number').first();
    await expect.element(serialHeader).toBeVisible();
    await serialHeader.click();

    await expect.element(page.getByText('1').first()).toBeVisible();
  });
});

describe('1020 — CS Security Server Auth Certs — delete dialog confirm-code gating and row count drop (Browser Mode)', () => {
  it('save disabled with no/wrong code, enabled with exact server code, row count drops after delete', async () => {
    let certsCallCount = 0;
    await renderRoute(`/security-servers/${encodeURIComponent(SERVER_ID)}/authentication-certificates`, {
      permissions: deletePermissions,
      msw: [
        specHttp.get('/security-servers/{server_id}', ({ response }) => response(200).json(serverFixture as never)),
        specHttp.get('/security-servers/{server_id}/authentication-certificates', ({ response }) => {
          certsCallCount += 1;
          return certsCallCount === 1
            ? response(200).json([certFixture1, certFixture2] as never)
            : response(200).json([certFixture2] as never);
        }),
        specHttp.delete('/security-servers/{server_id}/authentication-certificates/{certificate_id}', ({ response }) =>
          response(204).empty(),
        ),
      ],
    });

    await expect.element(page.getByTestId('security-server-authentication-certificates-view')).toBeVisible();
    await expect.element(page.getByText('Subject-E2e-test CA').first()).toBeVisible();
    await expect.element(page.getByText('Subject-E2e-test2 CA').first()).toBeVisible();

    await page.getByTestId('delete-AC-button').first().click();

    const codeInput = page.getByTestId('verify-server-code').getByRole('textbox');
    await expect.element(page.getByTestId('dialog-save-button')).toBeDisabled();

    await codeInput.fill('incorrect-code');
    await expect.element(page.getByTestId('dialog-save-button')).toBeDisabled();

    await codeInput.fill(SERVER_CODE);
    await expect.element(page.getByTestId('dialog-save-button')).not.toBeDisabled();

    submitDialogForm();

    await expect.element(page.getByText('Subject-E2e-test CA')).not.toBeInTheDocument();
    await expect.element(page.getByText('Subject-E2e-test2 CA').first()).toBeVisible();
  });
});
