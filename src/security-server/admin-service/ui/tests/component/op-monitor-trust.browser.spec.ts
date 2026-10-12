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

const OP_MONITOR_TRUST_PATH = '/keys/op-monitor-trust';

const viewAndDownload = [Permissions.VIEW_KEYS, Permissions.VIEW_INTERNAL_RPC_CA, Permissions.DOWNLOAD_INTERNAL_RPC_CA];
const viewOnly = [Permissions.VIEW_KEYS, Permissions.VIEW_INTERNAL_RPC_CA];

const HASH = 'AABB1122CCDD3344AABB1122CCDD3344AABB1122CCDD3344AABB1122CCDD3344';
const FINGERPRINT = HASH.match(/.{2}/g)!.join(':');

const internalRpcCa: CertificateDetails = {
  hash: HASH,
  issuer_common_name: 'Internal RPC CA',
  issuer_distinguished_name: 'CN=Internal RPC CA, O=X-Road',
  subject_common_name: 'Internal RPC CA',
  subject_distinguished_name: 'CN=Internal RPC CA, O=X-Road',
  serial: '1',
  version: 3,
  signature: 'abc123',
  signature_algorithm: 'SHA256withRSA',
  public_key_algorithm: 'RSA',
  rsa_public_key_exponent: 65537,
  rsa_public_key_modulus: 'deadbeef',
  not_before: '2024-01-01T00:00:00.000Z',
  not_after: '2034-01-01T00:00:00.000Z',
  key_usages: [],
  subject_alternative_names: '',
};

const detailsHandler = specHttp.get('/system/internal-rpc-ca', ({ response }) => response(200).json(internalRpcCa));

describe('SS Op-monitor Trust tab — internal RPC CA (Browser Mode)', () => {
  it('shows the subject, issuer, SHA-256 fingerprint and validity', async () => {
    await renderRoute(OP_MONITOR_TRUST_PATH, { permissions: viewAndDownload, msw: [detailsHandler] });

    await expect.element(page.getByTestId('internal-rpc-ca-subject')).toHaveTextContent('CN=Internal RPC CA, O=X-Road');
    await expect.element(page.getByTestId('internal-rpc-ca-issuer')).toHaveTextContent('CN=Internal RPC CA, O=X-Road');
    await expect.element(page.getByTestId('internal-rpc-ca-fingerprint')).toHaveTextContent(FINGERPRINT);
    await expect.element(page.getByTestId('internal-rpc-ca-not-before')).toBeVisible();
    await expect.element(page.getByTestId('internal-rpc-ca-not-after')).toBeVisible();
  });

  it('names the operational monitoring daemon (op-monitor) in the description', async () => {
    await renderRoute(OP_MONITOR_TRUST_PATH, { permissions: viewAndDownload, msw: [detailsHandler] });

    await expect.element(page.getByText('operational monitoring daemon (op-monitor)', { exact: false })).toBeVisible();
  });

  it('downloads the internal RPC CA', async () => {
    const downloadSpy = vi.fn();
    const savedFileNames: string[] = [];
    const anchorClick = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      savedFileNames.push(this.download);
    });

    await renderRoute(OP_MONITOR_TRUST_PATH, {
      permissions: viewAndDownload,
      msw: [
        detailsHandler,
        specHttp.untyped.get('/api/v1/system/internal-rpc-ca/download', () => {
          downloadSpy();
          return new HttpResponse('-----BEGIN CERTIFICATE-----\nAA==\n-----END CERTIFICATE-----\n', {
            headers: {
              'Content-Type': 'application/x-pem-file',
              'Content-Disposition': 'attachment; filename="internal-rpc-ca.pem"',
            },
          });
        }),
      ],
    });

    await page.getByTestId('download-internal-rpc-ca-button').click();

    await expect.poll(() => downloadSpy.mock.calls.length).toBeGreaterThan(0);
    await expect.poll(() => savedFileNames).toEqual(['internal-rpc-ca.pem']);
    anchorClick.mockRestore();
  });

  it('hides the download button without the download permission', async () => {
    await renderRoute(OP_MONITOR_TRUST_PATH, { permissions: viewOnly, msw: [detailsHandler] });

    await expect.element(page.getByTestId('internal-rpc-ca-subject')).toBeVisible();
    await expect.poll(() => page.getByTestId('download-internal-rpc-ca-button').query()).toBeNull();
  });

  it('shows a not-available state and disables the download when the internal RPC CA is not available', async () => {
    await renderRoute(OP_MONITOR_TRUST_PATH, {
      permissions: viewAndDownload,
      msw: [
        specHttp.untyped.get('/api/v1/system/internal-rpc-ca', () =>
          HttpResponse.json({ status: 404, error: { code: 'internal_rpc_ca_not_available' } }, { status: 404 }),
        ),
      ],
    });

    await expect.element(page.getByTestId('internal-rpc-ca-unavailable')).toBeVisible();
    await expect.element(page.getByTestId('download-internal-rpc-ca-button')).toBeDisabled();
    await expect.poll(() => page.getByTestId('internal-rpc-ca-details').query()).toBeNull();
  });

  it('shows the not-available state when loading the internal RPC CA fails', async () => {
    await renderRoute(OP_MONITOR_TRUST_PATH, {
      permissions: viewAndDownload,
      msw: [
        specHttp.untyped.get('/api/v1/system/internal-rpc-ca', () =>
          HttpResponse.json({ status: 500, error: { code: 'internal_error' } }, { status: 500 }),
        ),
      ],
    });

    await expect.element(page.getByTestId('internal-rpc-ca-unavailable')).toBeVisible();
    await expect.element(page.getByTestId('download-internal-rpc-ca-button')).toBeDisabled();
  });
});

