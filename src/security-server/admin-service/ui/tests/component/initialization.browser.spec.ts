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
import { delay } from 'msw';
import { renderRoute } from '../setup/render-route';
import { specHttp, validateBody } from '../setup/spec-http';
import { Permissions, RouteName } from '@/global';
import type { InitializationStatus, SecurityServer } from '@/openapi-types';
import { TokenInitStatus } from '@/openapi-types';

// ── JSON Schema definitions (inlined from OpenAPI components/schemas) ────────

const initStatusSchema = {
  type: 'object',
  required: ['is_anchor_imported', 'is_server_code_initialized', 'is_server_owner_initialized', 'software_token_init_status', 'enforce_token_pin_policy'],
  properties: {
    is_anchor_imported: { type: 'boolean' },
    is_server_code_initialized: { type: 'boolean' },
    is_server_owner_initialized: { type: 'boolean' },
    software_token_init_status: {
      type: 'string',
      enum: ['INITIALIZED', 'NOT_INITIALIZED', 'UNKNOWN'],
    },
    enforce_token_pin_policy: { type: 'boolean' },
  },
};

const securityServerSchema = {
  type: 'object',
  required: ['id', 'member_class', 'member_code', 'server_code'],
  properties: {
    id: { type: 'string' },
    member_class: { type: 'string' },
    member_code: { type: 'string' },
    server_code: { type: 'string' },
    server_address: { type: 'string' },
  },
};

// ── Fixtures ──────────────────────────────────────────────────────────────────

const uninitializedStatusFixture: InitializationStatus = {
  is_anchor_imported: false,
  is_server_code_initialized: false,
  is_server_owner_initialized: false,
  software_token_init_status: TokenInitStatus.NOT_INITIALIZED,
  enforce_token_pin_policy: false,
};

const anchorImportedStatusFixture: InitializationStatus = {
  is_anchor_imported: true,
  is_server_code_initialized: false,
  is_server_owner_initialized: false,
  software_token_init_status: TokenInitStatus.NOT_INITIALIZED,
  enforce_token_pin_policy: false,
};

const ownerAndCodeAlreadyInitializedStatusFixture: InitializationStatus = {
  is_anchor_imported: true,
  is_server_code_initialized: true,
  is_server_owner_initialized: true,
  software_token_init_status: TokenInitStatus.NOT_INITIALIZED,
  enforce_token_pin_policy: false,
};

const currentServerFixture: SecurityServer = {
  id: 'CS:COM:1234:SS0',
  member_class: 'COM',
  member_code: '1234',
  server_code: 'SS0',
};

validateBody(initStatusSchema, uninitializedStatusFixture);
validateBody(initStatusSchema, anchorImportedStatusFixture);
validateBody(initStatusSchema, ownerAndCodeAlreadyInitializedStatusFixture);
validateBody(securityServerSchema, currentServerFixture);

// ── MSW handlers ──────────────────────────────────────────────────────────────

const uninitializedStatusHandler = specHttp.get('/initialization/status', ({ response }) =>
  response(200).json(uninitializedStatusFixture),
);

const anchorImportedStatusHandler = specHttp.get('/initialization/status', ({ response }) =>
  response(200).json(anchorImportedStatusFixture),
);

const ownerAndCodeAlreadyInitializedStatusHandler = specHttp.get('/initialization/status', ({ response }) =>
  response(200).json(ownerAndCodeAlreadyInitializedStatusFixture),
);

const memberClassesHandler = specHttp.get('/member-classes', ({ response }) =>
  response(200).json(['COM', 'ORG']),
);

// A single available class, different from currentServerFixture.member_class, resolving
// after the current-server fetch, so the "only one member class available" auto-select
// would clobber the owner's already-populated real class if it were not guarded against
// an already-initialized owner.
const singleOtherMemberClassHandler = specHttp.get('/member-classes', async ({ response }) => {
  await delay(50);
  return response(200).json(['ORG']);
});

const emptyMemberClassesHandler = specHttp.get('/member-classes', ({ response }) => response(200).json([]));

const memberNamesHandler = specHttp.get('/member-names', ({ response }) =>
  response(404).empty(),
);

const initServerHandler = specHttp.post('/initialization', ({ response }) => response(201).empty());

