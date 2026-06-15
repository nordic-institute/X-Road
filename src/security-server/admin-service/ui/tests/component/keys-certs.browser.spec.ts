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
import type { CertificateAuthority, Token } from '@/openapi-types';
import { CertificateAuthorityOcspResponse, CsrFormat, PossibleAction, TokenStatus, TokenType } from '@/openapi-types';
import { useUser } from '@/store/modules/user';
import { useTokens } from '@/store/modules/tokens';

// ── AJV schemas ───────────────────────────────────────────────────────────────

const tokenSchema = {
  type: 'object',
  required: ['id', 'name', 'type', 'keys', 'status', 'logged_in', 'available', 'saved_to_configuration', 'read_only'],
  properties: {
    id: { type: 'string' },
    name: { type: 'string' },
    type: { type: 'string', enum: ['SOFTWARE', 'HARDWARE'] },
    keys: { type: 'array' },
    status: { type: 'string' },
    logged_in: { type: 'boolean' },
    available: { type: 'boolean' },
    saved_to_configuration: { type: 'boolean' },
    read_only: { type: 'boolean' },
  },
};

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
    default_csr_format: { type: 'string', enum: ['PEM', 'DER'] },
  },
};

// ── Fixtures ──────────────────────────────────────────────────────────────────

const softTokenFixture: Token = {
  id: 'softToken-0',
  name: 'softToken-0',
  type: TokenType.SOFTWARE,
  keys: [],
  status: TokenStatus.OK,
  logged_in: true,
  available: true,
  saved_to_configuration: true,
  read_only: false,
  possible_actions: [PossibleAction.EDIT_FRIENDLY_NAME, PossibleAction.TOKEN_CHANGE_PIN],
};

const testCaFixture: CertificateAuthority = {
  name: 'Test CA',
  subject_distinguished_name: 'CN=Test CA',
  issuer_distinguished_name: 'CN=Test CA',
  ocsp_response: CertificateAuthorityOcspResponse.OCSP_RESPONSE_GOOD,
  not_after: '2099-12-15T00:00:00.001Z',
  top_ca: true,
  path: 'CN=Test CA',
  authentication_only: false,
};

const newCaWithPemDefaultFixture: CertificateAuthority = {
  name: 'New CA',
  subject_distinguished_name: 'CN=New CA',
  issuer_distinguished_name: 'CN=New CA',
  ocsp_response: CertificateAuthorityOcspResponse.OCSP_RESPONSE_GOOD,
  not_after: '2099-12-15T00:00:00.001Z',
  top_ca: true,
  path: 'CN=New CA',
  authentication_only: false,
  default_csr_format: CsrFormat.PEM,
};

validateBody(tokenSchema, softTokenFixture);
validateBody(certAuthoritySchema, testCaFixture);
validateBody(certAuthoritySchema, newCaWithPemDefaultFixture);
validateBody({ type: 'array', items: certAuthoritySchema }, [testCaFixture]);
validateBody({ type: 'array', items: certAuthoritySchema }, [testCaFixture, newCaWithPemDefaultFixture]);

// ── Permissions ───────────────────────────────────────────────────────────────

const keysPermissions = [
  Permissions.VIEW_KEYS,
  Permissions.GENERATE_KEY,
  Permissions.EDIT_TOKEN_FRIENDLY_NAME,
  Permissions.UPDATE_TOKEN_PIN,
  Permissions.GENERATE_AUTH_CERT_REQ,
  Permissions.GENERATE_SIGN_CERT_REQ,
];

// ── Handlers ──────────────────────────────────────────────────────────────────

const tokensHandler = specHttp.get('/tokens', ({ response }) => response(200).json([softTokenFixture]));

const certAuthoritiesWithTestCaHandler = specHttp.get('/certificate-authorities', ({ response }) =>
  response(200).json([testCaFixture]),
);

const certAuthoritiesWithNewCaHandler = specHttp.get('/certificate-authorities', ({ response }) =>
  response(200).json([testCaFixture, newCaWithPemDefaultFixture]),
);

const membersHandler = specHttp.get('/clients', ({ response }) => response(200).json([]));

