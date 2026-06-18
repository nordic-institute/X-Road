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
import { createdServiceDescriptionFixture } from '../setup/msw-handlers';

// ── Schema validation ─────────────────────────────────────────────────────────
// createdServiceDescriptionFixture is already AJV-validated in msw-handlers.ts.
// Inline-validate the shape we care about for the success path.

const serviceDescriptionListSchema = {
  type: 'array',
  items: {
    type: 'object',
    required: ['id', 'url', 'type', 'disabled', 'disabled_notice', 'refreshed_at', 'services', 'client_id'],
    properties: {
      id: { type: 'string' },
      url: { type: 'string' },
      type: { type: 'string' },
      disabled: { type: 'boolean' },
      disabled_notice: { type: 'string' },
      refreshed_at: { type: 'string' },
      services: { type: 'array' },
      client_id: { type: 'string' },
    },
  },
};

validateBody(serviceDescriptionListSchema, [createdServiceDescriptionFixture]);

// ── Permissions ───────────────────────────────────────────────────────────────

const CLIENT_ID = 'CS:GOV:1234:SUBS1';
const SERVICES_PATH = `/clients/subsystem/${encodeURIComponent(CLIENT_ID)}/services`;

const servicesPermissions = [
  Permissions.VIEW_CLIENTS,
  Permissions.VIEW_CLIENT_DETAILS,
  Permissions.VIEW_CLIENT_SERVICES,
  Permissions.ADD_OPENAPI3,
  Permissions.ENABLE_DISABLE_WSDL,
];

// ── Specs ─────────────────────────────────────────────────────────────────────

