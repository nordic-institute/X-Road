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
import { delay, HttpResponse } from 'msw';
import { renderRoute } from '../setup/render-route';
import { specHttp } from '../setup/spec-http';
import { worker } from '../setup/browser-setup';
import { RouteName } from '@/global';

const authBaseUrl = 'https://localhost:8888';

const loginErrorHandler = specHttp.untyped.post(`${authBaseUrl}/login`, () =>
  HttpResponse.json({ error: 'invalid credentials' }, { status: 401 }),
);

const logoutHangHandler = specHttp.untyped.post(`${authBaseUrl}/logout`, async () => {
  await delay('infinite');
  return HttpResponse.json({});
});

const sessionExpiredStatusHandler = specHttp.untyped.get('/api/v1/notifications/session-status', () =>
  HttpResponse.json({ valid: false }),
);

describe('0800 — Authentication (Browser Mode)', () => {
  it('Invalid password rejected — login error message renders, form stays visible', async () => {
    worker.use(loginErrorHandler);

    await renderRoute('/login');

    await expect.element(page.getByTestId('login-username-input')).toBeVisible();

    await page.getByTestId('login-username-input').getByRole('textbox').fill('xrd');
    await page.getByTestId('login-password-input').getByRole('textbox').fill('INVALID');
    await page.getByTestId('login-button').click();

    await expect.element(page.getByText('Wrong username or password')).toBeVisible();
    await expect.element(page.getByTestId('login-username-input')).toBeVisible();
  });

  it('Invalid username rejected — login error message renders, form stays visible', async () => {
    worker.use(loginErrorHandler);

    await renderRoute('/login');

    await expect.element(page.getByTestId('login-username-input')).toBeVisible();

    await page.getByTestId('login-username-input').getByRole('textbox').fill('INVALID');
    await page.getByTestId('login-password-input').getByRole('textbox').fill('secret123!');
    await page.getByTestId('login-button').click();

    await expect.element(page.getByText('Wrong username or password')).toBeVisible();
    await expect.element(page.getByTestId('login-username-input')).toBeVisible();
  });

  it('User can log out — logout button navigates to login route', async () => {
    worker.use(logoutHangHandler);

    const { router } = await renderRoute('/clients');

    await page.getByTestId('user-menu').click();
    await page.getByTestId('logout-button').click();

    await expect.element(page.getByTestId('login-username-input')).toBeVisible();
    expect(router.currentRoute.value.name).toBe(RouteName.Login);
  });

  it('Automatic logout on timeout — OK-click navigates to login route', async () => {
    worker.use(logoutHangHandler, sessionExpiredStatusHandler);

    const { router } = await renderRoute('/clients', { session: 'expired' });

    await expect.element(page.getByTestId('dialog-title')).toBeVisible();
    await expect.element(page.getByText('Session expired')).toBeVisible();

    await page.getByTestId('dialog-save-button').click();

    await expect.element(page.getByTestId('login-username-input')).toBeVisible();
    expect(router.currentRoute.value.name).toBe(RouteName.Login);
  });
});
