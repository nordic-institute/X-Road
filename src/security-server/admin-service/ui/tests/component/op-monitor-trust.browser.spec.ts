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
import { Permissions, RouteName } from '@/global';
import type { CertificateDetails } from '@/openapi-types';

const OP_MONITOR_TRUST_PATH = '/keys/op-monitor-trust';

const viewAndDownload = [Permissions.VIEW_KEYS, Permissions.VIEW_EXTERNAL_RPC_CA, Permissions.DOWNLOAD_EXTERNAL_RPC_CA];
const viewOnly = [Permissions.VIEW_KEYS, Permissions.VIEW_EXTERNAL_RPC_CA];

const HASH = 'AABB1122CCDD3344AABB1122CCDD3344AABB1122CCDD3344AABB1122CCDD3344';
const FINGERPRINT = HASH.match(/.{2}/g)!.join(':');

const externalRpcCa: CertificateDetails = {
  hash: HASH,
  issuer_common_name: 'External RPC CA',
  issuer_distinguished_name: 'CN=External RPC CA, O=X-Road',
  subject_common_name: 'External RPC CA',
  subject_distinguished_name: 'CN=External RPC CA, O=X-Road',
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

const summaryLabels = ['Subject Distinguished Name', 'Issuer Distinguished Name', 'Not Before', 'Not After'];
const expectNoInlineSummary = async (...distinguishedNames: string[]) => {
  for (const text of [...summaryLabels, ...distinguishedNames]) {
    await expect.poll(() => page.getByText(text).query()).toBeNull();
  }
};

const cardTitles = () => Array.from(document.querySelectorAll('[data-test="view-title-text"]')).map((el) => el.textContent?.trim());

const detailsHandler = specHttp.get('/system/external-rpc-ca', ({ response }) => response(200).json(externalRpcCa));

describe('SS Operational Monitoring Trust tab — Security Server RPC CA (Browser Mode)', () => {
  it('titles the tab and the page Operational Monitoring Trust', async () => {
    await renderRoute(OP_MONITOR_TRUST_PATH, { permissions: viewAndDownload, msw: [detailsHandler] });
    await expect.poll(() => page.getByTestId('op-monitor-trust-tab-button').element().textContent).toContain('Operational Monitoring Trust');
    await expect.poll(() => document.querySelector('header.view-header .title-view')?.textContent?.trim()).toBe('Operational Monitoring Trust');
  });
  it('titles the Security Server RPC CA card', async () => {
    await renderRoute(OP_MONITOR_TRUST_PATH, { permissions: viewAndDownload, msw: [detailsHandler] });
    await expect.poll(() => cardTitles()).toEqual(['Security Server RPC CA']);
  });
  it('shows the SHA-256 fingerprint as a hash row and no inline subject, issuer or validity', async () => {
    await renderRoute(OP_MONITOR_TRUST_PATH, { permissions: viewAndDownload, msw: [detailsHandler] });
    await expect.element(page.getByTestId('external-rpc-ca-hash')).toMatchTextContent(FINGERPRINT);
    await expectNoInlineSummary('CN=External RPC CA, O=X-Road');
  });
  it('opens the full certificate details when the hash is clicked', async () => {
    const { router } = await renderRoute(OP_MONITOR_TRUST_PATH, { permissions: viewAndDownload, msw: [detailsHandler] });
    await page.getByTestId('external-rpc-ca-hash').click();
    await expect.poll(() => router.currentRoute.value.name).toBe(RouteName.ExternalRpcCaDetails);
    await expect.element(page.getByTestId('xrd-certificate')).toBeVisible();
    await expect.poll(() => page.getByTestId('xrd-certificate').element().textContent).toContain('CN=External RPC CA, O=X-Road');
    await expect.poll(() => page.getByTestId('xrd-certificate').element().textContent).toContain(FINGERPRINT);
  });
  it('returns to the tab without an error when the Security Server RPC CA details are opened and the CA is not available', async () => {
    const { router } = await renderRoute('/external-rpc-ca-details', {
      permissions: viewAndDownload,
      msw: [
        specHttp.untyped.get('/api/v1/system/external-rpc-ca', () =>
          HttpResponse.json({ status: 404, error: { code: 'external_rpc_ca_not_available' } }, { status: 404 }),
        ),
      ],
    });
    await expect.poll(() => router.currentRoute.value.name).toBe(RouteName.OpMonitorTrust);
    await expect.element(page.getByTestId('external-rpc-ca-unavailable')).toBeVisible();
    await expect.poll(() => page.getByTestId('xrd-certificate').query()).toBeNull();
    await expect.poll(() => page.getByTestId('contextual-alert').query()).toBeNull();
  });
  it('describes installing the Security Server RPC CA on the operational monitoring daemon host', async () => {
    await renderRoute(OP_MONITOR_TRUST_PATH, { permissions: viewAndDownload, msw: [detailsHandler] });
    await expect.element(page.getByText('install it on the operational monitoring daemon host', { exact: false })).toBeVisible();
  });
  it('downloads the Security Server RPC CA', async () => {
    const downloadSpy = vi.fn();
    const savedFileNames: string[] = [];
    const anchorClick = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      savedFileNames.push(this.download);
    });

    await renderRoute(OP_MONITOR_TRUST_PATH, {
      permissions: viewAndDownload,
      msw: [
        detailsHandler,
        specHttp.untyped.get('/api/v1/system/external-rpc-ca/download', () => {
          downloadSpy();
          return new HttpResponse('-----BEGIN CERTIFICATE-----\nAA==\n-----END CERTIFICATE-----\n', {
            headers: {
              'Content-Type': 'application/x-pem-file',
              'Content-Disposition': 'attachment; filename="external-rpc-ca.pem"',
            },
          });
        }),
      ],
    });

    await page.getByTestId('download-external-rpc-ca-button').click();

    await expect.poll(() => downloadSpy.mock.calls.length).toBeGreaterThan(0);
    await expect.poll(() => savedFileNames).toEqual(['external-rpc-ca.pem']);
    anchorClick.mockRestore();
  });

  it('hides the download button without the download permission', async () => {
    await renderRoute(OP_MONITOR_TRUST_PATH, { permissions: viewOnly, msw: [detailsHandler] });

    await expect.element(page.getByTestId('external-rpc-ca-hash')).toBeVisible();
    await expect.poll(() => page.getByTestId('download-external-rpc-ca-button').query()).toBeNull();
  });

  it('shows a not-available state and disables the download when the Security Server RPC CA is not available', async () => {
    await renderRoute(OP_MONITOR_TRUST_PATH, {
      permissions: viewAndDownload,
      msw: [
        specHttp.untyped.get('/api/v1/system/external-rpc-ca', () =>
          HttpResponse.json({ status: 404, error: { code: 'external_rpc_ca_not_available' } }, { status: 404 }),
        ),
      ],
    });

    await expect.element(page.getByTestId('external-rpc-ca-unavailable')).toBeVisible();
    await expect.element(page.getByTestId('download-external-rpc-ca-button')).toBeDisabled();
    await expect.poll(() => page.getByTestId('external-rpc-ca-hash').query()).toBeNull();
  });

  it('shows the not-available state when loading the Security Server RPC CA fails', async () => {
    await renderRoute(OP_MONITOR_TRUST_PATH, {
      permissions: viewAndDownload,
      msw: [
        specHttp.untyped.get('/api/v1/system/external-rpc-ca', () =>
          HttpResponse.json({ status: 500, error: { code: 'internal_error' } }, { status: 500 }),
        ),
      ],
    });

    await expect.element(page.getByTestId('external-rpc-ca-unavailable')).toBeVisible();
    await expect.element(page.getByTestId('download-external-rpc-ca-button')).toBeDisabled();
  });
});

