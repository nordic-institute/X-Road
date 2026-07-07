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
import type { CertificateDetails } from '@/openapi-types';

const TLS_CERTIFICATES_PATH = '/settings/tls-certificates';

const allPermissions = [
  Permissions.VIEW_MANAGEMENT_SERVICE_TLS_CERT,
  Permissions.DOWNLOAD_MANAGEMENT_SERVICE_TLS_CERT,
  Permissions.GENERATE_MANAGEMENT_SERVICE_TLS_KEY_CERT,
  Permissions.GENERATE_MANAGEMENT_SERVICE_TLS_CSR,
  Permissions.UPLOAD_MANAGEMENT_SERVICE_TLS_CERT,
];

const certDetails: CertificateDetails = {
  hash: 'AABB1122CCDD3344',
  issuer_common_name: 'cs.example.org',
  issuer_distinguished_name: 'CN=cs.example.org',
  subject_common_name: 'cs.example.org',
  subject_distinguished_name: 'CN=cs.example.org',
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
};

const certDetailsAfterRegenerate: CertificateDetails = {
  ...certDetails,
  hash: 'FFEE9988DDCC7766',
};

function certHandler(cert: CertificateDetails = certDetails) {
  return specHttp.get('/management-services-configuration/certificate', ({ response }) =>
    response(200).json(cert),
  );
}

describe('0860 — CS TLS Certificates — key hash visible and all action buttons enabled (Browser Mode)', () => {
  it('hash renders and all four action buttons are present when all permissions granted', async () => {
    await renderRoute(TLS_CERTIFICATES_PATH, {
      permissions: allPermissions,
      msw: [certHandler()],
    });

    await expect.element(page.getByTestId('view-management-service-certificate')).toBeVisible();
    // XrdHashValue formats the hash with colons; XrdLabelWithIcon prepends the icon name in DOM text
    await expect.element(page.getByTestId('view-management-service-certificate')).toHaveTextContent(
      'AA:BB:11:22:CC:DD:33:44',
    );

    await expect.element(page.getByTestId('download-management-service-certificate')).toBeVisible();
    await expect.element(page.getByTestId('download-management-service-certificate')).not.toBeDisabled();

    await expect.element(page.getByTestId('management-service-certificate-generateKey')).toBeVisible();
    await expect.element(page.getByTestId('management-service-certificate-generateKey')).not.toBeDisabled();

    await expect.element(page.getByTestId('management-service-certificate-generateCsr')).toBeVisible();
    await expect.element(page.getByTestId('management-service-certificate-generateCsr')).not.toBeDisabled();

    await expect.element(page.getByTestId('upload-management-service-certificate')).toBeVisible();
    await expect.element(page.getByTestId('upload-management-service-certificate')).not.toBeDisabled();
  });
});

describe('0860 — CS TLS Certificates — clicking hash navigates to certificate details (Browser Mode)', () => {
  it('clicking the hash label navigates to the cert details route', async () => {
    const { router } = await renderRoute(TLS_CERTIFICATES_PATH, {
      permissions: allPermissions,
      msw: [certHandler()],
    });

    await expect.element(page.getByTestId('view-management-service-certificate')).toBeVisible();
    await page.getByTestId('view-management-service-certificate').click();

    expect(router.currentRoute.value.name).toBe('management-service-certificate-details');
  });
});

describe('0860 — CS TLS Certificates — download certificate trigger (Browser Mode)', () => {
  it('clicking download triggers the download endpoint', async () => {
    const downloadSpy = vi.fn();

    await renderRoute(TLS_CERTIFICATES_PATH, {
      permissions: allPermissions,
      msw: [
        certHandler(),
        specHttp.untyped.get(
          '/api/v1/management-services-configuration/download-certificate',
          () => {
            downloadSpy();
            return new HttpResponse(new Blob(['cert-bytes'], { type: 'application/gzip' }), {
              status: 200,
            });
          },
        ),
      ],
    });

    await expect.element(page.getByTestId('download-management-service-certificate')).toBeVisible();
    await page.getByTestId('download-management-service-certificate').click();

    expect(downloadSpy).toHaveBeenCalled();
  });
});

