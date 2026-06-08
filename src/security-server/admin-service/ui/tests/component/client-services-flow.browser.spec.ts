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
import { http, HttpResponse } from 'msw';
import { renderRoute } from '../setup/render-route';
import { createdServiceDescriptionFixture } from '../setup/msw-handlers';

const CLIENT_ID = 'CS:GOV:1234:SUBS1';
const SERVICES_PATH = `/clients/subsystem/${encodeURIComponent(CLIENT_ID)}/services`;

function submitDialogForm(): void {
  const form = document.querySelector('.v-overlay-container form') as HTMLFormElement | null;
  if (!form) throw new Error('Dialog form not found');
  const btn = form.querySelector('[data-test="dialog-save-button"]') as HTMLButtonElement | null;
  form.requestSubmit(btn ?? undefined);
}

describe('Client Services page-flow (Browser Mode)', () => {
  it('navigates to Services, opens Add REST dialog, validates, submits, shows toast and new row', async () => {
    await renderRoute(SERVICES_PATH, {
      msw: [
        http.post('/api/v1/clients/:clientId/service-descriptions', () => {
          return HttpResponse.json(createdServiceDescriptionFixture, { status: 201 });
        }),
        http.get('/api/v1/clients/:clientId/service-descriptions', () => {
          return HttpResponse.json([createdServiceDescriptionFixture]);
        }),
      ],
    });

    await expect.element(page.getByTestId('add-rest-button')).toBeVisible();

    await page.getByTestId('add-rest-button').click();

    const saveBtn = page.getByTestId('dialog-save-button');
    await expect.element(saveBtn).toBeVisible();
    await expect.element(saveBtn).toBeDisabled();

    await page.getByTestId('rest-radio-button').getByRole('radio').click();

    const urlInput = page.getByTestId('service-url-text-field').getByRole('textbox');
    await urlInput.fill('not-a-valid-url');
    await page.getByTestId('service-code-text-field').getByRole('textbox').click();

    await expect.element(page.getByText('URL is not valid')).toBeVisible();
    await expect.element(saveBtn).toBeDisabled();

    await urlInput.fill('');
    await urlInput.fill('https://example.com/rest-api');

    const codeInput = page.getByTestId('service-code-text-field').getByRole('textbox');
    await codeInput.fill('MY-API');
    await urlInput.click();

    await expect.element(saveBtn).not.toBeDisabled();

    submitDialogForm();

    await expect.element(page.getByTestId('success-snackbar')).toBeVisible();
    await expect.element(page.getByText('REST service added')).toBeVisible();

    await page.getByTestId('service-description-accordion').getByTestId('service-description-header-url').click();
    await expect.element(page.getByText('MY-API')).toBeVisible();
  });
});
