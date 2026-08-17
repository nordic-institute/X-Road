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
import { describe, it, expect, vi } from 'vitest';
import { page } from 'vitest/browser';
import { HttpResponse } from 'msw';
import { renderRoute } from '../setup/render-route';
import { specHttp } from '../setup/spec-http';
import { Permissions } from '@/global';

const DS_TLS_CERTIFICATE_PATH = '/keys/ds-tls-cert';

const allPermissions = [
  Permissions.VIEW_DS_TLS_CERT,
  Permissions.DOWNLOAD_DS_TLS_CERT,
  Permissions.GENERATE_DS_TLS_KEY,
  Permissions.GENERATE_DS_TLS_CSR,
  Permissions.UPLOAD_DS_TLS_CERT,
];

describe('SS DS TLS Certificate — no key generated yet (Browser Mode)', () => {
  it('shows the generate key button', async () => {
    await renderRoute(DS_TLS_CERTIFICATE_PATH, {
      permissions: allPermissions,
      msw: [specHttp.untyped.get('/api/v1/ds-tls-certificate', () => HttpResponse.json({ key_generated: false }))],
    });

    await expect.element(page.getByTestId('management-service-certificate-generateKey')).toBeVisible();
  });

  it('generates a key', async () => {
    const generateSpy = vi.fn();

    await renderRoute(DS_TLS_CERTIFICATE_PATH, {
      permissions: allPermissions,
      msw: [
        specHttp.untyped.get('/api/v1/ds-tls-certificate', () => HttpResponse.json({ key_generated: false })),
        specHttp.untyped.post('/api/v1/ds-tls-certificate/key', () => {
          generateSpy();
          return new HttpResponse(null, { status: 201 });
        }),
      ],
    });

    await page.getByTestId('management-service-certificate-generateKey').click();
    await page.getByTestId('dialog-save-button').click();

    await expect.poll(() => generateSpy.mock.calls.length).toBeGreaterThan(0);
  });
});

describe('SS DS TLS Certificate — key generated, certificate pending (Browser Mode)', () => {
  it('shows the pending badge and offers CSR generation', async () => {
    const csrSpy = vi.fn();

    await renderRoute(DS_TLS_CERTIFICATE_PATH, {
      permissions: allPermissions,
      msw: [
        specHttp.untyped.get('/api/v1/ds-tls-certificate', () => HttpResponse.json({ key_generated: true })),
        specHttp.untyped.post('/api/v1/ds-tls-certificate/csr', async ({ request }) => {
          csrSpy(await request.json());
          return HttpResponse.arrayBuffer(new ArrayBuffer(0), { status: 200 });
        }),
      ],
    });

    await expect.element(page.getByText('DataSpace TLS key generated')).toBeVisible();
    await expect.element(page.getByTestId('management-service-certificate-generateCsr')).toBeVisible();

    await page.getByTestId('management-service-certificate-generateCsr').click();
    await page.getByTestId('enter-distinguished-name').getByRole('textbox').fill('CN=ds.example.org');
    await page.getByTestId('dialog-save-button').click();

    await expect.poll(() => csrSpy.mock.calls.length).toBeGreaterThan(0);
    expect(csrSpy).toHaveBeenCalledWith({ name: 'CN=ds.example.org' });
  });
});

describe('SS DS TLS Certificate — certificate-only upload (Browser Mode)', () => {
  it('uploads a certificate without ever collecting a private key', async () => {
    const uploadSpy = vi.fn();

    await renderRoute(DS_TLS_CERTIFICATE_PATH, {
      permissions: allPermissions,
      msw: [
        specHttp.untyped.get('/api/v1/ds-tls-certificate', () => HttpResponse.json({ key_generated: true })),
        specHttp.untyped.post('/api/v1/ds-tls-certificate/certificate', async ({ request }) => {
          const body = await request.formData();
          uploadSpy(Array.from(body.keys()).sort());
          return HttpResponse.json(
            {
              hash: 'AABB1122CCDD3344',
              issuer_common_name: 'ds.example.org',
              issuer_distinguished_name: 'CN=ds.example.org',
              subject_common_name: 'ds.example.org',
              subject_distinguished_name: 'CN=ds.example.org',
              serial: '1',
              version: 3,
              signature: 'abc123',
              signature_algorithm: 'SHA256withRSA',
              public_key_algorithm: 'RSA',
              rsa_public_key_exponent: 65537,
              rsa_public_key_modulus: 'deadbeef',
              not_before: '2024-01-01T00:00:00Z',
              not_after: '2026-01-01T00:00:00Z',
              key_usages: [],
              subject_alternative_names: '',
            },
            { status: 200 },
          );
        }),
      ],
    });

    await expect.element(page.getByTestId('upload-management-service-certificate')).toBeVisible();
    await page.getByTestId('upload-management-service-certificate').click();

    const fileInputs = document.querySelectorAll('input[type="file"]');
    expect(fileInputs.length).toBe(1);

    const certFile = new File(['-----BEGIN CERTIFICATE-----\ncert\n-----END CERTIFICATE-----'], 'ds-https.crt', {
      type: 'application/x-pem-file',
    });
    await page.elementLocator(fileInputs[0] as HTMLInputElement).upload(certFile);
    await expect.element(page.getByTestId('dialog-save-button')).not.toBeDisabled();

    await page.getByTestId('dialog-save-button').click();

    await expect.poll(() => uploadSpy.mock.calls.length).toBeGreaterThan(0);
    expect(uploadSpy).toHaveBeenCalledWith(['certificate']);
  });
});
