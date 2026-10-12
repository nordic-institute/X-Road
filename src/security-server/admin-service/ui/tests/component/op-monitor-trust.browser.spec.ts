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