// MIGRATED-FROM: 0550-ss-client-rest-services.feature :: "Client service with Base Path is configured"
// Split slice — API/persistence assertions DONE (RestServiceConflictTest).
// This spec covers the UI slice: client-side form validation errors in the Add REST dialog.
// CLIENT-SIDE validation: URL format check (`invalidUrl` rule) and required service code
// are both enforced by vee-validate BEFORE any server call — the save button is blocked
// and specific error messages are shown. No server interaction is needed to assert these.
describe('REST Services — add REST dialog form validation errors (Browser Mode)', () => {
  it('invalid URL triggers "URL is not valid" error and save button stays disabled', async () => {
    await renderRoute(SERVICES_PATH, {
      permissions: servicesPermissions,
      msw: [
        specHttp.post('/clients/{id}/service-descriptions', ({ response }) =>
          response(201).json(createdServiceDescriptionFixture),
        ),
        specHttp.get('/clients/{id}/service-descriptions', ({ response }) =>
          response(200).json([createdServiceDescriptionFixture]),
        ),
      ],
    });

    await expect.element(page.getByTestId('add-rest-button')).toBeVisible();
    await page.getByTestId('add-rest-button').click();

    const saveBtn = page.getByTestId('dialog-save-button');
    await expect.element(saveBtn).toBeVisible();

    // Select REST (base-path) radio.
    await page.getByTestId('rest-radio-button').getByRole('radio').click();

    // Enter an invalid URL and blur.
    const urlInput = page.getByTestId('service-url-text-field').getByRole('textbox');
    await urlInput.fill('invalid-url');
    await page.getByTestId('service-code-text-field').getByRole('textbox').click();

    // The specific error message must be visible.
    await expect.element(page.getByText('URL is not valid')).toBeVisible();
    // Save must remain blocked.
    await expect.element(saveBtn).toBeDisabled();
  });

  it('save enabled with valid URL + code; clearing code disables save', async () => {
    await renderRoute(SERVICES_PATH, {
      permissions: servicesPermissions,
      msw: [
        specHttp.post('/clients/{id}/service-descriptions', ({ response }) =>
          response(201).json(createdServiceDescriptionFixture),
        ),
        specHttp.get('/clients/{id}/service-descriptions', ({ response }) =>
          response(200).json([createdServiceDescriptionFixture]),
        ),
      ],
    });

    await expect.element(page.getByTestId('add-rest-button')).toBeVisible();
    await page.getByTestId('add-rest-button').click();

    const saveBtn = page.getByTestId('dialog-save-button');
    await expect.element(saveBtn).toBeVisible();

    // Select REST radio.
    await page.getByTestId('rest-radio-button').getByRole('radio').click();

    const urlInput = page.getByTestId('service-url-text-field').getByRole('textbox');
    await urlInput.fill('http://example.com');

    const codeInput = page.getByTestId('service-code-text-field').getByRole('textbox');
    await codeInput.fill('s3c1');
    await urlInput.click();

    // Save must now be enabled (valid URL + code).
    await expect.element(saveBtn).not.toBeDisabled();

    // Clear service code — save must become disabled (required field empty).
    await codeInput.fill('');
    await urlInput.click();
    await expect.element(saveBtn).toBeDisabled();
  });

  // MIGRATED-FROM: 0550-ss-client-rest-services.feature :: "Client service with Base Path is configured"
  it('whitespace-only service code triggers "The Service Code field is required" error text', async () => {
    await renderRoute(SERVICES_PATH, {
      permissions: servicesPermissions,
      msw: [
        specHttp.post('/clients/{id}/service-descriptions', ({ response }) =>
          response(201).json(createdServiceDescriptionFixture),
        ),
        specHttp.get('/clients/{id}/service-descriptions', ({ response }) =>
          response(200).json([createdServiceDescriptionFixture]),
        ),
      ],
    });

    await expect.element(page.getByTestId('add-rest-button')).toBeVisible();
    await page.getByTestId('add-rest-button').click();

    const saveBtn = page.getByTestId('dialog-save-button');
    await expect.element(saveBtn).toBeVisible();

    // Select REST radio.
    await page.getByTestId('rest-radio-button').getByRole('radio').click();

    const urlInput = page.getByTestId('service-url-text-field').getByRole('textbox');
    await urlInput.fill('http://example.com');

    const codeInput = page.getByTestId('service-code-text-field').getByRole('textbox');
    // Whitespace-only value triggers the required validator (String.trim() = "").
    await codeInput.fill('   ');
    await urlInput.click();

    // The "required" validation message for the "Service Code" field must be visible.
    await expect.element(page.getByText('The Service Code field is required')).toBeVisible();
    // Save must remain blocked.
    await expect.element(saveBtn).toBeDisabled();
  });

  it('valid URL and non-empty service code enables save, submitting shows success toast', async () => {
    await renderRoute(SERVICES_PATH, {
      permissions: servicesPermissions,
      msw: [
        specHttp.post('/clients/{id}/service-descriptions', ({ response }) =>
          response(201).json(createdServiceDescriptionFixture),
        ),
        specHttp.get('/clients/{id}/service-descriptions', ({ response }) =>
          response(200).json([createdServiceDescriptionFixture]),
        ),
      ],
    });

    await expect.element(page.getByTestId('add-rest-button')).toBeVisible();
    await page.getByTestId('add-rest-button').click();

    const saveBtn = page.getByTestId('dialog-save-button');

    // Select REST radio.
    await page.getByTestId('rest-radio-button').getByRole('radio').click();

    const urlInput = page.getByTestId('service-url-text-field').getByRole('textbox');
    await urlInput.fill('http://example.com');

    const codeInput = page.getByTestId('service-code-text-field').getByRole('textbox');
    await codeInput.fill('s3c1');
    await urlInput.click();

    // Save must now be enabled.
    await expect.element(saveBtn).not.toBeDisabled();

    // Submit via form submit (mirrors client-services-flow pattern).
    const form = document.querySelector('.v-overlay-container form') as HTMLFormElement | null;
    if (!form) throw new Error('Dialog form not found');
    const btn = form.querySelector('[data-test="dialog-save-button"]') as HTMLButtonElement | null;
    form.requestSubmit(btn ?? undefined);

    // Success toast must appear.
    await expect.element(page.getByTestId('success-snackbar')).toBeVisible();
    await expect.element(page.getByText('REST service added')).toBeVisible();
  });
});
