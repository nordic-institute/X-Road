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

const FUTURE_RENEWAL_TIME = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString();

const allPermissions = [
  Permissions.VIEW_DS_TLS_CERT,
  Permissions.DOWNLOAD_DS_TLS_CERT,
  Permissions.GENERATE_DS_TLS_KEY,
  Permissions.GENERATE_DS_TLS_CSR,
  Permissions.UPLOAD_DS_TLS_CERT,
  Permissions.ORDER_DS_TLS_CERT,
];

const permissionsWithoutOrder = [
  Permissions.VIEW_DS_TLS_CERT,
  Permissions.DOWNLOAD_DS_TLS_CERT,
  Permissions.GENERATE_DS_TLS_KEY,
  Permissions.GENERATE_DS_TLS_CSR,
  Permissions.UPLOAD_DS_TLS_CERT,
];

const sampleCertificate = {
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
  subject_alternative_names: 'DNS:ds.example.org',
};

function statusHandler(body: object) {
  return specHttp.untyped.get('/api/v1/ds-tls-certificate', () => HttpResponse.json(body));
}

function enrollmentStatusHandler(body: object) {
  return specHttp.untyped.get('/api/v1/ds-tls-certificate/enrollment-status', () =>
    HttpResponse.json({ enrollment_method: 'NONE', acme_available: false, ...body }),
  );
}

describe('SS DS TLS Certificate card — no key generated (Browser Mode)', () => {
  it('shows the key-not-generated text and the generate key button, no order button', async () => {
    await renderRoute(DS_TLS_CERTIFICATE_PATH, {
      permissions: allPermissions,
      msw: [
        statusHandler({ key_generated: false }),
        enrollmentStatusHandler({ acme_available: true, acme_cas: [{ name: 'Test CA' }], public_hostname: 'ds.example.org' }),
      ],
    });

    await expect.element(page.getByTestId('ds-tls-generate-key-button')).toBeVisible();
    await expect.element(page.getByTestId('ds-tls-key-not-generated')).toBeVisible();
    await expect.poll(() => page.getByTestId('ds-tls-order-certificate-button').query()).toBeNull();
  });

  it('generates a key', async () => {
    const generateSpy = vi.fn();

    await renderRoute(DS_TLS_CERTIFICATE_PATH, {
      permissions: allPermissions,
      msw: [
        statusHandler({ key_generated: false }),
        enrollmentStatusHandler({}),
        specHttp.untyped.post('/api/v1/ds-tls-certificate/key', () => {
          generateSpy();
          return new HttpResponse(null, { status: 201 });
        }),
      ],
    });

    await page.getByTestId('ds-tls-generate-key-button').click();
    await page.getByTestId('dialog-save-button').click();

    await expect.poll(() => generateSpy.mock.calls.length).toBeGreaterThan(0);
  });
});

describe('SS DS TLS Certificate card — key generated, certificate pending (Browser Mode)', () => {
  it('shows the pending text, offers CSR generation, order button hidden while ACME unavailable', async () => {
    const csrSpy = vi.fn();

    await renderRoute(DS_TLS_CERTIFICATE_PATH, {
      permissions: allPermissions,
      msw: [
        statusHandler({ key_generated: true }),
        enrollmentStatusHandler({ acme_available: false }),
        specHttp.untyped.post('/api/v1/ds-tls-certificate/csr', async ({ request }) => {
          csrSpy(await request.json());
          return HttpResponse.arrayBuffer(new ArrayBuffer(0), { status: 200 });
        }),
      ],
    });

    await expect.element(page.getByTestId('ds-tls-certificate-pending')).toBeVisible();
    await expect.element(page.getByTestId('ds-tls-generate-csr-button')).toBeVisible();
    await expect.poll(() => page.getByTestId('ds-tls-order-certificate-button').query()).toBeNull();

    await page.getByTestId('ds-tls-generate-csr-button').click();
    await page.getByTestId('ds-tls-csr-distinguished-name').getByRole('textbox').fill('CN=ds.example.org');
    await page.getByTestId('ds-tls-csr-subject-alt-name').getByRole('textbox').fill('ds.example.org');
    await page.getByTestId('dialog-save-button').click();

    await expect.poll(() => csrSpy.mock.calls.length).toBeGreaterThan(0);
    expect(csrSpy).toHaveBeenCalledWith({ name: 'CN=ds.example.org', subject_alt_name: 'ds.example.org' });
  });

  it('hides the order button when the key exists but the order authority is missing', async () => {
    await renderRoute(DS_TLS_CERTIFICATE_PATH, {
      permissions: permissionsWithoutOrder,
      msw: [
        statusHandler({ key_generated: true }),
        enrollmentStatusHandler({ acme_available: true, acme_cas: [{ name: 'Test CA' }], public_hostname: 'ds.example.org' }),
      ],
    });

    await expect.element(page.getByTestId('ds-tls-certificate-pending')).toBeVisible();
    await expect.poll(() => page.getByTestId('ds-tls-order-certificate-button').query()).toBeNull();
  });
});

