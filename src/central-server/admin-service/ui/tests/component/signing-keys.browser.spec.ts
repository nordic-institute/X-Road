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
import { Permissions } from '@/global';
import type { Token, ConfigurationSigningKey, ConfigurationType } from '@/openapi-types';

// Spec parametrized over internal/external configuration
const CONFIG_ROUTES = [
  { label: 'internal (0600)', path: '/global-configuration/internal-configuration', sourceType: 'INTERNAL' as ConfigurationType },
  { label: 'external (0610)', path: '/global-configuration/external-configuration', sourceType: 'EXTERNAL' as ConfigurationType },
] as const;

const TOKEN_ID = 'softToken-0';
const TOKEN_NAME = 'softToken-0';

function makeKey(id: string, sourceType: ConfigurationType, actions: ConfigurationSigningKey['possible_actions']): ConfigurationSigningKey {
  return {
    id,
    label: { label: id },
    active: !actions.includes('ACTIVATE'),
    available: true,
    created_at: '2024-01-01T00:00:00Z',
    possible_actions: actions,
    source_type: sourceType,
    key_algorithm: 'RSA',
  };
}

function loggedOutToken(keys: ConfigurationSigningKey[] = []): Token {
  return {
    id: TOKEN_ID,
    name: TOKEN_NAME,
    active: false,
    available: true,
    logged_in: false,
    status: 'NOT_INITIALIZED',
    possible_actions: ['LOGIN'],
    configuration_signing_keys: keys,
  };
}

function loggedInToken(
  keys: ConfigurationSigningKey[] = [],
  canAddInternal = true,
  canAddExternal = true,
): Token {
  const actions: Token['possible_actions'] = ['LOGOUT'];
  if (canAddInternal) actions.push('GENERATE_INTERNAL_KEY');
  if (canAddExternal) actions.push('GENERATE_EXTERNAL_KEY');
  return {
    id: TOKEN_ID,
    name: TOKEN_NAME,
    active: true,
    available: true,
    logged_in: true,
    status: 'OK',
    possible_actions: actions,
    configuration_signing_keys: keys,
  };
}

const basePermissions = [
  Permissions.VIEW_INTERNAL_CONFIGURATION_SOURCE,
  Permissions.VIEW_EXTERNAL_CONFIGURATION_SOURCE,
  Permissions.VIEW_CONFIGURATION_MANAGEMENT,
  Permissions.ACTIVATE_TOKEN,
  Permissions.DEACTIVATE_TOKEN,
  Permissions.GENERATE_SIGNING_KEY,
  Permissions.ACTIVATE_SIGNING_KEY,
  Permissions.DELETE_SIGNING_KEY,
];

function anchorHandlers() {
  return [
    specHttp.get('/configuration-sources/{configuration_type}/anchor', ({ response }) =>
      response(200).json({ anchor: { hash: 'abc123', created_at: '2024-01-01T00:00:00Z' } }),
    ),
    specHttp.get('/configuration-sources/{configuration_type}/download-url', ({ response }) =>
      response(200).json({ url: 'http://example.com/anchor' }),
    ),
    specHttp.get('/configuration-sources/{configuration_type}/configuration-parts', ({ response }) =>
      response(200).json([]),
    ),
  ];
}

// Helper: expand the token panel by clicking its header link
async function expandToken() {
  await expect.element(page.getByTestId('token-name')).toBeVisible();
  await page.getByTestId('token-name').click();
}

