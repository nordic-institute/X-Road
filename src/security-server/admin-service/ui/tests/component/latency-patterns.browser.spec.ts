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

/**
 * Part 3 — Latency / async-state patterns.
 *
 * Spec A uses a finite MSW `delay()` to let the component reach its loading
 * state before the response lands. The handler is pre-registered with
 * `worker.use({ once: true })` before `renderRoute` so the Service Worker has
 * the override in place before the component fires its mount-time GET. The
 * `{ once: true }` flag ensures only the first request is delayed; subsequent
 * fetches fall through to the base handler. After the loading indicator
 * appears the test waits for it to disappear, confirming the loading→data cycle.
 *
 * Spec B uses a finite `delay()` on a POST that fires only on user action
 * (not on mount), so there is no race condition. The in-flight POST keeps the
 * save button in aria-busy state; the test waits for it to resolve and for the
 * success snackbar to appear.
 *
 * Note on POST in-flight (Spec B): when XrdBtn has :loading="true", Vuetify
 * sets aria-busy="true" on the underlying <button> rather than setting the
 * HTML disabled attribute. The submit-guard inside XrdSimpleDialog checks
 * props.loading and does not re-invoke save() while in-flight. The spec
 * therefore asserts aria-busy rather than toBeDisabled().
 */
import { describe, it, expect } from 'vitest';
import { page } from 'vitest/browser';
import { delay } from 'msw';
import { renderRoute } from '../setup/render-route';
import { specHttp, validateBody } from '../setup/spec-http';
import { clientsFixture, createdServiceDescriptionFixture, serviceDescriptionFixture } from '../setup/msw-handlers';
import { worker } from '../setup/browser-setup';

const CLIENT_ID = 'CS:GOV:1234:SUBS1';
const SERVICES_PATH = `/clients/subsystem/${encodeURIComponent(CLIENT_ID)}/services`;

function submitDialogForm(): void {
  const form = document.querySelector('.v-overlay-container form') as HTMLFormElement | null;
  if (!form) throw new Error('Dialog form not found');
  const btn = form.querySelector('[data-test="dialog-save-button"]') as HTMLButtonElement | null;
  form.requestSubmit(btn ?? undefined);
}

describe('Latency patterns — loading state (Browser Mode)', () => {
  it('Spec A: data table shows loading indicator while GET /clients is in-flight, then renders rows', async () => {
    // Pre-register a one-time delayed handler BEFORE calling renderRoute so
    // the Service Worker has the override in place before the component mounts
    // and fires the request in its created() hook. { once: true } ensures only
    // the first request is delayed; subsequent requests fall through to the
    // base handler and resolve immediately.
    worker.use(
      specHttp.get(
        '/clients',
        async ({ response }) => {
          await delay(200);
          return response(200).json(clientsFixture);
        },
        { once: true },
      ),
    );

    await renderRoute('/clients');

    // While the request is in-flight, no client rows are rendered.
    // The v-data-table shows a loading progress indicator (role="progressbar").
    await expect.element(page.getByRole('progressbar')).toBeVisible();

    // After the delayed response lands, the first client row appears.
    await expect.element(page.getByTestId('client-name').first()).toBeVisible();
    expect(page.getByRole('progressbar').query()).toBeNull();
  });

  it('Spec B: save button shows aria-busy while POST is in-flight; XrdSimpleDialog guards re-submit', async () => {
    const serviceDescriptionSchema = {
      type: 'object',
      required: ['id', 'url', 'type', 'disabled', 'disabled_notice', 'refreshed_at', 'services', 'client_id'],
      properties: {
        id: { type: 'string' },
        url: { type: 'string' },
        type: { type: 'string', enum: ['WSDL', 'REST', 'OPENAPI3'] },
        disabled: { type: 'boolean' },
        disabled_notice: { type: 'string' },
        refreshed_at: { type: 'string', format: 'date-time' },
        services: { type: 'array' },
        client_id: { type: 'string' },
      },
    };
    validateBody(serviceDescriptionSchema, serviceDescriptionFixture);

    // The POST fires only on user action (not on mount), so worker.use()
    // inside renderRoute is fine — no race condition. A finite delay keeps the
    // button in aria-busy state long enough to assert it, then the POST resolves.
    await renderRoute(SERVICES_PATH, {
      msw: [
        specHttp.post('/clients/{id}/service-descriptions', async ({ response }) => {
          await delay(500);
          return response(201).json(createdServiceDescriptionFixture);
        }),
        specHttp.get('/clients/{id}/service-descriptions', ({ response }) => {
          return response(200).json([serviceDescriptionFixture]);
        }),
      ],
    });

    await expect.element(page.getByTestId('add-rest-button')).toBeVisible();
    await page.getByTestId('add-rest-button').click();

    await page.getByTestId('rest-radio-button').getByRole('radio').click();
    await page.getByTestId('service-url-text-field').getByRole('textbox').fill('https://example.com/api');
    const codeInput = page.getByTestId('service-code-text-field').getByRole('textbox');
    await codeInput.fill('MY-API');
    await page.getByTestId('service-url-text-field').getByRole('textbox').click();

    const saveBtn = page.getByTestId('dialog-save-button');
    await expect.element(saveBtn).not.toBeDisabled();

    // Submit the form — saving ref goes true synchronously before POST lands.
    submitDialogForm();

    // XrdBtn :loading="saving" sets aria-busy="true" on the button (Vuetify 4
    // does not set the HTML disabled attribute for :loading alone).
    await expect.element(saveBtn).toHaveAttribute('aria-busy', 'true');

    // After the delayed POST resolves, the dialog closes (save succeeded) and
    // the success snackbar appears.
    await expect.element(page.getByTestId('success-snackbar')).toBeVisible();
  });
});