describe('SS DS TLS Certificate card — manual certificate (Browser Mode)', () => {
  it('shows the certificate hash, the Manual chip and N/A renewal state', async () => {
    await renderRoute(DS_TLS_CERTIFICATE_PATH, {
      permissions: allPermissions,
      msw: [statusHandler({ key_generated: true, certificate: sampleCertificate }), enrollmentStatusHandler({ enrollment_method: 'MANUAL' })],
    });

    await expect.element(page.getByTestId('ds-tls-certificate-hash')).toBeVisible();
    await expect.element(page.getByTestId('ds-tls-enrollment-method')).toBeVisible();
    await expect.element(page.getByTestId('ds-tls-enrollment-method').getByText('Manual')).toBeVisible();
    await expect.element(page.getByTestId('ds-tls-renewal-na')).toBeVisible();
  });
});

describe('SS DS TLS Certificate card — ACME certificate with next renewal (Browser Mode)', () => {
  it('shows the ACME chip and the next planned renewal date', async () => {
    await renderRoute(DS_TLS_CERTIFICATE_PATH, {
      permissions: allPermissions,
      msw: [
        statusHandler({ key_generated: true, certificate: sampleCertificate }),
        enrollmentStatusHandler({ enrollment_method: 'ACME', next_renewal_time: FUTURE_RENEWAL_TIME }),
      ],
    });

    await expect.element(page.getByTestId('ds-tls-enrollment-method').getByText('ACME')).toBeVisible();
    await expect.element(page.getByTestId('ds-tls-renewal-next')).toBeVisible();
    await expect.element(page.getByText('Next planned renewal on', { exact: false })).toBeVisible();
  });
});

describe('SS DS TLS Certificate table — status and renewal columns (Browser Mode)', () => {
  it('shows the Status and Automatic Renewal headers and places the chip and renewal state in the certificate row', async () => {
    await renderRoute(DS_TLS_CERTIFICATE_PATH, {
      permissions: allPermissions,
      msw: [
        statusHandler({ key_generated: true, certificate: sampleCertificate }),
        enrollmentStatusHandler({ enrollment_method: 'ACME', next_renewal_time: FUTURE_RENEWAL_TIME }),
      ],
    });

    await expect.element(page.getByTestId('ds-tls-renewal-next')).toBeVisible();

    const headerCells = Array.from(document.querySelectorAll('thead th')).map((th) => th.textContent?.trim());
    expect(headerCells).toContain('Status');
    expect(headerCells).toContain('Automatic Renewal');

    const certificateRow = page.getByTestId('ds-tls-certificate-hash').query()?.closest('tr');
    const methodCell = page.getByTestId('ds-tls-enrollment-method').query()?.closest('td') as HTMLTableCellElement | undefined;
    const renewalCell = page.getByTestId('ds-tls-renewal-next').query()?.closest('td') as HTMLTableCellElement | undefined;

    expect(certificateRow).not.toBeNull();
    expect(methodCell?.closest('tr')).toBe(certificateRow);
    expect(renewalCell?.closest('tr')).toBe(certificateRow);
    expect(methodCell?.cellIndex).toBe(1);
    expect(renewalCell?.cellIndex).toBe(2);
  });
});

