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

/*
 * Consolidation note: three other 1100 scenarios are DROP-dup per the audit ledger:
 * - "Configuration can be backed up and deleted" (create+delete) — pure-API CRUD, covered at API tier.
 * - "Already existing configuration backup is overwritten on upload" — same upload flow, different fixture.
 * - "Configuration can be restored from backup" — restore action, covered at API tier.
 * Only the download-trigger + upload-roundtrip scenario is authored here.
 */
import { describe, it, expect, vi } from 'vitest';
import { page } from 'vitest/browser';
import { HttpResponse } from 'msw';
import { renderRoute } from '../setup/render-route';
import { specHttp } from '../setup/spec-http';
import { Permissions } from '@/global';
import type { Backup } from '@/openapi-types';

const BACKUP_PATH = '/settings/backup';

const existingBackup: Backup = {
  filename: 'configuration_backup_20240601.gpg',
  compatible: true,
};

const uploadedBackup: Backup = {
  filename: 'my_new_backup.gpg',
  compatible: true,
};

describe('1100 — CS Backup and Restore — download trigger + upload updates count (Browser Mode)', () => {
  it('download button triggers download; uploading a new backup file increments the row count', async () => {
    const downloadSpy = vi.fn();
    let backupsCallCount = 0;

    await renderRoute(BACKUP_PATH, {
      permissions: [Permissions.BACKUP_CONFIGURATION],
      msw: [
        specHttp.get('/backups', ({ response }) => {
          backupsCallCount += 1;
          // first call: one existing backup; second call (after upload): two backups
          return backupsCallCount === 1
            ? response(200).json([existingBackup])
            : response(200).json([existingBackup, uploadedBackup]);
        }),
        specHttp.untyped.get(
          '/api/v1/backups/:filename/download',
          () => {
            downloadSpy();
            return new HttpResponse(new Blob(['backup-bytes'], { type: 'application/gzip' }), {
              status: 200,
            });
          },
        ),
        specHttp.post('/backups/upload', ({ response }) => response(201).json(uploadedBackup)),
      ],
    });

    await expect.element(page.getByText(existingBackup.filename)).toBeVisible();

    // Download trigger
    await page.getByTestId('backup-download').first().click();
    expect(downloadSpy).toHaveBeenCalled();

    // Upload a new backup via the hidden file input (XrdFileUpload)
    await page.getByTestId('backup-upload').click();
    const gpgFile = new File(['gpg-backup-content'], uploadedBackup.filename, {
      type: 'application/pgp-encrypted',
    });
    const fileInput = document.querySelector('input[type="file"]') as HTMLInputElement;
    await page.elementLocator(fileInput).upload(gpgFile);

    // After upload the list re-fetches and the new backup appears
    await expect.element(page.getByText(uploadedBackup.filename)).toBeVisible();
  });
});
