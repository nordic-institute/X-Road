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
import { Permissions } from '@/global';
import type { CertificateAuthority } from '@/openapi-types';
import { CertificateAuthorityOcspResponse, CsrFormat } from '@/openapi-types';

// ── AJV schemas ───────────────────────────────────────────────────────────────

const certAuthoritySchema = {
  type: 'object',
  required: ['name', 'subject_distinguished_name', 'issuer_distinguished_name', 'ocsp_response', 'not_after', 'top_ca', 'path', 'authentication_only'],
  properties: {
    name: { type: 'string' },
    subject_distinguished_name: { type: 'string' },
    issuer_distinguished_name: { type: 'string' },
    ocsp_response: { type: 'string' },
    not_after: { type: 'string' },
    top_ca: { type: 'boolean' },
    path: { type: 'string' },
    authentication_only: { type: 'boolean' },
    acme_capable: { type: 'boolean' },
  },
};

const acmeEabCredentialsSchema = {
  type: 'object',
  required: ['acme_eab_required', 'has_acme_external_account_credentials'],
  properties: {
    acme_eab_required: { type: 'boolean' },
    has_acme_external_account_credentials: { type: 'boolean' },
  },
};

const csrSubjectFieldSchema = {
  type: 'object',
  required: ['id', 'label_key', 'default_value', 'read_only', 'required', 'localized'],
  properties: {
    id: { type: 'string' },
    label_key: { type: 'string' },
    default_value: { type: 'string' },
    read_only: { type: 'boolean' },
    required: { type: 'boolean' },
    localized: { type: 'boolean' },
  },
};

// ── Fixtures ──────────────────────────────────────────────────────────────────

const acmeCaFixture: CertificateAuthority = {
  name: 'Test CA',
  subject_distinguished_name: 'CN=Test CA',
  issuer_distinguished_name: 'CN=Test CA',
  ocsp_response: CertificateAuthorityOcspResponse.OCSP_RESPONSE_GOOD,
  not_after: '2099-12-15T00:00:00.001Z',
  top_ca: true,
  path: 'CN=Test CA',
  authentication_only: false,
  acme_capable: true,
};

const eabRequiredMissingFixture = {
  acme_eab_required: true,
  has_acme_external_account_credentials: false,
};

const eabRequiredPresentFixture = {
  acme_eab_required: true,
  has_acme_external_account_credentials: true,
};

const csrSubjectFieldsFixture = [
  { id: 'CN', label_key: 'CN', default_value: '', read_only: false, required: true, localized: false },
  { id: 'O', label_key: 'O', default_value: 'ui-test', read_only: false, required: false, localized: false },
];

validateBody(certAuthoritySchema, acmeCaFixture);
validateBody(acmeEabCredentialsSchema, eabRequiredMissingFixture);
validateBody(acmeEabCredentialsSchema, eabRequiredPresentFixture);
validateBody({ type: 'array', items: csrSubjectFieldSchema }, csrSubjectFieldsFixture);

// ── Permissions ───────────────────────────────────────────────────────────────

const keysPermissions = [
  Permissions.VIEW_KEYS,
  Permissions.GENERATE_KEY,
  Permissions.GENERATE_AUTH_CERT_REQ,
  Permissions.GENERATE_SIGN_CERT_REQ,
];

// ── Handlers ──────────────────────────────────────────────────────────────────

const certAuthoritiesHandler = specHttp.get('/certificate-authorities', ({ response }) =>
  response(200).json([acmeCaFixture]),
);

const membersHandler = specHttp.get('/clients', ({ response }) => response(200).json([]));

const csrSubjectFieldsHandler = specHttp.get('/certificate-authorities/{ca_name}/csr-subject-fields', ({ response }) =>
  response(200).json(csrSubjectFieldsFixture),
);

const eabMissingHandler = specHttp.get('/certificate-authorities/{ca_name}/has-acme-eab-credentials', ({ response }) =>
  response(200).json(eabRequiredMissingFixture),
);

const eabPresentHandler = specHttp.get('/certificate-authorities/{ca_name}/has-acme-eab-credentials', ({ response }) =>
  response(200).json(eabRequiredPresentFixture),
);

// ── Helper: drive wizard to step 3 (GenerateCsr page) ─────────────────────────

async function driveWizardToStep3(): Promise<void> {
  await expect.element(page.getByTestId('key-label-input')).toBeVisible();
  await page.getByTestId('key-label-input').getByRole('textbox').fill('auth key for eab');
  await page.getByTestId('next-button').click();

  await expect.element(page.getByTestId('csr-usage-select')).toBeVisible();

  await page.getByTestId('csr-usage-select').click();
  await expect.element(page.getByRole('option', { name: 'AUTHENTICATION' })).toBeVisible();
  await page.getByRole('option', { name: 'AUTHENTICATION' }).click();

  await page.getByTestId('csr-certification-service-select').click();
  await expect.element(page.getByRole('option', { name: 'Test CA' })).toBeVisible();
  await page.getByRole('option', { name: 'Test CA' }).click();

  await page.getByTestId('csr-format-select').click();
  await expect.element(page.getByRole('option', { name: CsrFormat.DER })).toBeVisible();
  await page.getByRole('option', { name: CsrFormat.DER }).click();

  await expect.element(page.getByTestId('save-button')).not.toBeDisabled();
  await page.getByTestId('save-button').click();

  await expect.element(page.getByTestId('generate-csr-button')).toBeVisible();
}

// ── Specs ─────────────────────────────────────────────────────────────────────

describe('ACME — add-key wizard UI slice: ACME section reachable (Browser Mode)', () => {
  it('drives wizard to step 3 with ACME-capable CA, order button section is visible', async () => {
    await renderRoute('/add-key/softToken-0/SOFTWARE', {
      permissions: keysPermissions,
      msw: [certAuthoritiesHandler, membersHandler, csrSubjectFieldsHandler, eabPresentHandler],
    });

    await driveWizardToStep3();

    const orderBtn = page.getByTestId('acme-order-certificate-button');
    await expect.element(orderBtn).toBeVisible();
  });
});

describe('ACME — EAB gating: alert visible when credentials missing (Browser Mode)', () => {
  it('EAB-missing alert is visible and order button is visible when EAB required but missing', async () => {
    await renderRoute('/add-key/softToken-0/SOFTWARE', {
      permissions: keysPermissions,
      msw: [certAuthoritiesHandler, membersHandler, csrSubjectFieldsHandler, eabMissingHandler],
    });

    await driveWizardToStep3();

    const orderBtn = page.getByTestId('acme-order-certificate-button');
    await expect.element(orderBtn).toBeVisible();

    await expect.element(page.getByText('credentials are missing from the configuration', { exact: false })).toBeVisible();
  });

  it('EAB-missing alert is absent when EAB credentials are present', async () => {
    await renderRoute('/add-key/softToken-0/SOFTWARE', {
      permissions: keysPermissions,
      msw: [certAuthoritiesHandler, membersHandler, csrSubjectFieldsHandler, eabPresentHandler],
    });

    await driveWizardToStep3();

    await expect.element(page.getByTestId('acme-order-certificate-button')).toBeVisible();

    await expect.poll(() => page.getByText('credentials are missing from the configuration', { exact: false }).query()).toBeNull();
  });
});
