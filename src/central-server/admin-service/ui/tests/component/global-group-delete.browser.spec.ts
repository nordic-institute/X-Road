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
import { page, userEvent } from 'vitest/browser';
import { HttpResponse } from 'msw';
import type { RequestHandler } from 'msw';
import { renderRoute } from '../setup/render-route';
import { specHttp } from '../setup/spec-http';
import { Permissions } from '@/global';
import type { GlobalGroupResource } from '@/openapi-types';

const GROUP_CODE = 'test-group';

const groupFixture: GlobalGroupResource = {
  code: GROUP_CODE,
  description: 'Test group description',
  member_count: 0,
  updated_at: '2024-01-01T00:00:00Z',
};

const otherGroupFixture: GlobalGroupResource = {
  code: 'security-server-owners',
  description: 'Security server owners',
  member_count: 0,
  updated_at: '2024-01-01T00:00:00Z',
};

const deletePermissions = [Permissions.VIEW_GLOBAL_GROUPS, Permissions.VIEW_GROUP_DETAILS, Permissions.DELETE_GROUP];

async function renderGroupView(extraHandlers: RequestHandler[]) {
  return renderRoute(`/settings/global-resources/global-groups/${GROUP_CODE}`, {
    permissions: deletePermissions,
    msw: [
      specHttp.get('/global-groups', ({ response }) => response(200).json([otherGroupFixture])),
      specHttp.get('/global-groups/{group_code}', ({ response }) => response(200).json(groupFixture)),
      specHttp.post('/global-groups/{group_code}/members', ({ response }) =>
        response(200).json({ items: [], paging_metadata: { total_items: 0, items: 0, limit: 25, offset: 0 } } as never),
      ),
      ...extraHandlers,
    ],
  });
}

async function openDeleteDialog() {
  await expect.element(page.getByTestId('remove-group-button')).toBeVisible();
  await page.getByTestId('remove-group-button').click();
  await expect.element(page.getByText(`Are you sure you want to remove the global group ${GROUP_CODE}?`)).toBeVisible();
}

describe('CS Global Group — delete dialog (Browser Mode)', () => {
  it('confirming sends DELETE, shows a success message and returns to the global groups list', async () => {
    const deleteSpy = vi.fn();
    await renderGroupView([
      specHttp.delete('/global-groups/{group_code}', ({ params, response }) => {
        deleteSpy(params.group_code);
        return response(204).empty();
      }),
    ]);

    await openDeleteDialog();
    await page.getByTestId('dialog-save-button').click();

    await expect.poll(() => deleteSpy.mock.calls.length).toBe(1);
    expect(deleteSpy).toHaveBeenCalledWith(GROUP_CODE);
    await expect.element(page.getByText('Global group deleted successfully.')).toBeVisible();
    await expect.element(page.getByTestId('global-groups-table')).toBeVisible();
    await expect.poll(() => page.getByTestId('remove-group-button').query()).toBeNull();
  });

  it('a failing DELETE shows the error and clears the loading state', async () => {
    const deleteSpy = vi.fn();
    await renderGroupView([
      specHttp.untyped.delete('/api/v1/global-groups/:group_code', () => {
        deleteSpy();
        return HttpResponse.json({ status: 400, error: { code: 'cannot_read_certificate' } }, { status: 400 });
      }),
    ]);

    await openDeleteDialog();
    await page.getByTestId('dialog-save-button').click();

    await expect.poll(() => deleteSpy.mock.calls.length).toBe(1);
    await expect.element(page.getByText('Cannot read TLS certificate')).toBeVisible();
    await expect.element(page.getByTestId('dialog-save-button')).not.toHaveClass('v-btn--loading');
  });

  it('a second confirm while the DELETE is pending sends no second request', async () => {
    const deleteSpy = vi.fn();
    let releaseDelete: () => void = () => {};
    const deleteGate = new Promise<void>((resolve) => {
      releaseDelete = resolve;
    });
    await renderGroupView([
      specHttp.delete('/global-groups/{group_code}', async ({ params, response }) => {
        deleteSpy(params.group_code);
        await deleteGate;
        return response(204).empty();
      }),
    ]);

    await openDeleteDialog();
    await page.getByTestId('dialog-save-button').click();
    await expect.poll(() => deleteSpy.mock.calls.length).toBe(1);
    await expect.element(page.getByTestId('dialog-save-button')).toHaveClass('v-btn--loading');

    await userEvent.keyboard('{Enter}');
    releaseDelete();

    await expect.element(page.getByText('Global group deleted successfully.')).toBeVisible();
    await expect.element(page.getByTestId('global-groups-table')).toBeVisible();
    expect(deleteSpy).toHaveBeenCalledTimes(1);
  });

  it('confirming again after a failed DELETE sends a new request', async () => {
    const deleteSpy = vi.fn();
    await renderGroupView([
      specHttp.untyped.delete('/api/v1/global-groups/:group_code', () => {
        deleteSpy();
        return HttpResponse.json({ status: 400, error: { code: 'cannot_read_certificate' } }, { status: 400 });
      }),
    ]);

    await openDeleteDialog();
    await page.getByTestId('dialog-save-button').click();
    await expect.poll(() => deleteSpy.mock.calls.length).toBe(1);
    await expect.element(page.getByTestId('dialog-save-button')).not.toHaveClass('v-btn--loading');

    await page.getByTestId('dialog-save-button').click();
    await expect.poll(() => deleteSpy.mock.calls.length).toBe(2);
  });

  it('cancelling closes the dialog without sending DELETE', async () => {
    const deleteSpy = vi.fn();
    await renderGroupView([
      specHttp.delete('/global-groups/{group_code}', ({ response }) => {
        deleteSpy();
        return response(204).empty();
      }),
    ]);

    await openDeleteDialog();
    await page.getByTestId('dialog-cancel-button').click();

    await expect.poll(() => page.getByTestId('dialog-save-button').query()).toBeNull();
    expect(deleteSpy).not.toHaveBeenCalled();
  });
});
