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
import { delay } from 'msw';
import { renderRoute } from '../setup/render-route';
import { specHttp, validateBody } from '../setup/spec-http';
import { serviceDescriptionSchema } from '../setup/schemas';
import { submitDialogForm } from '../setup/dialog-helpers';
import { clientsFixture, createdServiceDescriptionFixture, serviceDescriptionFixture } from '../setup/msw-handlers';
import { worker } from '../setup/browser-setup';

const CLIENT_ID = 'CS:GOV:1234:SUBS1';
const SERVICES_PATH = `/clients/subsystem/${encodeURIComponent(CLIENT_ID)}/services`;

describe('Latency patterns — loading state (Browser Mode)', () => {
  it('Spec A: data table shows loading indicator while GET /clients is in-flight, then renders rows', async () => {
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

    await expect.element(page.getByRole('progressbar')).toBeVisible();

    await expect.element(page.getByTestId('client-name').first()).toBeVisible();
    expect(page.getByRole('progressbar').query()).toBeNull();
  });

  it('Spec B: save button shows aria-busy while POST is in-flight; XrdSimpleDialog guards re-submit', async () => {
    validateBody(serviceDescriptionSchema, serviceDescriptionFixture);

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

    submitDialogForm();

    await expect.element(saveBtn).toHaveAttribute('aria-busy', 'true');

    await expect.element(page.getByTestId('success-snackbar')).toBeVisible();
  });
});