describe('0860 — CS TLS Certificates — regenerate key shows confirm dialog; hash updates (Browser Mode)', () => {
  it('clicking regenerate opens a confirm dialog; after confirm the hash in the table updates', async () => {
    let certCallCount = 0;
    await renderRoute(TLS_CERTIFICATES_PATH, {
      permissions: allPermissions,
      msw: [
        specHttp.get('/management-services-configuration/certificate', ({ response }) => {
          certCallCount += 1;
          return certCallCount === 1
            ? response(200).json(certDetails)
            : response(200).json(certDetailsAfterRegenerate);
        }),
        specHttp.post('/management-services-configuration/certificate', ({ response }) =>
          response(201).empty(),
        ),
      ],
    });

    await expect.element(page.getByTestId('view-management-service-certificate')).toBeVisible();
    await page.getByTestId('management-service-certificate-generateKey').click();

    await expect.element(
      page.getByTestId('generate-tls-and-certificate-dialog-confirmation-text'),
    ).toBeVisible();
    await page.getByTestId('dialog-save-button').click();

    await expect.element(page.getByTestId('view-management-service-certificate')).toHaveTextContent(
      'FF:EE:99:88:DD:CC:77:66',
    );
  });
});

describe('0860 — CS TLS Certificates — generate CSR dialog accepts DN and downloads (Browser Mode)', () => {
  it('clicking generate-CSR opens a dialog; entering a DN and confirming triggers the CSR download', async () => {
    const csrSpy = vi.fn();

    await renderRoute(TLS_CERTIFICATES_PATH, {
      permissions: allPermissions,
      msw: [
        certHandler(),
        specHttp.untyped.post(
          '/api/v1/management-services-configuration/generate-csr',
          () => {
            csrSpy();
            return new HttpResponse(new Blob(['csr-bytes'], { type: 'application/octet-stream' }), {
              status: 201,
            });
          },
        ),
      ],
    });

    await expect.element(page.getByTestId('management-service-certificate-generateCsr')).toBeVisible();
    await page.getByTestId('management-service-certificate-generateCsr').click();

    await expect.element(page.getByTestId('enter-distinguished-name')).toBeVisible();
    await page.getByTestId('enter-distinguished-name').getByRole('textbox').fill('CN=cs');

    await expect.element(page.getByTestId('dialog-save-button')).not.toBeDisabled();
    await page.getByTestId('dialog-save-button').click();

    // Dialog closes after successful CSR generation; wait for it to disappear
    await expect.element(page.getByTestId('enter-distinguished-name')).not.toBeInTheDocument();
    expect(csrSpy).toHaveBeenCalled();
  });
});

describe('0860 — CS TLS Certificates — uploading mismatched cert shows error (Browser Mode)', () => {
  it('uploading a certificate that does not match the TLS key renders an error message', async () => {
    await renderRoute(TLS_CERTIFICATES_PATH, {
      permissions: allPermissions,
      msw: [
        certHandler(),
        specHttp.untyped.post(
          '/api/v1/management-services-configuration/upload-certificate',
          () =>
            HttpResponse.json(
              {
                status: 400,
                error: { code: 'key_not_found' },
              },
              { status: 400 },
            ),
        ),
      ],
    });

    await expect.element(page.getByTestId('upload-management-service-certificate')).toBeVisible();
    await page.getByTestId('upload-management-service-certificate').click();

    const certFile = new File(['-----BEGIN CERTIFICATE-----\nbadcert\n-----END CERTIFICATE-----'], 'management-service-new.crt', {
      type: 'application/x-pem-file',
    });
    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
    await page.elementLocator(fileInput).upload(certFile);

    await expect.element(page.getByTestId('dialog-save-button')).not.toBeDisabled();
    await page.getByTestId('dialog-save-button').click();

    await expect.element(page.getByText('The imported certificate does not match the TLS key')).toBeVisible();
  });
});