// ── Specs ─────────────────────────────────────────────────────────────────────

// MIGRATED-FROM: 0300-ss-keys-and-certificates.feature :: "Token edit page is navigable"
describe('Keys and Certificates — token edit page navigable (Browser Mode)', () => {
  it('opens token details dialog and shows policy alert when enforce_token_pin_policy is enabled', async () => {
    await renderRoute('/keys/sign-and-auth', {
      permissions: keysPermissions,
      msw: [tokensHandler, certAuthoritiesWithTestCaHandler],
    });

    // Patch enforce_token_pin_policy on the user store so the alert renders.
    useUser().$patch({
      initializationStatus: {
        is_anchor_imported: true,
        is_server_code_initialized: true,
        is_server_owner_initialized: true,
        software_token_init_status: 'INITIALIZED',
        enforce_token_pin_policy: true,
      },
    });

    // Seed the token into the tokens store so TokenDetailsDialog can look it up by id.
    useTokens().$patch({ tokens: [softTokenFixture] });

    // The token row must be visible on the Keys page.
    await expect.element(page.getByText('softToken-0').first()).toBeVisible();

    // Click the edit (pencil) icon button on the token row.
    await page.getByTestId('token-icon-button').first().click();

    // The token details dialog opens.
    await expect.element(page.getByTestId('token-friendly-name')).toBeVisible();

    // Expand the change-PIN section to reveal the policy alert.
    await page.getByTestId('token-open-pin-change-link').click();

    // The token policy alert must be present.
    await expect.element(page.getByTestId('alert-token-policy-enabled')).toBeVisible();
  });
});

// MIGRATED-FROM: 0300-ss-keys-and-certificates.feature :: "Add key wizard is navigable"
describe('Keys and Certificates — add key wizard navigable (Browser Mode)', () => {
  it('wizard opens on step 1, cancel closes it, re-open navigates to step 2 and back, close exits', async () => {
    await renderRoute('/add-key/softToken-0/SOFTWARE', {
      permissions: keysPermissions,
      msw: [certAuthoritiesWithTestCaHandler, membersHandler],
    });

    // Step 1 (Key Label) is the first step — cancel button and next button are present.
    await expect.element(page.getByTestId('cancel-button').first()).toBeVisible();
    await expect.element(page.getByTestId('next-button')).toBeVisible();

    // Close by clicking Cancel on step 1.
    await page.getByTestId('cancel-button').first().click();

    // After cancel the router navigates away; the wizard step is no longer rendered.
    // The add-key page (XrdElevatedViewSimple) title is gone.
    // Use query() for absence check — not.toBeVisible() times out when the element leaves the DOM.
    await expect.poll(() => page.getByTestId('next-button').query()).toBeNull();

    // Re-navigate to the wizard.
    await renderRoute('/add-key/softToken-0/SOFTWARE', {
      permissions: keysPermissions,
      msw: [certAuthoritiesWithTestCaHandler, membersHandler],
    });

    // Step 1 visible.
    await expect.element(page.getByTestId('next-button')).toBeVisible();

    // Move to step 2 (CSR Details) by clicking Next.
    await page.getByTestId('next-button').click();

    // Step 2: CSR usage select and certification service select are visible.
    await expect.element(page.getByTestId('csr-usage-select')).toBeVisible();
    await expect.element(page.getByTestId('csr-certification-service-select')).toBeVisible();

    // Navigate back to step 1 via Previous.
    await page.getByTestId('previous-button').click();

    // Step 1 controls are visible again.
    await expect.element(page.getByTestId('next-button')).toBeVisible();
    await expect.element(page.getByTestId('key-label-input')).toBeVisible();
  });
});

