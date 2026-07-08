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
import type { ConfigurationPart } from '@/openapi-types';

// 0800 External + 0810 Internal are consolidated: one spec exercises SHARED-PARAMETERS (both),
// PRIVATE-PARAMETERS (internal only), and the optional MONITORING upload/download (internal only).

const INTERNAL_PATH = '/global-configuration/internal-configuration';
const EXTERNAL_PATH = '/global-configuration/external-configuration';

const ANCHOR = { hash: 'abc123', created_at: '2024-01-01T00:00:00Z' };

const basePermissions = [
  Permissions.VIEW_INTERNAL_CONFIGURATION_SOURCE,
  Permissions.VIEW_EXTERNAL_CONFIGURATION_SOURCE,
  Permissions.VIEW_CONFIGURATION_MANAGEMENT,
  Permissions.DOWNLOAD_CONFIGURATION_PART,
  Permissions.UPLOAD_CONFIGURATION_PART,
];

function anchorHandler() {
  return specHttp.get('/configuration-sources/{configuration_type}/anchor', ({ response }) =>
    response(200).json({ anchor: ANCHOR }),
  );
}

function downloadUrlHandler() {
  return specHttp.get('/configuration-sources/{configuration_type}/download-url', ({ response }) =>
    response(200).json({ url: 'http://example.com/anchor' }),
  );
}

function tokensHandler() {
  return specHttp.get('/tokens', ({ response }) => response(200).json([]));
}

function nonOptionalPart(contentIdentifier: string): ConfigurationPart {
  return {
    content_identifier: contentIdentifier,
    file_name: `${contentIdentifier.toLowerCase()}.xml`,
    optional: false,
    file_updated_at: '2024-01-01T10:00:00Z',
    version: 0,
  };
}

function optionalPartNoFile(contentIdentifier: string): ConfigurationPart {
  return {
    content_identifier: contentIdentifier,
    file_name: `${contentIdentifier.toLowerCase()}.xml`,
    optional: true,
    file_updated_at: '',
    version: 0,
  };
}

function optionalPartWithFile(contentIdentifier: string): ConfigurationPart {
  return {
    content_identifier: contentIdentifier,
    file_name: `${contentIdentifier.toLowerCase()}.xml`,
    optional: true,
    file_updated_at: '2024-06-01T12:00:00Z',
    version: 0,
  };
}

// Representative: SHARED-PARAMETERS covers the non-optional download trigger (same logic for PRIVATE-PARAMETERS)
describe('0800/0810 — CS Config Parts — download non-optional part (SHARED-PARAMETERS) (Browser Mode)', () => {
  it('download button is visible for SHARED-PARAMETERS and triggers the download action', async () => {
    const downloadSpy = vi.fn();

    await renderRoute(INTERNAL_PATH, {
      permissions: basePermissions,
      msw: [
        tokensHandler(),
        anchorHandler(),
        downloadUrlHandler(),
        specHttp.get('/configuration-sources/{configuration_type}/configuration-parts', ({ response }) =>
          response(200).json([nonOptionalPart('SHARED-PARAMETERS')]),
        ),
        specHttp.untyped.get('/api/v1/configuration-sources/:configuration_type/configuration-parts/:content_identifier/:version/download', () => {
          downloadSpy();
          return new HttpResponse(new Blob(['<params/>'], { type: 'application/xml' }), { status: 200 });
        }),
      ],
    });

    const downloadBtn = page.getByTestId('configuration-part-SHARED-PARAMETERS-download');
    await expect.element(downloadBtn).toBeVisible();
    await downloadBtn.click();

    expect(downloadSpy).toHaveBeenCalled();
  });
});

// Representative: non-optional parts show no upload button (optional: false → upload button gated out)
describe('0800/0810 — CS Config Parts — upload disabled for non-optional SHARED-PARAMETERS (Browser Mode)', () => {
  it('no upload button is rendered for SHARED-PARAMETERS (non-optional part)', async () => {
    await renderRoute(EXTERNAL_PATH, {
      permissions: basePermissions,
      msw: [
        tokensHandler(),
        anchorHandler(),
        downloadUrlHandler(),
        specHttp.get('/configuration-sources/{configuration_type}/configuration-parts', ({ response }) =>
          response(200).json([nonOptionalPart('SHARED-PARAMETERS')]),
        ),
      ],
    });

    await expect.element(page.getByTestId('configuration-part-SHARED-PARAMETERS')).toBeVisible();
    expect(page.getByTestId('configuration-part-SHARED-PARAMETERS-upload').query()).toBeNull();
  });
});

