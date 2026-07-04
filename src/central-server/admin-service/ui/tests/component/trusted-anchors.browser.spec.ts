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
import type { TrustedAnchor } from '@/openapi-types';

const TRUSTED_ANCHORS_PATH = '/global-configuration/trusted-anchors';

const ANCHOR_CS2: TrustedAnchor = {
  hash: 'aabbcc112233',
  generated_at: '2024-03-01T10:00:00Z',
  instance_identifier: 'CS2-E2E',
};

const basePermissions = [
  Permissions.UPLOAD_TRUSTED_ANCHOR,
  Permissions.DOWNLOAD_TRUSTED_ANCHOR,
  Permissions.DELETE_TRUSTED_ANCHOR,
];

describe('0850 — CS Trusted Anchors — upload anchor + confirm dialog -> appears in list (Browser Mode)', () => {
  it('uploading an anchor file shows confirm dialog; after confirm the anchor appears in the list', async () => {
    let anchorsCallCount = 0;
    await renderRoute(TRUSTED_ANCHORS_PATH, {
      permissions: basePermissions,
      msw: [
        specHttp.get('/trusted-anchors', ({ response }) => {
          anchorsCallCount += 1;
          return anchorsCallCount === 1
            ? response(200).json([])
            : response(200).json([ANCHOR_CS2]);
        }),
        specHttp.post('/trusted-anchors/preview', ({ response }) => response(200).json(ANCHOR_CS2)),
        specHttp.post('/trusted-anchors', ({ response }) => response(201).json(ANCHOR_CS2)),
      ],
    });

    // No anchors in initial list
    expect(page.getByTestId('anchor').query()).toBeNull();

    // Upload button is visible
    const uploadBtn = page.getByTestId('upload-anchor-button');
    await expect.element(uploadBtn).toBeVisible();
    await uploadBtn.click();

    // Trigger file selection via the hidden file input (XrdFileUpload)
    const xmlFile = new File(['<anchor/>'], 'trusted-anchor-CS2-E2E.xml', { type: 'application/xml' });
    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
    await page.elementLocator(fileInput).upload(xmlFile);

    // Confirm dialog shows with the anchor hash from the preview response
    await expect.element(page.getByTestId('dialog-save-button')).toBeVisible();
    await page.getByTestId('dialog-save-button').click();

    // After upload the anchor CS2-E2E appears in the list
    await expect.element(page.getByTestId('anchor')).toBeVisible();
    await expect.element(page.getByTestId('anchor-hash')).toHaveTextContent('aabbcc112233');
  });
});

describe('0850 — CS Trusted Anchors — download anchor trigger (Browser Mode)', () => {
  it('download button is visible for a trusted anchor and triggers the download action', async () => {
    const downloadSpy = vi.fn();
    await renderRoute(TRUSTED_ANCHORS_PATH, {
      permissions: basePermissions,
      msw: [
        specHttp.get('/trusted-anchors', ({ response }) => response(200).json([ANCHOR_CS2])),
        specHttp.untyped.get('/api/v1/trusted-anchors/:hash/download', () => {
          downloadSpy();
          return new HttpResponse(new Blob(['<anchor/>'], { type: 'application/xml' }), { status: 200 });
        }),
      ],
    });

    const downloadBtn = page.getByTestId('download-anchor-button');
    await expect.element(downloadBtn).toBeVisible();
    await downloadBtn.click();

    expect(downloadSpy).toHaveBeenCalled();
  });
});

describe('0850 — CS Trusted Anchors — delete anchor + confirm dialog -> gone from list (Browser Mode)', () => {
  it('clicking Delete opens confirm dialog; after confirm the anchor is removed from the list', async () => {
    let anchorsCallCount = 0;
    await renderRoute(TRUSTED_ANCHORS_PATH, {
      permissions: basePermissions,
      msw: [
        specHttp.get('/trusted-anchors', ({ response }) => {
          anchorsCallCount += 1;
          return anchorsCallCount === 1
            ? response(200).json([ANCHOR_CS2])
            : response(200).json([]);
        }),
        specHttp.delete('/trusted-anchors/{hash}', ({ response }) => response(204).empty()),
      ],
    });

    await expect.element(page.getByTestId('anchor')).toBeVisible();
    await expect.element(page.getByTestId('anchor-hash')).toHaveTextContent('aabbcc112233');

    await expect.element(page.getByTestId('delete-anchor-button')).toBeVisible();
    await page.getByTestId('delete-anchor-button').click();

    // Confirm dialog
    await expect.element(page.getByTestId('dialog-save-button')).toBeVisible();
    await page.getByTestId('dialog-save-button').click();

    // Anchor is gone from the list
    await expect.element(page.getByTestId('anchor')).not.toBeInTheDocument();
  });
});