const currentServerHandler = specHttp.get('/security-servers', ({ response }) =>
  response(200).json([currentServerFixture]),
);

// ── Permissions used by the init wizard view ──────────────────────────────────

const initPermissions = [Permissions.INIT_CONFIG, Permissions.UPLOAD_ANCHOR, Permissions.VIEW_CLIENTS];

// ── Specs ──────────────────────────────────────────────────────────────────────

describe('0100 — Security Server initialisation wizard (Browser Mode)', () => {
  it('Anchor step renders and Continue is disabled before anchor is loaded', async () => {
    await renderRoute('/initial-configuration', {
      permissions: initPermissions,
      msw: [uninitializedStatusHandler, memberClassesHandler, memberNamesHandler],
    });

    await expect.element(page.getByTestId('view-header-title')).toBeVisible();
    await expect.element(page.getByText('Initial configuration')).toBeVisible();

    await expect.element(page.getByTestId('configuration-anchor-save-button')).toBeVisible();
    await expect.element(page.getByTestId('configuration-anchor-save-button')).toBeDisabled();
  });

  it('Owner-member validation blocks Continue until required fields are filled', async () => {
    await renderRoute('/initial-configuration', {
      permissions: initPermissions,
      msw: [anchorImportedStatusHandler, memberClassesHandler, memberNamesHandler],
    });

    await expect.element(page.getByTestId('member-class-input')).toBeVisible();
    await expect.element(page.getByTestId('member-code-input')).toBeVisible();
    await expect.element(page.getByTestId('security-server-code-input')).toBeVisible();

    await expect.element(page.getByTestId('owner-member-save-button')).toBeDisabled();

    await page.getByTestId('member-class-input').click();
    await expect.element(page.getByRole('listbox')).toBeVisible();
    await page.getByRole('option', { name: 'COM' }).click();

    await page.getByTestId('member-code-input').getByRole('textbox').fill('1234');
    await page.getByTestId('security-server-code-input').getByRole('textbox').fill('SS0');

    await page.getByTestId('member-class-input').click();

    await expect.element(page.getByTestId('owner-member-save-button')).not.toBeDisabled();
  });

  it('Owner-member fields are prefilled and disabled, and Continue is enabled on load, on an already-initialized server', async () => {
    await renderRoute('/initial-configuration', {
      permissions: initPermissions,
      msw: [
        ownerAndCodeAlreadyInitializedStatusHandler,
        memberClassesHandler,
        memberNamesHandler,
        currentServerHandler,
      ],
    });

    await expect.element(page.getByTestId('member-class-input')).toBeVisible();

    await expect.element(page.getByTestId('member-class-input')).toHaveTextContent(currentServerFixture.member_class ?? '');
    await expect.element(page.getByTestId('member-class-input').getByRole('combobox').nth(1)).toBeDisabled();

    await expect
      .element(page.getByTestId('member-code-input').getByRole('textbox'))
      .toHaveValue(currentServerFixture.member_code);
    await expect.element(page.getByTestId('member-code-input').getByRole('textbox')).toBeDisabled();

    await expect
      .element(page.getByTestId('security-server-code-input').getByRole('textbox'))
      .toHaveValue(currentServerFixture.server_code);
    await expect.element(page.getByTestId('security-server-code-input').getByRole('textbox')).toBeDisabled();

    await expect.element(page.getByTestId('owner-member-save-button')).not.toBeDisabled();
  });

  it("Owner's real member class is not overwritten by the single-available-class auto-select, on an already-initialized server", async () => {
    await renderRoute('/initial-configuration', {
      permissions: initPermissions,
      msw: [
        ownerAndCodeAlreadyInitializedStatusHandler,
        singleOtherMemberClassHandler,
        memberNamesHandler,
        currentServerHandler,
      ],
    });

    await expect.element(page.getByTestId('member-class-input')).toBeVisible();
    await expect.element(page.getByTestId('member-class-input')).toHaveTextContent(currentServerFixture.member_class ?? '');
  });

  it('Owner-member fields stay prefilled and Continue stays enabled when the current instance has no member classes, on an already-initialized server', async () => {
    await renderRoute('/initial-configuration', {
      permissions: initPermissions,
      msw: [
        ownerAndCodeAlreadyInitializedStatusHandler,
        emptyMemberClassesHandler,
        memberNamesHandler,
        currentServerHandler,
      ],
    });

    await expect.element(page.getByTestId('member-class-input')).toBeVisible();
    await expect.element(page.getByTestId('member-class-input')).toHaveTextContent(currentServerFixture.member_class ?? '');

    await expect
      .element(page.getByTestId('member-code-input').getByRole('textbox'))
      .toHaveValue(currentServerFixture.member_code);
    await expect
      .element(page.getByTestId('security-server-code-input').getByRole('textbox'))
      .toHaveValue(currentServerFixture.server_code);

    await expect.element(page.getByTestId('owner-member-save-button')).not.toBeDisabled();
  });

  it('Previous button is not shown on the Owner Member step when the anchor is already imported', async () => {
    await renderRoute('/initial-configuration', {
      permissions: initPermissions,
      msw: [
        ownerAndCodeAlreadyInitializedStatusHandler,
        memberClassesHandler,
        memberNamesHandler,
        currentServerHandler,
      ],
    });

    await expect.element(page.getByTestId('member-class-input')).toBeVisible();
    await expect.element(page.getByTestId('previous-button')).not.toBeInTheDocument();
  });

  it('PIN step shows token-policy alert; matching PINs enable Submit and POST 201 navigates to Clients', async () => {
    const { router } = await renderRoute('/initial-configuration', {
      permissions: initPermissions,
      msw: [anchorImportedStatusHandler, memberClassesHandler, memberNamesHandler, initServerHandler, currentServerHandler],
    });

    await expect.element(page.getByTestId('member-class-input')).toBeVisible();

    await page.getByTestId('member-class-input').click();
    await expect.element(page.getByRole('listbox')).toBeVisible();
    await page.getByRole('option', { name: 'COM' }).click();
    await page.getByTestId('member-code-input').getByRole('textbox').fill('1234');
    await page.getByTestId('security-server-code-input').getByRole('textbox').fill('SS0');

    await page.getByTestId('member-class-input').click();
    await expect.element(page.getByTestId('owner-member-save-button')).not.toBeDisabled();
    await page.getByTestId('owner-member-save-button').click();

    await expect.element(page.getByTestId('pin-input')).toBeVisible();
    await expect.element(page.getByTestId('alert-token-policy-enabled')).toBeVisible();

    await page.getByTestId('pin-input').getByRole('textbox').fill('T0ken1zer3');
    await page.getByTestId('confirm-pin-input').getByRole('textbox').fill('T0ken1zer3');

    await expect.element(page.getByTestId('token-pin-save-button')).not.toBeDisabled();
    await page.getByTestId('token-pin-save-button').click();

    await expect.element(page.getByTestId('client-name').first()).toBeVisible();
    expect(router.currentRoute.value.name).toBe(RouteName.Clients);
  });

  it('PIN confirmation mismatch disables Submit', async () => {
    await renderRoute('/initial-configuration', {
      permissions: initPermissions,
      msw: [anchorImportedStatusHandler, memberClassesHandler, memberNamesHandler],
    });

    await expect.element(page.getByTestId('member-class-input')).toBeVisible();

    await page.getByTestId('member-class-input').click();
    await expect.element(page.getByRole('listbox')).toBeVisible();
    await page.getByRole('option', { name: 'COM' }).click();
    await page.getByTestId('member-code-input').getByRole('textbox').fill('1234');
    await page.getByTestId('security-server-code-input').getByRole('textbox').fill('SS0');

    await page.getByTestId('member-class-input').click();
    await expect.element(page.getByTestId('owner-member-save-button')).not.toBeDisabled();
    await page.getByTestId('owner-member-save-button').click();

    await expect.element(page.getByTestId('pin-input')).toBeVisible();

    await page.getByTestId('pin-input').getByRole('textbox').fill('T0ken1zer3');
    await page.getByTestId('confirm-pin-input').getByRole('textbox').fill('DifferentPIN!');

    await page.getByTestId('pin-input').click();

    await expect.element(page.getByTestId('token-pin-save-button')).toBeDisabled();
  });
});
