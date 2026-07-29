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
import type { Backup } from '@/openapi-types';

// ── JSON Schema (inlined from OpenAPI components/schemas/Backup) ──────────────

const backupSchema = {
  type: 'object',
  required: ['filename', 'created_at'],
  properties: {
    filename: { type: 'string', minLength: 1, maxLength: 255 },
    created_at: { type: 'string', format: 'date-time' },
    compatible: { type: 'boolean' },
  },
};

// ── Fixtures ──────────────────────────────────────────────────────────────────

const backupsFixture: Backup[] = [
  {
    filename: 'configuration_backup_20230101.gpg',
    created_at: '2023-01-01T10:00:00.000Z',
    compatible: true,
  },
  {
    filename: 'configuration_backup_20221215.gpg',
    created_at: '2022-12-15T10:00:00.000Z',
    compatible: true,
  },
  {
    filename: 'configuration_backup_20220601.gpg',
    created_at: '2022-06-01T10:00:00.000Z',
    compatible: true,
  },
];

validateBody({ type: 'array', items: backupSchema }, backupsFixture);

// ── Permissions ───────────────────────────────────────────────────────────────

const backupPermissions = [Permissions.BACKUP_CONFIGURATION];

// ── Handlers ──────────────────────────────────────────────────────────────────

const backupsHandler = specHttp.get('/backups', ({ response }) => response(200).json(backupsFixture));

// ── Helper ────────────────────────────────────────────────────────────────────

function getTableRowTexts(): string[] {
  return page
    .getByTestId('backup-restore-view')
    .getByRole('row')
    .elements()
    .slice(1)
    .map((el) => el.textContent?.trim() ?? '');
}

describe('Backups — filter render: two-sided assertion (Browser Mode)', () => {
  it('shows matching backup and hides non-matching ones when filter is applied', async () => {
    await renderRoute('/settings/backup', {
      permissions: backupPermissions,
      msw: [backupsHandler],
    });

    await expect.element(page.getByTestId('backup-restore-view')).toBeVisible();
    await expect.element(page.getByTestId('search-query-field')).toBeVisible();

    const searchField = page.getByTestId('search-query-field').getByRole('textbox');
    await searchField.fill('20230101');

    await expect.element(page.getByTestId('backup-restore-view')).toBeVisible();

    const rows = getTableRowTexts();
    const joined = rows.join('\n');

    expect(joined).toContain('configuration_backup_20230101.gpg');
    expect(joined).not.toContain('configuration_backup_20221215.gpg');
    expect(joined).not.toContain('configuration_backup_20220601.gpg');
  });
});
