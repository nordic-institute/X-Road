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
import { HttpResponse } from 'msw';
import { renderRoute } from '../setup/render-route';
import { specHttp } from '../setup/spec-http';
import { worker } from '../setup/browser-setup';

// VITE_AUTH_URL in the dev .env is https://localhost:8080 — axiosAuth posts to that origin.
const authBaseUrl = 'https://localhost:8080';

// Mocks a 401 from the login endpoint so the UI renders the error message.
const loginRejectHandler = specHttp.untyped.post(`${authBaseUrl}/login`, () =>
  HttpResponse.json({ error: 'invalid credentials' }, { status: 401 }),
);

describe('0200 — CS Authentication — invalid password (Browser Mode)', () => {
  it('401 on wrong password renders error message and keeps login form visible', async () => {
    worker.use(loginRejectHandler);

    await renderRoute('/login');

    await expect.element(page.getByTestId('login-username-input')).toBeVisible();

    await page.getByTestId('login-username-input').getByRole('textbox').fill('xrd');
    await page.getByTestId('login-password-input').getByRole('textbox').fill('INVALID');
    await page.getByTestId('login-button').click();

    await expect.element(page.getByText('Wrong username or password')).toBeVisible();
    await expect.element(page.getByTestId('login-username-input')).toBeVisible();
  });
});

describe('0200 — CS Authentication — invalid username (Browser Mode)', () => {
  it('401 on wrong username renders error message and keeps login form visible', async () => {
    worker.use(loginRejectHandler);

    await renderRoute('/login');

    await expect.element(page.getByTestId('login-username-input')).toBeVisible();

    await page.getByTestId('login-username-input').getByRole('textbox').fill('INVALID');
    await page.getByTestId('login-password-input').getByRole('textbox').fill('secret123!');
    await page.getByTestId('login-button').click();

    await expect.element(page.getByText('Wrong username or password')).toBeVisible();
    await expect.element(page.getByTestId('login-username-input')).toBeVisible();
  });
});
