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
import { HttpResponse } from 'msw';
import type { RequestHandler } from 'msw';
import { renderRoute } from '../setup/render-route';
import { specHttp } from '../setup/spec-http';
import { submitDialogForm } from '../setup/dialog-helpers';
import { Permissions } from '@/global';
import type { GlobalGroupResource } from '@/openapi-types';

const globalResourcesPermissions = [Permissions.VIEW_GLOBAL_GROUPS, Permissions.ADD_GLOBAL_GROUP];

const ownersGroup: GlobalGroupResource = {
  code: 'security-server-owners',
  description: 'Security server owners',
  member_count: 0,
  updated_at: '2024-01-01T00:00:00Z',
};

const globalGroupsHandler = specHttp.get('/global-groups', ({ response }) =>
  response(200).json([ownersGroup]),
);

async function renderGlobalResources(extraHandlers: RequestHandler[] = []) {
  return renderRoute('/settings/global-resources', {
    permissions: globalResourcesPermissions,
    msw: [globalGroupsHandler, ...extraHandlers],
  });
}

describe('0340 — CS Global Resources — add dialog save-disabled gating and list render (Browser Mode)', () => {
  it('Save button is disabled when form is empty and enabled after filling code and description, then added group appears', async () => {
    const newGroup: GlobalGroupResource = {
      code: 'test-group',
      description: 'Test Group Description',
      member_count: 0,
      updated_at: '2024-01-01T00:00:00Z',
    };

    let groupsCallCount = 0;
    await renderRoute('/settings/global-resources', {
      permissions: globalResourcesPermissions,
      msw: [
        specHttp.get('/global-groups', ({ response }) => {
          groupsCallCount += 1;
          return groupsCallCount === 1
            ? response(200).json([ownersGroup])
            : response(200).json([ownersGroup, newGroup]);
        }),
        specHttp.post('/global-groups', ({ response }) => response(201).json(newGroup)),
      ],
    });

    await expect.element(page.getByTestId('global-groups-table')).toBeVisible();

    await page.getByTestId('add-global-group-button').click();

    await expect.element(page.getByTestId('dialog-save-button')).toBeDisabled();

    await page.getByTestId('add-global-group-code-input').getByRole('textbox').fill('test-group');
    await page
      .getByTestId('add-global-group-description-input')
      .getByRole('textbox')
      .fill('Test Group Description');

    await expect.element(page.getByTestId('dialog-save-button')).not.toBeDisabled();
    submitDialogForm();

    await expect.element(page.getByText('test-group').first()).toBeVisible();
  });
});

describe('0340 — CS Global Resources — invalid description on add (Browser Mode)', () => {
  it('entering invalid description characters shows inline validation error', async () => {
    await renderGlobalResources();

    await expect.element(page.getByTestId('global-groups-table')).toBeVisible();

    await page.getByTestId('add-global-group-button').click();

    await page.getByTestId('add-global-group-code-input').getByRole('textbox').fill('valid-code');
    await page
      .getByTestId('add-global-group-description-input')
      .getByRole('textbox')
      .fill('invalid-desc$€');

    submitDialogForm();

    await expect.element(page.getByText('Use valid description characters only')).toBeVisible();
  });
});

describe('0340 — CS Global Resources — colon in code field (Browser Mode)', () => {
  it('entering colon in code field shows invalid identifier error and Save button is disabled', async () => {
    await renderGlobalResources([
      specHttp.untyped.post('/api/v1/global-groups', () =>
        HttpResponse.json(
          {
            status: 400,
            error: {
              code: 'validation_failure',
              validation_errors: {
                'globalGroupCodeAndDescriptionDto.code': ['IdentifierChars'],
              },
            },
          },
          { status: 400 },
        ),
      ),
    ]);

    await expect.element(page.getByTestId('global-groups-table')).toBeVisible();

    await page.getByTestId('add-global-group-button').click();

    await page.getByTestId('add-global-group-code-input').getByRole('textbox').fill('no:colons');
    await page.getByTestId('add-global-group-description-input').getByRole('textbox').fill('Some description');

    await expect.element(page.getByTestId('dialog-save-button')).not.toBeDisabled();
    submitDialogForm();

    await expect.element(page.getByText('Use valid identifier characters only')).toBeVisible();
    await expect.element(page.getByTestId('dialog-save-button')).toBeDisabled();
  });
});