// MIGRATED-FROM: 0300-ss-keys-and-certificates.feature :: "Certificate format is preselected"
describe('Keys and Certificates — certificate format preselected (Browser Mode)', () => {
  it('CSR format field is disabled and shows PEM when a CA with default_csr_format=PEM is selected', async () => {
    await renderRoute('/add-key/softToken-0/SOFTWARE', {
      permissions: keysPermissions,
      msw: [certAuthoritiesWithNewCaHandler, membersHandler],
    });

    // Navigate from step 1 to step 2.
    await page.getByTestId('next-button').click();

    // Step 2 visible.
    await expect.element(page.getByTestId('csr-certification-service-select')).toBeVisible();

    // Select "New CA" which has default_csr_format: PEM.
    await page.getByTestId('csr-certification-service-select').click();
    await expect.element(page.getByRole('option', { name: 'New CA' })).toBeVisible();
    await page.getByRole('option', { name: 'New CA' }).click();

    // The format select must show PEM and be disabled (format is locked by the CA default).
    const formatSelect = page.getByTestId('csr-format-select');
    await expect.element(formatSelect).toBeVisible();

    // The selected value is visible in the Vuetify select control text.
    await expect.element(formatSelect).toHaveTextContent('PEM');

    // The field must be disabled because default_csr_format is set.
    // Vuetify renders a disabled v-select with the v-field--disabled CSS class on the outer div.
    // The <input type="text" disabled> also carries disabled. Both confirm the field is locked.
    // We assert on the outer wrapper which also carries aria-disabled semantics.
    await expect.element(page.getByRole('combobox', { name: 'CSR Format' })).toBeDisabled();
  });
});

// MIGRATED-FROM: 0300-ss-keys-and-certificates.feature :: "<$label> key is added and imported"
// Split slice — API/status assertions: DONE (KeysAndCsrTest#keyAddedAndImportedStatusAssertions).
// This spec covers the UI slice: wizard driven through all three steps with a mocked backend;
// asserts the generate-CSR step renders (observable wizard outcome).
describe('Keys and Certificates — add key wizard UI slice: wizard driven to generate step (Browser Mode)', () => {
  it('completes steps 1 and 2 and reaches the generate-CSR step with generate button visible', async () => {
    const csrSubjectFieldsHandler = specHttp.get('/certificate-authorities/{ca_name}/csr-subject-fields', ({ response }) =>
      response(200).json([
        { id: 'CN', label_key: 'CN', default_value: '', read_only: false, required: true, localized: false },
        { id: 'subjectAltName', label_key: 'subjectAltName', default_value: '', read_only: false, required: false, localized: false },
        { id: 'O', label_key: 'O', default_value: '', read_only: false, required: false, localized: false },
      ]),
    );

    await renderRoute('/add-key/softToken-0/SOFTWARE', {
      permissions: keysPermissions,
      msw: [certAuthoritiesWithTestCaHandler, membersHandler, csrSubjectFieldsHandler],
    });

    // Step 1 — enter a key label and proceed.
    await expect.element(page.getByTestId('key-label-input')).toBeVisible();
    await page.getByTestId('key-label-input').getByRole('textbox').fill('test auth key');
    await page.getByTestId('next-button').click();

    // Step 2 — CSR Details.
    await expect.element(page.getByTestId('csr-usage-select')).toBeVisible();

    // Select AUTHENTICATION usage.
    await page.getByTestId('csr-usage-select').click();
    await expect.element(page.getByRole('option', { name: 'AUTHENTICATION' })).toBeVisible();
    await page.getByRole('option', { name: 'AUTHENTICATION' }).click();

    // Select "Test CA".
    await page.getByTestId('csr-certification-service-select').click();
    await expect.element(page.getByRole('option', { name: 'Test CA' })).toBeVisible();
    await page.getByRole('option', { name: 'Test CA' }).click();

    // Select DER format.
    await page.getByTestId('csr-format-select').click();
    await expect.element(page.getByRole('option', { name: 'DER' })).toBeVisible();
    await page.getByRole('option', { name: 'DER' }).click();

    // Proceed to step 3 (save-button on step 2 = Continue/Next).
    await expect.element(page.getByTestId('save-button')).not.toBeDisabled();
    await page.getByTestId('save-button').click();

    // Step 3 — Generate CSR step renders; the generate-csr-button is the observable outcome.
    await expect.element(page.getByTestId('generate-csr-button')).toBeVisible();
  });
});