// Internal only: PRIVATE-PARAMETERS is an internal-only non-optional part
describe('0810 — CS Config Parts — upload disabled for non-optional PRIVATE-PARAMETERS (Browser Mode)', () => {
  it('no upload button is rendered for PRIVATE-PARAMETERS (non-optional part)', async () => {
    await renderRoute(INTERNAL_PATH, {
      permissions: basePermissions,
      msw: [
        tokensHandler(),
        anchorHandler(),
        downloadUrlHandler(),
        specHttp.get('/configuration-sources/{configuration_type}/configuration-parts', ({ response }) =>
          response(200).json([nonOptionalPart('PRIVATE-PARAMETERS')]),
        ),
      ],
    });

    await expect.element(page.getByTestId('configuration-part-PRIVATE-PARAMETERS')).toBeVisible();
    expect(page.getByTestId('configuration-part-PRIVATE-PARAMETERS-upload').query()).toBeNull();
  });
});

// Representative for optional upload: MONITORING covers the upload flow (same pattern for FETCHINTERVAL, NEXTUPDATE)
describe('0810 — CS Config Parts — upload optional part (MONITORING) updates part row (Browser Mode)', () => {
  it('upload button opens file dialog; after upload the part row shows an updated timestamp', async () => {
    let partsCallCount = 0;
    await renderRoute(INTERNAL_PATH, {
      permissions: basePermissions,
      msw: [
        tokensHandler(),
        anchorHandler(),
        downloadUrlHandler(),
        specHttp.get('/configuration-sources/{configuration_type}/configuration-parts', ({ response }) => {
          partsCallCount += 1;
          return partsCallCount === 1
            ? response(200).json([optionalPartNoFile('MONITORING')])
            : response(200).json([optionalPartWithFile('MONITORING')]);
        }),
        specHttp.post('/configuration-sources/{configuration_type}/configuration-parts', ({ response }) =>
          response(204).empty(),
        ),
      ],
    });

    // Upload button visible; no download button (file_updated_at empty)
    const uploadBtn = page.getByTestId('configuration-part-MONITORING-upload');
    await expect.element(uploadBtn).toBeVisible();
    expect(page.getByTestId('configuration-part-MONITORING-download').query()).toBeNull();

    // Open upload dialog
    await uploadBtn.click();
    await expect.element(page.getByTestId('timestamping-service-file-input')).toBeVisible();

    // Upload an XML file via file input
    const xmlFile = new File(['<monitoring/>'], 'monitoring-params.xml', { type: 'application/xml' });
    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
    await page.elementLocator(fileInput).upload(xmlFile);

    await expect.element(page.getByTestId('dialog-save-button')).not.toBeDisabled();
    await page.getByTestId('dialog-save-button').click();

    // After upload the part row shows a download button (file_updated_at is now set)
    await expect.element(page.getByTestId('configuration-part-MONITORING-download')).toBeVisible();
  });
});

// Representative for optional download: after upload, download button appears for optional parts
describe('0810 — CS Config Parts — download optional part (MONITORING) trigger (Browser Mode)', () => {
  it('download button is visible for MONITORING when a file exists and triggers the download action', async () => {
    const downloadSpy = vi.fn();

    await renderRoute(INTERNAL_PATH, {
      permissions: basePermissions,
      msw: [
        tokensHandler(),
        anchorHandler(),
        downloadUrlHandler(),
        specHttp.get('/configuration-sources/{configuration_type}/configuration-parts', ({ response }) =>
          response(200).json([optionalPartWithFile('MONITORING')]),
        ),
        specHttp.untyped.get('/api/v1/configuration-sources/:configuration_type/configuration-parts/:content_identifier/:version/download', () => {
          downloadSpy();
          return new HttpResponse(new Blob(['<monitoring/>'], { type: 'application/xml' }), { status: 200 });
        }),
      ],
    });

    const downloadBtn = page.getByTestId('configuration-part-MONITORING-download');
    await expect.element(downloadBtn).toBeVisible();
    await downloadBtn.click();

    expect(downloadSpy).toHaveBeenCalled();
  });
});
