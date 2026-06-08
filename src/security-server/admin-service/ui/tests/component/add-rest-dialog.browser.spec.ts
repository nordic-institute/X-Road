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
import { describe, it, beforeAll, expect } from 'vitest';
import { render } from 'vitest-browser-vue';
import { page } from 'vitest/browser';
import AddRestDialog from '@/views/Clients/Services/AddRestDialog.vue';
import { configureGlobals } from '../setup/vue-test-utils';

beforeAll(() => {
  configureGlobals();
});

describe('AddRestDialog — validation (Browser Mode)', () => {
  it('Save button is disabled when all fields are empty', async () => {
    await render(AddRestDialog, { props: { clientId: 'CS:GOV:1234:SUBS1' } });

    const saveBtn = page.getByTestId('dialog-save-button');
    await expect.element(saveBtn).toBeVisible();
    await expect.element(saveBtn).toBeDisabled();
  });

  it('Save button remains disabled with invalid URL (no service code)', async () => {
    await render(AddRestDialog, { props: { clientId: 'CS:GOV:1234:SUBS1' } });

    const urlInput = page.getByTestId('service-url-text-field').getByRole('textbox');
    await urlInput.fill('not-a-valid-url');
    await page.getByTestId('service-code-text-field').getByRole('textbox').click();

    const saveBtn = page.getByTestId('dialog-save-button');
    await expect.element(saveBtn).toBeDisabled();
  });

  it('URL is not valid error is visible after entering invalid URL and blurring', async () => {
    await render(AddRestDialog, { props: { clientId: 'CS:GOV:1234:SUBS1' } });

    const urlInput = page.getByTestId('service-url-text-field').getByRole('textbox');
    await urlInput.fill('not-a-valid-url');
    await page.getByTestId('service-code-text-field').getByRole('textbox').click();

    await expect.element(page.getByText('URL is not valid')).toBeVisible();
  });

  it('Service Code field error is visible after entering a valid URL and blurring service code', async () => {
    await render(AddRestDialog, { props: { clientId: 'CS:GOV:1234:SUBS1' } });

    const urlInput = page.getByTestId('service-url-text-field').getByRole('textbox');
    await urlInput.fill('https://example.com/api');

    const codeInput = page.getByTestId('service-code-text-field').getByRole('textbox');
    await codeInput.fill('TEMP');
    await codeInput.fill('');
    await urlInput.click();

    await expect.element(page.getByText('Service Code is required')).toBeVisible();

    const saveBtn = page.getByTestId('dialog-save-button');
    await expect.element(saveBtn).toBeDisabled();
  });

  it('Save button becomes enabled once URL and Service Code are both valid', async () => {
    await render(AddRestDialog, { props: { clientId: 'CS:GOV:1234:SUBS1' } });

    await page.getByTestId('rest-radio-button').getByRole('radio').click();

    const urlInput = page.getByTestId('service-url-text-field').getByRole('textbox');
    await urlInput.fill('https://example.com/api');

    const codeInput = page.getByTestId('service-code-text-field').getByRole('textbox');
    await codeInput.fill('MY-SERVICE');
    await urlInput.click();

    const saveBtn = page.getByTestId('dialog-save-button');
    await expect.element(saveBtn).not.toBeDisabled();
  });
});