CONFIG_ROUTES.forEach(({ label, path, sourceType }) => {
  describe(`0600/0610 — CS Signing Keys [${label}] — Add Key disabled on logged-out token (Browser Mode)`, () => {
    it('Add Key button is disabled when the token is logged out', async () => {
      await renderRoute(path, {
        permissions: basePermissions,
        msw: [
          specHttp.get('/tokens', ({ response }) => response(200).json([loggedOutToken()])),
          ...anchorHandlers(),
        ],
      });

      await expandToken();

      const addKeyBtn = page.getByTestId('token-add-key-button');
      await expect.element(addKeyBtn).toBeVisible();
      await expect.element(addKeyBtn).toBeDisabled();
    });
  });

  describe(`0600/0610 — CS Signing Keys [${label}] — Add Key enabled after login, disabled after 2 keys (Browser Mode)`, () => {
    it('Add Key is enabled on a logged-in token; disabled once 2 keys are present (count-cap gating)', async () => {
      const key1 = makeKey('key-1', sourceType, ['DELETE']);
      const key2 = makeKey('key-2', sourceType, ['DELETE']);

      let tokensCallCount = 0;
      await renderRoute(path, {
        permissions: basePermissions,
        msw: [
          specHttp.get('/tokens', ({ response }) => {
            tokensCallCount += 1;
            if (tokensCallCount === 1) return response(200).json([loggedInToken([])]);
            if (tokensCallCount === 2) return response(200).json([loggedInToken([key1])]);
            return response(200).json([loggedInToken([key1, key2], false, false)]);
          }),
          specHttp.post('/configuration-sources/{configuration_type}/signing-keys', ({ response }) =>
            response(201).json(key1),
          ),
          ...anchorHandlers(),
        ],
      });

      await expandToken();

      const addKeyBtn = page.getByTestId('token-add-key-button');
      await expect.element(addKeyBtn).toBeVisible();
      await expect.element(addKeyBtn).not.toBeDisabled();

      // Add first key
      await addKeyBtn.click();
      await expect.element(page.getByTestId('signing-key-label-input')).toBeVisible();
      await page.getByTestId('signing-key-label-input').getByRole('textbox').fill('key_1');
      await expect.element(page.getByTestId('dialog-save-button')).not.toBeDisabled();
      await page.getByTestId('dialog-save-button').click();

      // Add second key: on re-fetch token now has 1 key; next fetch (after 2nd add) returns 2 keys + no generate actions
      await expect.element(page.getByTestId('token-add-key-button')).toBeVisible();
      await page.getByTestId('token-add-key-button').click();
      await expect.element(page.getByTestId('signing-key-label-input')).toBeVisible();
      await page.getByTestId('signing-key-label-input').getByRole('textbox').fill('key_2');
      await page.getByTestId('dialog-save-button').click();

      await expect.element(page.getByTestId('token-add-key-button')).toBeDisabled();
    });
  });

  describe(`0600/0610 — CS Signing Keys [${label}] — activate signing key (Browser Mode)`, () => {
    it('clicking Activate on key_2 triggers activate call; key_1 then has the activate button and key_2 does not', async () => {
      // key_1 is active (no ACTIVATE action), key_2 is inactive (has ACTIVATE)
      const key1Active = makeKey('key-1', sourceType, []);
      const key2Inactive = makeKey('key-2', sourceType, ['ACTIVATE', 'DELETE']);

      const key1AfterSwitch = makeKey('key-1', sourceType, ['ACTIVATE', 'DELETE']);
      const key2AfterSwitch = makeKey('key-2', sourceType, []);

      let tokensCallCount = 0;
      await renderRoute(path, {
        permissions: basePermissions,
        msw: [
          specHttp.get('/tokens', ({ response }) => {
            tokensCallCount += 1;
            if (tokensCallCount === 1) return response(200).json([loggedInToken([key1Active, key2Inactive])]);
            return response(200).json([loggedInToken([key1AfterSwitch, key2AfterSwitch])]);
          }),
          specHttp.put('/signing-keys/{sign_key_id}/activate', ({ response }) => response(204).empty()),
          ...anchorHandlers(),
        ],
      });

      await expandToken();

      // keyLabel() returns key.id when label.label is absent — so data-test uses key.id
      // key-2 has activate button (inactive); key-1 does not (already active)
      await expect.element(page.getByTestId('key-key-2-activate-button')).toBeVisible();
      expect(page.getByTestId('key-key-1-activate-button').query()).toBeNull();

      await page.getByTestId('key-key-2-activate-button').click();
      await expect.element(page.getByTestId('dialog-save-button')).toBeVisible();
      await page.getByTestId('dialog-save-button').click();

      // After re-fetch: key-1 now has activate button; key-2 does not
      await expect.element(page.getByTestId('key-key-1-activate-button')).toBeVisible();
      await expect.element(page.getByTestId('key-key-2-activate-button')).not.toBeInTheDocument();
    });
  });

  describe(`0600/0610 — CS Signing Keys [${label}] — delete and activate disabled when token logged out (Browser Mode)`, () => {
    it('activate and delete buttons disappear from keys after token logout', async () => {
      const keyWithActions = makeKey('key-1', sourceType, ['ACTIVATE', 'DELETE']);
      const keyNoActions = makeKey('key-1', sourceType, []);

      let tokensCallCount = 0;
      await renderRoute(path, {
        permissions: basePermissions,
        msw: [
          specHttp.get('/tokens', ({ response }) => {
            tokensCallCount += 1;
            if (tokensCallCount === 1) return response(200).json([loggedInToken([keyWithActions])]);
            return response(200).json([loggedOutToken([keyNoActions])]);
          }),
          specHttp.put('/tokens/{token_id}/logout', ({ response }) => response(200).json(loggedOutToken())),
          ...anchorHandlers(),
        ],
      });

      await expandToken();

      // Both activate and delete buttons present before logout (data-test uses key.id)
      await expect.element(page.getByTestId('key-key-1-activate-button')).toBeVisible();
      await expect.element(page.getByTestId('key-key-1-delete-button')).toBeVisible();

      await page.getByTestId('token-logout-button').click();
      await expect.element(page.getByTestId('dialog-save-button')).toBeVisible();
      await page.getByTestId('dialog-save-button').click();

      // After logout re-fetch: login button appears, no activate or delete buttons
      await expect.element(page.getByTestId('token-login-button')).toBeVisible();
      await expect.element(page.getByTestId('key-key-1-activate-button')).not.toBeInTheDocument();
      await expect.element(page.getByTestId('key-key-1-delete-button')).not.toBeInTheDocument();
    });
  });

  describe(`0600/0610 — CS Signing Keys [${label}] — delete signing key (Browser Mode)`, () => {
    it('clicking Delete on key_1 removes it from the list and re-enables Add Key', async () => {
      const keyWithDelete = makeKey('key-1', sourceType, ['DELETE']);

      let tokensCallCount = 0;
      await renderRoute(path, {
        permissions: basePermissions,
        msw: [
          specHttp.get('/tokens', ({ response }) => {
            tokensCallCount += 1;
            if (tokensCallCount === 1) return response(200).json([loggedInToken([keyWithDelete])]);
            return response(200).json([loggedInToken([])]);
          }),
          specHttp.delete('/signing-keys/{sign_key_id}', ({ response }) => response(204).empty()),
          ...anchorHandlers(),
        ],
      });

      await expandToken();

      await expect.element(page.getByTestId('key-key-1-delete-button')).toBeVisible();

      await page.getByTestId('key-key-1-delete-button').click();
      await expect.element(page.getByTestId('dialog-save-button')).toBeVisible();
      await page.getByTestId('dialog-save-button').click();

      // key-1 gone from list; Add Key re-enabled
      await expect.element(page.getByTestId('key-key-1-delete-button')).not.toBeInTheDocument();
      await expect.element(page.getByTestId('token-add-key-button')).not.toBeDisabled();
    });
  });

  describe(`0600/0610 — CS Signing Keys [${label}] — logout disables Add Key (Browser Mode)`, () => {
    it('Add Key button becomes disabled after token logout', async () => {
      let tokensCallCount = 0;
      await renderRoute(path, {
        permissions: basePermissions,
        msw: [
          specHttp.get('/tokens', ({ response }) => {
            tokensCallCount += 1;
            if (tokensCallCount === 1) return response(200).json([loggedInToken([])]);
            return response(200).json([loggedOutToken([])]);
          }),
          specHttp.put('/tokens/{token_id}/logout', ({ response }) => response(200).json(loggedOutToken())),
          ...anchorHandlers(),
        ],
      });

      await expandToken();

      await expect.element(page.getByTestId('token-add-key-button')).not.toBeDisabled();

      await page.getByTestId('token-logout-button').click();
      await expect.element(page.getByTestId('dialog-save-button')).toBeVisible();
      await page.getByTestId('dialog-save-button').click();

      await expect.element(page.getByTestId('token-add-key-button')).toBeDisabled();
    });
  });
});
