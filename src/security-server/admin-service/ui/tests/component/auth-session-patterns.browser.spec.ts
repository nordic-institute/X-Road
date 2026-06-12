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
 * Part 4 — Auth / session / error-nav patterns.
 *
 * Auth model:
 *   - Cookie-session only. No client-side CSRF/XSRF token attach. No axios
 *     interceptors. CSRF testing is out of scope for this tier.
 *   - 401 responses are intentionally suppressed: addError() does not add a
 *     notification when the Axios error status is 401 (session-expiry path).
 *   - setupAddErrorNavigation(router, { 404: NotFound }) is wired inside
 *     renderRoute so the real nav-on-error code runs in all specs.
 *   - Session state is controlled by renderRoute's `session` option ('alive'
 *     | 'expired'). Default is 'alive'.
 *
 * Specs:
 *   A — API 404 on GET /clients/{id} while navigate:true is set triggers
 *       router.replace to the NotFound route.
 *   B — fetchSessionStatus() with { valid: false } marks the session dead and
 *       the app renders the session-expired dialog.
 *   C — API 401 on GET /clients does NOT surface an error notification (the
 *       suppress-on-401 path). A one-time delayed 401 is pre-registered via
 *       worker.use({ once: true }) to assert the loading state before the 401
 *       lands and clears clientsLoading.
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
    // SubsystemView watches the `id` prop and calls fetchClient(id), catching
    // with addError(error, { navigate: true }). With setupAddErrorNavigation
    // wired and a 404 rule pointing to RouteName.NotFound, the router is
    // replaced to the not-found route.
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
    // Start with a live session (default). Override the session-status endpoint
    // to return { valid: false }. Calling fetchSessionStatus() from the user
    // store exercises the real code path that calls appState.setSessionAlive(false),
    // which makes XrdApp render XrdLogoutDialog.
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
    // The addError() helper explicitly skips 401 responses (session-expiry).
    // ClientsListView.fetchData() calls addError(error) — without navigate:true —
    // so the routing context is not involved. The 401 is simply discarded and
    // no contextual-alert banner should appear.
    //
    // Pre-register a one-time delayed handler before renderRoute to avoid a
    // race where the mount-time GET fires before the Service Worker has the
    // override. { once: true } ensures only the first request gets the 401;
    // subsequent requests fall through to the base handler.
    //
    // addError() always returns Promise.reject(), even for suppressed 401s.
    // In browser mode, unhandled promise rejections inside the Playwright page
    // never reach the Vitest runner and never fail the suite regardless of any
    // listener here. This listener's only purpose is to call e.preventDefault()
    // and suppress the expected-401 console.error noise so the run output
    // stays clean. It provides no suite-level protection.
    const absorb401 = (e: PromiseRejectionEvent) => {
      if (e.reason?.response?.status === 401) {
        e.preventDefault();
      }
    };
    window.addEventListener('unhandledrejection', absorb401);

    try {
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

      // While the 401 is in-flight, v-data-table renders VProgressLinear (role="progressbar").
      await expect.element(page.getByRole('progressbar')).toBeVisible();

      // After the 200ms delay the 401 lands, clientsLoading → false, and the
      // progress bar element is removed from the DOM entirely.
      await expect.element(page.getByRole('progressbar')).not.toBeInTheDocument();

      // No contextual-alert should be present in the DOM — 401 is suppressed.
      expect(page.getByTestId('contextual-alert').query()).toBeNull();
    } finally {
      window.removeEventListener('unhandledrejection', absorb401);
    }
  });

  it('Spec D (session boot): session:"expired" option renders session-expired dialog on mount', async () => {
    // The { session: 'expired' } option calls appState.setSessionAlive(false)
    // during bootstrap, causing XrdApp to show XrdLogoutDialog immediately.
    await renderRoute('/clients', { session: 'expired' });

    await expect.element(page.getByTestId('dialog-title')).toBeVisible();
    await expect.element(page.getByText('Session expired')).toBeVisible();
  });
});