const opMonitorCaViewOnly = [Permissions.VIEW_KEYS, Permissions.VIEW_INTERNAL_RPC_CA, Permissions.VIEW_OP_MONITOR_CA];
const opMonitorCaViewAndUpload = [...opMonitorCaViewOnly, Permissions.UPLOAD_OP_MONITOR_CA];
const OP_MONITOR_HASH = '1122334455667788112233445566778811223344556677881122334455667788';
const OP_MONITOR_FINGERPRINT = OP_MONITOR_HASH.match(/.{2}/g)!.join(':');
const opMonitorCa: CertificateDetails = {
  ...internalRpcCa,
  hash: OP_MONITOR_HASH,
  issuer_distinguished_name: 'CN=Op-monitor Root CA, O=Other Org',
  subject_distinguished_name: 'CN=Op-monitor CA, O=Other Org',
  not_before: '2025-02-03T00:00:00.000Z',
  not_after: '2035-02-03T00:00:00.000Z',
};
const storedOpMonitorCaHandler = specHttp.get('/system/op-monitor-ca', ({ response }) => response(200).json(opMonitorCa));
const noOpMonitorCaHandler = specHttp.get('/system/op-monitor-ca', ({ response }) => response(204).empty());
const rejectionMessages: Record<string, string> = {
  ca_cert_empty: 'The file is empty',
  ca_cert_not_a_certificate: 'The file is not a PEM or DER encoded certificate',
  ca_cert_multiple_certificates:
    'The file contains more than one certificate. Upload only the CA certificate that issued the certificate of the operational monitoring daemon (op-monitor)',
  ca_cert_key_material: 'The file contains private key material. Upload the CA certificate only',
  ca_cert_not_a_ca: 'The certificate is not a CA certificate',
  ca_cert_expired: 'The CA certificate has expired',
  ca_cert_not_yet_valid: 'The CA certificate is not yet valid',
};
async function chooseOpMonitorCaFile(content = 'file content') {
  const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
  await page.elementLocator(fileInput).upload(new File([content], 'op-monitor-ca.pem', { type: 'application/x-pem-file' }));
}
describe('SS Op-monitor Trust tab — op-monitor CA (Browser Mode)', () => {
  it('shows an empty state, the restart hint and an upload action when no op-monitor CA is stored', async () => {
    await renderRoute(OP_MONITOR_TRUST_PATH, {
      permissions: opMonitorCaViewAndUpload,
      msw: [detailsHandler, noOpMonitorCaHandler],
    });
    await expect.element(page.getByTestId('op-monitor-ca-empty')).toBeVisible();
    await expect.element(page.getByTestId('op-monitor-ca-restart-hint')).toHaveTextContent('Restart proxy and admin-service to apply');
    await expect.poll(() => page.getByTestId('upload-op-monitor-ca-button').element().textContent).toContain('Upload');
    await expect.poll(() => page.getByTestId('op-monitor-ca-details').query()).toBeNull();
  });
  it('shows the subject, issuer, SHA-256 fingerprint and validity of the stored op-monitor CA with a replace action', async () => {
    await renderRoute(OP_MONITOR_TRUST_PATH, {
      permissions: opMonitorCaViewAndUpload,
      msw: [detailsHandler, storedOpMonitorCaHandler],
    });
    await expect.element(page.getByTestId('op-monitor-ca-subject')).toHaveTextContent('CN=Op-monitor CA, O=Other Org');
    await expect.element(page.getByTestId('op-monitor-ca-issuer')).toHaveTextContent('CN=Op-monitor Root CA, O=Other Org');
    await expect.element(page.getByTestId('op-monitor-ca-fingerprint')).toHaveTextContent(OP_MONITOR_FINGERPRINT);
    await expect.element(page.getByTestId('op-monitor-ca-not-before')).toBeVisible();
    await expect.element(page.getByTestId('op-monitor-ca-not-after')).toBeVisible();
    await expect.poll(() => page.getByTestId('upload-op-monitor-ca-button').element().textContent).toContain('Replace');
    await expect.poll(() => page.getByTestId('op-monitor-ca-empty').query()).toBeNull();
  });
  it('uploads the file as the certificate part and shows the stored op-monitor CA', async () => {
    const uploadSpy = vi.fn();
    await renderRoute(OP_MONITOR_TRUST_PATH, {
      permissions: opMonitorCaViewAndUpload,
      msw: [
        detailsHandler,
        noOpMonitorCaHandler,
        specHttp.untyped.post('/api/v1/system/op-monitor-ca', async ({ request }) => {
          const body = await request.formData();
          uploadSpy(Array.from(body.keys()));
          return HttpResponse.json(opMonitorCa, { status: 200 });
        }),
      ],
    });
    await expect.element(page.getByTestId('op-monitor-ca-empty')).toBeVisible();
    await chooseOpMonitorCaFile();
    await expect.poll(() => uploadSpy.mock.calls.length).toBeGreaterThan(0);
    expect(uploadSpy).toHaveBeenCalledWith(['certificate']);
    await expect.element(page.getByTestId('op-monitor-ca-fingerprint')).toHaveTextContent(OP_MONITOR_FINGERPRINT);
    await expect.poll(() => page.getByTestId('op-monitor-ca-empty').query()).toBeNull();
  });
  for (const [code, message] of Object.entries(rejectionMessages)) {
    it(`shows a readable message for the rejection ${code}`, async () => {
      await renderRoute(OP_MONITOR_TRUST_PATH, {
        permissions: opMonitorCaViewAndUpload,
        msw: [
          detailsHandler,
          noOpMonitorCaHandler,
          specHttp.untyped.post('/api/v1/system/op-monitor-ca', () => HttpResponse.json({ status: 400, error: { code } }, { status: 400 })),
        ],
      });
      await expect.element(page.getByTestId('op-monitor-ca-empty')).toBeVisible();
      await chooseOpMonitorCaFile();
      await expect.element(page.getByText(message)).toBeVisible();
    });
  }
  it('hides the upload action without the upload permission', async () => {
    await renderRoute(OP_MONITOR_TRUST_PATH, {
      permissions: opMonitorCaViewOnly,
      msw: [detailsHandler, storedOpMonitorCaHandler],
    });
    await expect.element(page.getByTestId('op-monitor-ca-subject')).toBeVisible();
    await expect.poll(() => page.getByTestId('upload-op-monitor-ca-button').query()).toBeNull();
  });
  it('hides the op-monitor CA card without the view permission', async () => {
    await renderRoute(OP_MONITOR_TRUST_PATH, { permissions: viewOnly, msw: [detailsHandler] });
    await expect.element(page.getByTestId('internal-rpc-ca-subject')).toBeVisible();
    await expect.poll(() => page.getByTestId('op-monitor-ca-restart-hint').query()).toBeNull();
    await expect.poll(() => page.getByTestId('upload-op-monitor-ca-button').query()).toBeNull();
  });
});