describe('SS DS TLS Certificate card — renewal error (Browser Mode)', () => {
  it('shows the renewal error text instead of the next renewal date', async () => {
    await renderRoute(DS_TLS_CERTIFICATE_PATH, {
      permissions: allPermissions,
      msw: [
        statusHandler({ key_generated: true, certificate: sampleCertificate }),
        enrollmentStatusHandler({
          enrollment_method: 'ACME',
          next_renewal_time: FUTURE_RENEWAL_TIME,
          last_error: 'ACME order failed: timeout',
        }),
      ],
    });

    await expect.element(page.getByTestId('ds-tls-enrollment-method').getByText('ACME')).toBeVisible();
    await expect.element(page.getByTestId('ds-tls-renewal-error')).toBeVisible();
    await expect.element(page.getByTestId('ds-tls-renewal-error').getByText('ACME order failed: timeout', { exact: false })).toBeVisible();
    await expect.poll(() => page.getByTestId('ds-tls-renewal-next').query()).toBeNull();
  });
});

describe('SS DS TLS Certificate card — certificate-only upload (Browser Mode)', () => {
  it('uploads a certificate without ever collecting a private key', async () => {
    const uploadSpy = vi.fn();

    await renderRoute(DS_TLS_CERTIFICATE_PATH, {
      permissions: allPermissions,
      msw: [
        statusHandler({ key_generated: true }),
        enrollmentStatusHandler({}),
        specHttp.untyped.post('/api/v1/ds-tls-certificate/certificate', async ({ request }) => {
          const body = await request.formData();
          uploadSpy(Array.from(body.keys()).sort());
          return HttpResponse.json(sampleCertificate, { status: 200 });
        }),
      ],
    });

    await expect.element(page.getByTestId('ds-tls-upload-certificate-button')).toBeVisible();
    await page.getByTestId('ds-tls-upload-certificate-button').click();

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

  it('uploading a certificate that does not match the DS TLS key renders an error message', async () => {
    await renderRoute(DS_TLS_CERTIFICATE_PATH, {
      permissions: allPermissions,
      msw: [
        statusHandler({ key_generated: true }),
        enrollmentStatusHandler({}),
        specHttp.untyped.post('/api/v1/ds-tls-certificate/certificate', () =>
          HttpResponse.json({ status: 400, error: { code: 'ds_tls_key_certificate_mismatch' } }, { status: 400 }),
        ),
      ],
    });

    await expect.element(page.getByTestId('ds-tls-upload-certificate-button')).toBeVisible();
    await page.getByTestId('ds-tls-upload-certificate-button').click();

    const certFile = new File(['-----BEGIN CERTIFICATE-----\nbadcert\n-----END CERTIFICATE-----'], 'ds-https.crt', {
      type: 'application/x-pem-file',
    });
    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
    await page.elementLocator(fileInput).upload(certFile);

    await expect.element(page.getByTestId('dialog-save-button')).not.toBeDisabled();
    await page.getByTestId('dialog-save-button').click();

    await expect.element(page.getByText('The uploaded certificate does not match the Dataspace TLS key')).toBeVisible();
  });
});

describe('SS DS TLS Certificate card — order button visibility (Browser Mode)', () => {
  it('shows the order button when ACME is available, a key exists and the authority is held', async () => {
    await renderRoute(DS_TLS_CERTIFICATE_PATH, {
      permissions: allPermissions,
      msw: [
        statusHandler({ key_generated: true }),
        enrollmentStatusHandler({ acme_available: true, acme_cas: [{ name: 'Test CA' }], public_hostname: 'ds.example.org' }),
      ],
    });

    await expect.element(page.getByTestId('ds-tls-order-certificate-button')).toBeVisible();
  });
});

describe('SS DS TLS Certificate card — order CA selection (Browser Mode)', () => {
  it('preselects the only ACME-capable CA', async () => {
    await renderRoute(DS_TLS_CERTIFICATE_PATH, {
      permissions: allPermissions,
      msw: [
        statusHandler({ key_generated: true }),
        enrollmentStatusHandler({ acme_available: true, acme_cas: [{ name: 'Test CA' }], public_hostname: 'ds.example.org' }),
      ],
    });

    await page.getByTestId('ds-tls-order-certificate-button').click();
    await expect.element(page.getByTestId('ds-tls-order-ca-select').getByText('Test CA')).toBeVisible();
  });

  it('lets the administrator choose between two ACME-capable CAs', async () => {
    await renderRoute(DS_TLS_CERTIFICATE_PATH, {
      permissions: allPermissions,
      msw: [
        statusHandler({ key_generated: true }),
        enrollmentStatusHandler({
          acme_available: true,
          acme_cas: [{ name: 'Test CA' }, { name: 'Other CA' }],
          public_hostname: 'ds.example.org',
        }),
      ],
    });

    await page.getByTestId('ds-tls-order-certificate-button').click();
    await page.getByTestId('ds-tls-order-ca-select').click();
    await expect.element(page.getByRole('option', { name: 'Test CA' })).toBeVisible();
    await expect.element(page.getByRole('option', { name: 'Other CA' })).toBeVisible();
    await page.getByRole('option', { name: 'Other CA' }).click();
    await expect.element(page.getByTestId('ds-tls-order-ca-select').getByText('Other CA')).toBeVisible();
  });
});

describe('SS DS TLS Certificate card — order happy path (Browser Mode)', () => {
  it('orders a certificate and refreshes the card to show ACME with the next renewal', async () => {
    const orderSpy = vi.fn();
    let ordered = false;

    await renderRoute(DS_TLS_CERTIFICATE_PATH, {
      permissions: allPermissions,
      msw: [
        statusHandler({ key_generated: true }),
        specHttp.untyped.get('/api/v1/ds-tls-certificate/enrollment-status', () =>
          HttpResponse.json(
            ordered
              ? { enrollment_method: 'ACME', acme_available: true, acme_cas: [{ name: 'Test CA' }], next_renewal_time: FUTURE_RENEWAL_TIME }
              : { enrollment_method: 'NONE', acme_available: true, acme_cas: [{ name: 'Test CA' }], public_hostname: 'ds.example.org' },
          ),
        ),
        specHttp.untyped.post('/api/v1/ds-tls-certificate/acme-order', async ({ request }) => {
          orderSpy(await request.json());
          ordered = true;
          return HttpResponse.json(sampleCertificate, { status: 200 });
        }),
      ],
    });

    await page.getByTestId('ds-tls-order-certificate-button').click();
    await page.getByTestId('ds-tls-order-distinguished-name').getByRole('textbox').fill('CN=ds.example.org');
    await page.getByTestId('ds-tls-order-subject-alt-name').getByRole('textbox').fill('ds.example.org');
    await page.getByTestId('dialog-save-button').click();

    await expect.poll(() => orderSpy.mock.calls.length).toBeGreaterThan(0);
    expect(orderSpy).toHaveBeenCalledWith({
      ca_name: 'Test CA',
      distinguished_name: 'CN=ds.example.org',
      subject_alt_name: 'ds.example.org',
    });

    await expect.element(page.getByTestId('ds-tls-enrollment-method').getByText('ACME')).toBeVisible();
    await expect.element(page.getByTestId('ds-tls-renewal-next')).toBeVisible();
  });
});

describe('SS DS TLS Certificate card — order error path (Browser Mode)', () => {
  it('shows the actionable error inside the order dialog on failure', async () => {
    await renderRoute(DS_TLS_CERTIFICATE_PATH, {
      permissions: allPermissions,
      msw: [
        statusHandler({ key_generated: true }),
        enrollmentStatusHandler({ acme_available: true, acme_cas: [{ name: 'Test CA' }], public_hostname: 'ds.example.org' }),
        specHttp.untyped.post('/api/v1/ds-tls-certificate/acme-order', () =>
          HttpResponse.json({ status: 400, error: { code: 'ds_tls_ca_not_found' } }, { status: 400 }),
        ),
      ],
    });

    await page.getByTestId('ds-tls-order-certificate-button').click();
    await page.getByTestId('ds-tls-order-distinguished-name').getByRole('textbox').fill('CN=ds.example.org');
    await page.getByTestId('ds-tls-order-subject-alt-name').getByRole('textbox').fill('ds.example.org');
    await page.getByTestId('dialog-save-button').click();

    await expect
      .element(page.getByText('The named certification authority is not a designated, ACME-capable Dataspace TLS CA', { exact: false }))
      .toBeVisible();
  });
});
