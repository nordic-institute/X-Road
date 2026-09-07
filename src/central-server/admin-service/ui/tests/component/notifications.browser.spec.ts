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

describe('CS Global Alerts — DS TLS ACME failure (Browser Mode)', () => {
  it('a failing DS TLS ACME enrollment renders a distinct readable message, not the raw i18n key', async () => {
    await renderRoute('/members', {
      msw: [
        specHttp.untyped.get('/api/v1/notifications/alerts', () =>
          HttpResponse.json([
            { errorCode: 'status.dataspace_tls_acme.enrollment_failing', metadata: ['CA unreachable'] },
          ]),
        ),
      ],
    });

    await expect
      .element(page.getByText('DS TLS certificate ACME enrollment failing: CA unreachable'))
      .toBeVisible();

    // The raw i18n key must never be what actually renders on screen.
    await expect.element(page.getByText('status.dataspace_tls_acme.enrollment_failing')).not.toBeInTheDocument();
  });

  it('a failing DS TLS ACME renewal renders a distinct readable message, not the raw i18n key', async () => {
    await renderRoute('/members', {
      msw: [
        specHttp.untyped.get('/api/v1/notifications/alerts', () =>
          HttpResponse.json([{ errorCode: 'status.dataspace_tls_acme.renewal_failing', metadata: ['CA unreachable'] }]),
        ),
      ],
    });

    await expect.element(page.getByText('DS TLS certificate ACME renewal failing: CA unreachable')).toBeVisible();

    // The raw i18n key must never be what actually renders on screen.
    await expect.element(page.getByText('status.dataspace_tls_acme.renewal_failing')).not.toBeInTheDocument();
  });

  it('a successful enrollment/renewal (no recorded error) shows no DS TLS ACME alert', async () => {
    await renderRoute('/members', {
      msw: [specHttp.untyped.get('/api/v1/notifications/alerts', () => HttpResponse.json([]))],
    });

    await expect.element(page.getByText('DS TLS certificate ACME enrollment failing', { exact: false })).not.toBeInTheDocument();
    await expect.element(page.getByText('DS TLS certificate ACME renewal failing', { exact: false })).not.toBeInTheDocument();
  });
});
