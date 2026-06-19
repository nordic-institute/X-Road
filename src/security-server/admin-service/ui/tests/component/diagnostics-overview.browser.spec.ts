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
import { renderRoute } from '../setup/render-route';
import { specHttp, validateBody } from '../setup/spec-http';
import { Permissions } from '@/global';
import type { AddOnStatus, MessageLogEncryptionStatus } from '@/openapi-types';

// ── AJV schemas ───────────────────────────────────────────────────────────────

const addonStatusSchema = {
  type: 'object',
  required: ['messagelog_enabled', 'opmonitoring_enabled'],
  properties: {
    messagelog_enabled: { type: 'boolean' },
    opmonitoring_enabled: { type: 'boolean' },
  },
};

const messageLogEncryptionStatusSchema = {
  type: 'object',
  required: ['message_log_archive_encryption_status', 'message_log_database_encryption_status', 'message_log_grouping_rule'],
  properties: {
    message_log_archive_encryption_status: { type: 'boolean' },
    message_log_database_encryption_status: { type: 'boolean' },
    message_log_grouping_rule: { type: 'string' },
  },
};

// ── Fixtures ──────────────────────────────────────────────────────────────────

const addonStatusFixture: AddOnStatus = {
  messagelog_enabled: true,
  opmonitoring_enabled: true,
};

const messageLogEncryptionStatusFixture: MessageLogEncryptionStatus = {
  message_log_archive_encryption_status: false,
  message_log_database_encryption_status: false,
  message_log_grouping_rule: 'NONE',
};

validateBody(addonStatusSchema, addonStatusFixture);
validateBody(messageLogEncryptionStatusSchema, messageLogEncryptionStatusFixture);

// ── Handlers ──────────────────────────────────────────────────────────────────

const addonStatusHandler = specHttp.get('/diagnostics/addon-status', ({ response }) =>
  response(200).json(addonStatusFixture),
);

const messageLogEncryptionHandler = specHttp.get('/diagnostics/message-log-encryption-status', ({ response }) =>
  response(200).json(messageLogEncryptionStatusFixture),
);

// ── Specs ─────────────────────────────────────────────────────────────────────

describe('Diagnostics Overview — download diagnostics report trigger (Browser Mode)', () => {
  it('clicking the download button fires a GET /diagnostics/info/download request', async () => {
    let downloadRequestFired = false;

    const downloadHandler = specHttp.get('/diagnostics/info/download', ({ response }) => {
      downloadRequestFired = true;
      return response(200).json('{}');
    });

    await renderRoute('/diagnostics/overview', {
      permissions: [Permissions.DIAGNOSTICS, Permissions.DOWNLOAD_DIAGNOSTICS_REPORT],
      msw: [addonStatusHandler, messageLogEncryptionHandler, downloadHandler],
    });

    const downloadBtn = page.getByTestId('download-diagnostics-report-button');
    await expect.element(downloadBtn).toBeVisible();

    await downloadBtn.click();

    await expect.poll(() => downloadRequestFired).toBe(true);
  });
});
