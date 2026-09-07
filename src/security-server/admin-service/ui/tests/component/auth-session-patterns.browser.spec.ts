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
import { useUser } from '@/store/modules/user';

describe('Auth / session / error-nav patterns (Browser Mode)', () => {
  it('Spec A: GET /clients/{id} returns 404 with navigate:true → router replaces to NotFound', async () => {
    const unknownId = 'CS:GOV:UNKNOWN:MISSING';
    const encodedId = encodeURIComponent(unknownId);

    const { router } = await renderRoute(`/clients/subsystem/${encodedId}/details`, {
      msw: [
        specHttp.get('/clients/{id}', ({ response }) => {
          return response(404).empty();
        }),
      ],
    });

    await expect.element(page.getByTestId('404-view')).toBeVisible();
    expect(router.currentRoute.value.name).toBe('not-found');
  });

  it('Spec B: fetchSessionStatus returns { valid: false } → app shows session-expired dialog', async () => {
    await renderRoute('/clients', {
      msw: [
        specHttp.untyped.get('/api/v1/notifications/session-status', () => {
          return HttpResponse.json({ valid: false });
        }),
      ],
    });

    const userStore = useUser();
    await userStore.fetchSessionStatus();

    // XrdLogoutDialog renders with title "Session expired" inside XrdConfirmDialog.
    await expect.element(page.getByTestId('dialog-title')).toBeVisible();
    await expect.element(page.getByText('Session expired')).toBeVisible();
  });

  it('Spec C: GET /clients returns 401 → no error notification is surfaced', async () => {
    worker.use(
      specHttp.get(
        '/clients',
        async ({ response }) => {
          await delay(200);
          return response(401).empty();
        },
        { once: true },
      ),
    );

    await renderRoute('/clients');

    await expect.element(page.getByRole('progressbar')).toBeVisible();

    await expect.element(page.getByRole('progressbar')).not.toBeInTheDocument();

    expect(page.getByTestId('contextual-alert').query()).toBeNull();
  });

  it('Spec D (session boot): session:"expired" option renders session-expired dialog on mount', async () => {
    await renderRoute('/clients', { session: 'expired' });

    await expect.element(page.getByTestId('dialog-title')).toBeVisible();
    await expect.element(page.getByText('Session expired')).toBeVisible();
  });
});
