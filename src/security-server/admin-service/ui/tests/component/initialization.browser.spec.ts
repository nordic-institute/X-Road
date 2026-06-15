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

/**
 * 0100 — Security Server initialisation wizard UI-integration scenarios.
 *
 * Setup strategy:
 *   - renderRoute('/initial-configuration') with a normal authenticated session.
 *     In non-bootstrap mode renderRoute uses a plain createRouter (no guard), so
 *     navigating to /initial-configuration works unconditionally.
 *   - InitialConfigurationView.created() calls fetchInitializationStatus()
 *     → GET /initialization/status. Every test mocks this endpoint.
 *   - When is_anchor_imported is false the wizard starts at the anchor step
 *     (anchorStep = 1). When is_anchor_imported is true it is skipped
 *     (anchorStep = 0, falsy) and the wizard opens directly at the owner-member
 *     step (memberStep = 1).
 *   - Permissions: INIT_CONFIG (required to access the init wizard view) and
 *     UPLOAD_ANCHOR (required for the upload button inside ConfigurationAnchorStep).
 *
 * Anchor-upload interaction:
 *   - The real file-chooser + preview + confirm + multipart-upload chain is an
 *     OS-level interaction (file-picker API). Exercising it end-to-end is an e2e
 *     concern. The anchor-step test therefore focuses on the observable pre-upload
 *     state: the wizard renders the anchor step header and the Continue button is
 *     disabled until an anchor has been loaded.
 *   - The owner-member → token-PIN → submit flow is tested with is_anchor_imported
 *     set to true so the wizard skips the anchor step.
 *
 * POST /initialization success:
 *   - The success handler calls setInitializationStatus() (marks init done),
 *     fetchCurrentSecurityServer() (GET /security-servers), checkAlertStatus()
 *     (GET /notifications/alerts — default handler from msw-handlers.ts), then
 *     router.replace(firstAllowedTab.to). DEFAULT_PERMISSIONS includes VIEW_CLIENTS,
 *     so firstAllowedTab resolves to { name: RouteName.Clients }. The Clients view
 *     fires GET /clients — also covered by the default handler.
 *
 * PIN validation:
 *   - The vee-validate schema on TokenPinStep is:
 *       pin: 'required'
 *       confirmPin: 'required|confirmed:@pin'
 *     Mismatched pins make meta.valid false → token-pin-save-button is disabled.
 *   - Matching pins make meta.valid true → button enabled.
 *
 * 4xx rejection suppression:
 *   - The member-names lookup (GET /member-names) may return 404 when the
 *     synthetic member does not exist in the mocked global conf; OwnerMemberStep
 *     silently swallows 404s. No suppression needed.
 */
import { describe, it, expect } from 'vitest';
import { page } from 'vitest/browser';
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

const currentServerFixture: SecurityServer = {
  id: 'CS:COM:1234:SS0',
  member_class: 'COM',
  member_code: '1234',
  server_code: 'SS0',
};

validateBody(initStatusSchema, uninitializedStatusFixture);
validateBody(initStatusSchema, anchorImportedStatusFixture);
validateBody(securityServerSchema, currentServerFixture);

// ── MSW handlers ──────────────────────────────────────────────────────────────

const uninitializedStatusHandler = specHttp.get('/initialization/status', ({ response }) =>
  response(200).json(uninitializedStatusFixture),
);

const anchorImportedStatusHandler = specHttp.get('/initialization/status', ({ response }) =>
  response(200).json(anchorImportedStatusFixture),
);

const memberClassesHandler = specHttp.get('/member-classes', ({ response }) =>
  response(200).json(['COM', 'ORG']),
);

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

// MIGRATED-FROM: 0100-ss-initialization.feature :: "Security server is initialized"
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
