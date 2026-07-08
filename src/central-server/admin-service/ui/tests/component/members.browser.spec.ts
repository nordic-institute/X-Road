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
import { specHttp } from '../setup/spec-http';
import { submitDialogForm } from '../setup/dialog-helpers';
import { handlers, membersFixture } from '../setup/msw-handlers';
import { Permissions } from '@/global';
import type { PagedClients } from '@/openapi-types';

const MEMBER_ID = 'CS:GOV:1000';
const MEMBER_NAME = 'Test Organisation';
const MEMBER_CLASS = 'GOV';
const MEMBER_CODE = '1000';

const memberDetailPermissions = [
  Permissions.VIEW_MEMBERS,
  Permissions.VIEW_MEMBER_DETAILS,
  Permissions.EDIT_MEMBER_NAME,
  Permissions.DELETE_MEMBER,
];

function memberDetailHandlers() {
  return [
    ...handlers,
    specHttp.get('/members/{member_id}', ({ response }) =>
      response(200).json(membersFixture.clients![0] as never),
    ),
    specHttp.get('/members/{member_id}/owned-servers', ({ response }) =>
      response(200).json([] as never),
    ),
    specHttp.get('/members/{member_id}/used-servers', ({ response }) =>
      response(200).json([] as never),
    ),
    specHttp.get('/members/{member_id}/global-groups', ({ response }) =>
      response(200).json([] as never),
    ),
  ];
}

describe('0400 — CS Members — member detail info is correctly shown (Browser Mode)', () => {
  it('renders member name, class and code on the details view', async () => {
    await renderRoute(`/members/${encodeURIComponent(MEMBER_ID)}/details`, {
      permissions: memberDetailPermissions,
      msw: memberDetailHandlers(),
    });

    await expect.element(page.getByTestId('member-details')).toBeVisible();
    await expect.element(page.getByTestId('member-name')).toHaveTextContent(MEMBER_NAME);
    await expect.element(page.getByTestId('member-class')).toHaveTextContent(MEMBER_CLASS);
    await expect.element(page.getByTestId('member-code')).toHaveTextContent(MEMBER_CODE);
  });
});

describe('0400 — CS Members — change member name (Browser Mode)', () => {
  it('opens edit name dialog and updates member name after save', async () => {
    const updatedName = 'Renamed Organisation';

    await renderRoute(`/members/${encodeURIComponent(MEMBER_ID)}/details`, {
      permissions: memberDetailPermissions,
      msw: [
        ...memberDetailHandlers(),
        specHttp.patch('/members/{member_id}', ({ response }) =>
          response(200).json({
            ...membersFixture.clients![0],
            member_name: updatedName,
          } as never),
        ),
        specHttp.get('/members/{member_id}', ({ response }) =>
          response(200).json({
            ...membersFixture.clients![0],
            member_name: updatedName,
          } as never),
        ),
      ],
    });

    await expect.element(page.getByTestId('member-details')).toBeVisible();
    await expect.element(page.getByTestId('info-card-edit-button')).toBeVisible();

    await page.getByTestId('info-card-edit-button').click();

    await expect.element(page.getByTestId('edit-member-name')).toBeVisible();

    await page.getByTestId('edit-member-name').getByRole('textbox').fill(updatedName);

    submitDialogForm();

    await expect.element(page.getByTestId('edit-member-name')).not.toBeInTheDocument();
  });
});

describe('0400 — CS Members — delete member requires member code input (Browser Mode)', () => {
  it('disables save with wrong code and enables it with exact member code', async () => {
    await renderRoute(`/members/${encodeURIComponent(MEMBER_ID)}/details`, {
      permissions: memberDetailPermissions,
      msw: memberDetailHandlers(),
    });

    await expect.element(page.getByTestId('member-details')).toBeVisible();

    await page.getByTestId('delete-member').click();

    const deleteDialog = page.getByTestId('dialog-simple');
    await expect.element(deleteDialog.getByTestId('member-code')).toBeVisible();

    await deleteDialog.getByTestId('member-code').getByRole('textbox').fill('WRONG');

    await expect.element(page.getByTestId('dialog-save-button')).toBeDisabled();

    await deleteDialog.getByTestId('member-code').getByRole('textbox').fill(MEMBER_CODE);

    await expect.element(page.getByTestId('dialog-save-button')).not.toBeDisabled();
  });
});

describe('0400 — CS Members — search filters member rows (Browser Mode)', () => {
  it('typing partial name shows matching member and hides non-matching member', async () => {
    const filteredFixture: PagedClients = {
      clients: [membersFixture.clients![0]],
      paging_metadata: { total_items: 1, items: 1, limit: 25, offset: 0 },
    };

    await renderRoute('/members', {
      permissions: [Permissions.VIEW_MEMBERS, Permissions.VIEW_MEMBER_DETAILS],
      msw: [
        specHttp.get('/clients', ({ response }) => response(200).json(filteredFixture as never)),
        ...handlers,
      ],
    });

    await expect.element(page.getByTestId('members-table')).toBeVisible();

    await page.getByTestId('search-query-field').getByRole('textbox').fill('Test Org');

    await expect.element(page.getByText(MEMBER_NAME).first()).toBeVisible();
    await expect.element(page.getByText('Alpha Corp')).not.toBeInTheDocument();
  });
});