const opMonitorCaViewOnly = [Permissions.VIEW_KEYS, Permissions.VIEW_EXTERNAL_RPC_CA, Permissions.VIEW_OP_MONITOR_CA];
const opMonitorCaViewAndUpload = [...opMonitorCaViewOnly, Permissions.UPLOAD_OP_MONITOR_CA];
const opMonitorCaViewAndDelete = [...opMonitorCaViewOnly, Permissions.DELETE_OP_MONITOR_CA];
const OP_MONITOR_HASH = '1122334455667788112233445566778811223344556677881122334455667788';
const OP_MONITOR_FINGERPRINT = OP_MONITOR_HASH.match(/.{2}/g)!.join(':');
const opMonitorCa: CertificateDetails = {
  ...externalRpcCa,
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
    'The file contains more than one certificate. Upload only the CA certificate that issues the certificate',
  ca_cert_key_material: 'The file contains private key material. Upload the CA certificate only',
  ca_cert_not_a_ca: 'The certificate is not a CA certificate',
  ca_cert_unsupported_key_algorithm: 'The CA certificate public key must be RSA or EC',
  ca_cert_expired: 'The CA certificate has expired',
  ca_cert_not_yet_valid: 'The CA certificate is not yet valid',
};
async function chooseOpMonitorCaFile(content = 'file content') {
  const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
  await page.elementLocator(fileInput).upload(new File([content], 'op-monitor-ca.pem', { type: 'application/x-pem-file' }));
}
describe('SS Operational Monitoring Trust tab — op-monitor CA (Browser Mode)', () => {
  it('shows an empty state and an upload action when no op-monitor CA is stored', async () => {
    await renderRoute(OP_MONITOR_TRUST_PATH, {
      permissions: opMonitorCaViewAndUpload,
      msw: [detailsHandler, noOpMonitorCaHandler],
    });
    await expect.element(page.getByTestId('op-monitor-ca-empty')).toBeVisible();
    await expect.poll(() => page.getByTestId('upload-op-monitor-ca-button').element().textContent).toContain('Upload');
    await expect.poll(() => page.getByTestId('op-monitor-ca-hash').query()).toBeNull();
  });
  it('titles the Operational Monitoring CA card', async () => {
    await renderRoute(OP_MONITOR_TRUST_PATH, {
      permissions: opMonitorCaViewAndUpload,
      msw: [detailsHandler, storedOpMonitorCaHandler],
    });
    await expect.poll(() => cardTitles()).toEqual(['Security Server RPC CA', 'Operational Monitoring CA']);
  });
  it('shows the SHA-256 fingerprint of the stored op-monitor CA as a hash row, with no inline details and a replace action', async () => {
    await renderRoute(OP_MONITOR_TRUST_PATH, {
      permissions: opMonitorCaViewAndUpload,
      msw: [detailsHandler, storedOpMonitorCaHandler],
    });
    await expect.poll(() => page.getByTestId('op-monitor-ca-hash').element().textContent).toContain(OP_MONITOR_FINGERPRINT);
    await expectNoInlineSummary('CN=Op-monitor CA, O=Other Org', 'CN=Op-monitor Root CA, O=Other Org');
    await expect.poll(() => page.getByTestId('upload-op-monitor-ca-button').element().textContent).toContain('Replace');
    await expect.poll(() => page.getByTestId('op-monitor-ca-empty').query()).toBeNull();
  });
  it('opens the full certificate details of the op-monitor CA when the hash is clicked', async () => {
    const { router } = await renderRoute(OP_MONITOR_TRUST_PATH, {
      permissions: opMonitorCaViewOnly,
      msw: [detailsHandler, storedOpMonitorCaHandler],
    });
    await page.getByTestId('op-monitor-ca-hash').click();
    await expect.poll(() => router.currentRoute.value.name).toBe(RouteName.OpMonitorCaDetails);
    await expect.element(page.getByTestId('xrd-certificate')).toBeVisible();
    await expect.poll(() => page.getByTestId('xrd-certificate').element().textContent).toContain('CN=Op-monitor CA, O=Other Org');
    await expect.poll(() => page.getByTestId('xrd-certificate').element().textContent).toContain('CN=Op-monitor Root CA, O=Other Org');
    await expect.poll(() => page.getByTestId('xrd-certificate').element().textContent).toContain(OP_MONITOR_FINGERPRINT);
  });
  it('returns to the tab without an error when the op-monitor CA details are opened and no CA is stored', async () => {
    const { router } = await renderRoute('/op-monitor-ca-details', {
      permissions: opMonitorCaViewOnly,
      msw: [detailsHandler, noOpMonitorCaHandler],
    });
    await expect.poll(() => router.currentRoute.value.name).toBe(RouteName.OpMonitorTrust);
    await expect.element(page.getByTestId('op-monitor-ca-empty')).toBeVisible();
    await expect.poll(() => page.getByTestId('xrd-certificate').query()).toBeNull();
    await expect.poll(() => page.getByTestId('contextual-alert').query()).toBeNull();
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
    await expect.poll(() => page.getByTestId('op-monitor-ca-hash').element().textContent).toContain(OP_MONITOR_FINGERPRINT);
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
  for (const [outcome, status] of [
    ['succeeds', 200],
    ['fails', 500],
  ] as const) {
    it(`disables the upload action until the first read of the op-monitor CA ${outcome}`, async () => {
      let releaseRead: () => void = () => {};
      const readGate = new Promise<void>((resolve) => (releaseRead = resolve));
      await renderRoute(OP_MONITOR_TRUST_PATH, {
        permissions: opMonitorCaViewAndUpload,
        msw: [
          detailsHandler,
          specHttp.untyped.get('/api/v1/system/op-monitor-ca', async () => {
            await readGate;
            return status === 200
              ? HttpResponse.json(opMonitorCa, { status })
              : HttpResponse.json({ status, error: { code: 'internal_error' } }, { status });
          }),
        ],
      });
      await expect.element(page.getByTestId('upload-op-monitor-ca-button')).toBeDisabled();
      releaseRead();
      await expect.element(page.getByTestId('upload-op-monitor-ca-button')).toBeEnabled();
    });
  }
  it('hides the upload action without the upload permission', async () => {
    await renderRoute(OP_MONITOR_TRUST_PATH, {
      permissions: opMonitorCaViewOnly,
      msw: [detailsHandler, storedOpMonitorCaHandler],
    });
    await expect.element(page.getByTestId('op-monitor-ca-hash')).toBeVisible();
    await expect.poll(() => page.getByTestId('upload-op-monitor-ca-button').query()).toBeNull();
  });
  it('asks for confirmation before deleting and shows the empty state afterwards', async () => {
    const deleteSpy = vi.fn();
    await renderRoute(OP_MONITOR_TRUST_PATH, {
      permissions: opMonitorCaViewAndDelete,
      msw: [
        detailsHandler,
        storedOpMonitorCaHandler,
        specHttp.untyped.delete('/api/v1/system/op-monitor-ca', () => {
          deleteSpy();
          return new HttpResponse(null, { status: 204 });
        }),
      ],
    });
    await expect.element(page.getByTestId('op-monitor-ca-hash')).toBeVisible();
    await page.getByTestId('delete-op-monitor-ca-button').click();
    await expect.element(page.getByTestId('dialog-title')).toHaveTextContent('Delete Operational Monitoring CA?');
    expect(deleteSpy).not.toHaveBeenCalled();
    await page.getByTestId('dialog-save-button').click();
    await expect.poll(() => deleteSpy.mock.calls.length).toBe(1);
    await expect.element(page.getByTestId('op-monitor-ca-empty')).toBeVisible();
    await expect.poll(() => page.getByTestId('op-monitor-ca-hash').query()).toBeNull();
    await expect.poll(() => page.getByTestId('delete-op-monitor-ca-button').query()).toBeNull();
  });
  it('keeps the stored op-monitor CA when the delete confirmation is cancelled', async () => {
    const deleteSpy = vi.fn();
    await renderRoute(OP_MONITOR_TRUST_PATH, {
      permissions: opMonitorCaViewAndDelete,
      msw: [
        detailsHandler,
        storedOpMonitorCaHandler,
        specHttp.untyped.delete('/api/v1/system/op-monitor-ca', () => {
          deleteSpy();
          return new HttpResponse(null, { status: 204 });
        }),
      ],
    });
    await expect.element(page.getByTestId('op-monitor-ca-hash')).toBeVisible();
    await page.getByTestId('delete-op-monitor-ca-button').click();
    await page.getByTestId('dialog-cancel-button').click();
    await expect.poll(() => page.getByTestId('dialog-title').query()).toBeNull();
    expect(deleteSpy).not.toHaveBeenCalled();
    await expect.element(page.getByTestId('op-monitor-ca-hash')).toBeVisible();
  });
  it('hides the delete action without the delete permission', async () => {
    await renderRoute(OP_MONITOR_TRUST_PATH, {
      permissions: opMonitorCaViewAndUpload,
      msw: [detailsHandler, storedOpMonitorCaHandler],
    });
    await expect.element(page.getByTestId('op-monitor-ca-hash')).toBeVisible();
    await expect.poll(() => page.getByTestId('delete-op-monitor-ca-button').query()).toBeNull();
  });
  it('does not offer delete when no op-monitor CA is stored', async () => {
    await renderRoute(OP_MONITOR_TRUST_PATH, {
      permissions: opMonitorCaViewAndDelete,
      msw: [detailsHandler, noOpMonitorCaHandler],
    });
    await expect.element(page.getByTestId('op-monitor-ca-empty')).toBeVisible();
    await expect.poll(() => page.getByTestId('delete-op-monitor-ca-button').query()).toBeNull();
  });
  it('hides the op-monitor CA card without the view permission', async () => {
    await renderRoute(OP_MONITOR_TRUST_PATH, { permissions: viewOnly, msw: [detailsHandler] });
    await expect.element(page.getByTestId('external-rpc-ca-hash')).toBeVisible();
    await expect.poll(() => page.getByTestId('op-monitor-ca-description').query()).toBeNull();
    await expect.poll(() => page.getByTestId('upload-op-monitor-ca-button').query()).toBeNull();
  });
});
