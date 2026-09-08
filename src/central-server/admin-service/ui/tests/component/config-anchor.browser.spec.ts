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
import type { ConfigurationSigningKey, Token, ConfigurationType } from '@/openapi-types';

// Parametrized over internal (0700) and external (0710)
const CONFIG_ROUTES = [
  { label: 'internal (0700)', path: '/global-configuration/internal-configuration', sourceType: 'INTERNAL' as ConfigurationType },
  { label: 'external (0710)', path: '/global-configuration/external-configuration', sourceType: 'EXTERNAL' as ConfigurationType },
] as const;

const TOKEN_ID = 'softToken-0';

const ANCHOR_V1 = { hash: 'aabbcc1111', created_at: '2024-01-01T10:00:00Z' };
const ANCHOR_V2 = { hash: 'ddeeff2222', created_at: '2024-06-01T12:00:00Z' };

function makeKey(id: string, sourceType: ConfigurationType): ConfigurationSigningKey {
  return {
    id,
    label: { label: id },
    active: true,
    available: true,
    created_at: '2024-01-01T00:00:00Z',
    possible_actions: ['DELETE'],
    source_type: sourceType,
    key_algorithm: 'RSA',
  };
}

function loggedInToken(keys: ConfigurationSigningKey[]): Token {
  return {
    id: TOKEN_ID,
    name: TOKEN_ID,
    active: true,
    available: true,
    logged_in: true,
    status: 'OK',
    possible_actions: ['LOGOUT', 'GENERATE_INTERNAL_KEY', 'GENERATE_EXTERNAL_KEY'],
    configuration_signing_keys: keys,
  };
}

const basePermissions = [
  Permissions.VIEW_INTERNAL_CONFIGURATION_SOURCE,
  Permissions.VIEW_EXTERNAL_CONFIGURATION_SOURCE,
  Permissions.VIEW_CONFIGURATION_MANAGEMENT,
  Permissions.GENERATE_SOURCE_ANCHOR,
  Permissions.DOWNLOAD_SOURCE_ANCHOR,
  Permissions.GENERATE_SIGNING_KEY,
  Permissions.DELETE_SIGNING_KEY,
  Permissions.DOWNLOAD_CONFIGURATION_PART,
];

function tokensHandler(token: Token) {
  return specHttp.get('/tokens', ({ response }) => response(200).json([token]));
}

function partsHandler() {
  return specHttp.get('/configuration-sources/{configuration_type}/configuration-parts', ({ response }) =>
    response(200).json([]),
  );
}

function downloadUrlHandler() {
  return specHttp.get('/configuration-sources/{configuration_type}/download-url', ({ response }) =>
    response(200).json({ url: 'http://example.com/anchor' }),
  );
}

CONFIG_ROUTES.forEach(({ label, path, sourceType }) => {
  describe(`0700/0710 — CS Config Anchor [${label}] — recreate anchor renders updated anchor info (Browser Mode)`, () => {
    it('clicking Re-create calls the API and the anchor card updates with a new hash and timestamp', async () => {
      let anchorCallCount = 0;
      await renderRoute(path, {
        permissions: basePermissions,
        msw: [
          tokensHandler(loggedInToken([])),
          specHttp.get('/configuration-sources/{configuration_type}/anchor', ({ response }) => {
            anchorCallCount += 1;
            return anchorCallCount === 1
              ? response(200).json({ anchor: ANCHOR_V1 })
              : response(200).json({ anchor: ANCHOR_V2 });
          }),
          specHttp.put('/configuration-sources/{configuration_type}/anchor/re-create', ({ response }) =>
            response(200).json(ANCHOR_V2),
          ),
          downloadUrlHandler(),
          partsHandler(),
        ],
      });

      // Initial anchor hash visible
      await expect.element(page.getByTestId('anchor-hash')).toBeVisible();
      await expect.element(page.getByTestId('anchor-hash')).toHaveTextContent('aabbcc1111');

      await page.getByTestId('re-create-anchor-button').click();

      // Updated anchor hash shown after recreate
      await expect.element(page.getByTestId('anchor-hash')).toHaveTextContent('ddeeff2222');
    });
  });

  describe(`0700/0710 — CS Config Anchor [${label}] — download anchor trigger renders (Browser Mode)`, () => {
    it('download button is visible and triggers the download action', async () => {
      // Intercept the blob download to prevent browser navigation
      const downloadSpy = vi.fn();
      await renderRoute(path, {
        permissions: basePermissions,
        msw: [
          tokensHandler(loggedInToken([])),
          specHttp.get('/configuration-sources/{configuration_type}/anchor', ({ response }) =>
            response(200).json({ anchor: ANCHOR_V1 }),
          ),
          specHttp.untyped.get('/api/v1/configuration-sources/:configuration_type/anchor/download', () => {
            downloadSpy();
            return new HttpResponse(new Blob(['<anchor/>'], { type: 'application/xml' }), { status: 200 });
          }),
          downloadUrlHandler(),
          partsHandler(),
        ],
      });

      const downloadBtn = page.getByTestId('download-anchor-button');
      await expect.element(downloadBtn).toBeVisible();
      await downloadBtn.click();

      // Download endpoint was called
      expect(downloadSpy).toHaveBeenCalled();
    });
  });

  describe(`0700/0710 — CS Config Anchor [${label}] — anchor updates when signing key added (Browser Mode)`, () => {
    it('adding a signing key triggers anchor re-fetch; updated anchor hash is displayed', async () => {
      const key1 = makeKey('key-1', sourceType);
      const newKey = makeKey('key-new', sourceType);

      let anchorCallCount = 0;
      let tokensCallCount = 0;
      await renderRoute(path, {
        permissions: [
          ...basePermissions,
          Permissions.GENERATE_SIGNING_KEY,
        ],
        msw: [
          specHttp.get('/tokens', ({ response }) => {
            tokensCallCount += 1;
            if (tokensCallCount === 1) return response(200).json([loggedInToken([key1])]);
            return response(200).json([loggedInToken([key1, newKey])]);
          }),
          specHttp.get('/configuration-sources/{configuration_type}/anchor', ({ response }) => {
            anchorCallCount += 1;
            return anchorCallCount === 1
              ? response(200).json({ anchor: ANCHOR_V1 })
              : response(200).json({ anchor: ANCHOR_V2 });
          }),
          specHttp.post('/configuration-sources/{configuration_type}/signing-keys', ({ response }) =>
            response(201).json(newKey),
          ),
          downloadUrlHandler(),
          partsHandler(),
        ],
      });

      // One cert initially
      await expect.element(page.getByTestId('anchor-hash')).toBeVisible();
      await expect.element(page.getByTestId('anchor-hash')).toHaveTextContent('aabbcc1111');

      // Expand token and add a key
      await expect.element(page.getByTestId('token-name')).toBeVisible();
      await page.getByTestId('token-name').click();

      const addKeyBtn = page.getByTestId('token-add-key-button');
      await expect.element(addKeyBtn).toBeVisible();
      await addKeyBtn.click();

      await expect.element(page.getByTestId('signing-key-label-input')).toBeVisible();
      await page.getByTestId('signing-key-label-input').getByRole('textbox').fill('new-key');
      await page.getByTestId('dialog-save-button').click();

      // Anchor hash updated after key add re-triggers anchor fetch
      await expect.element(page.getByTestId('anchor-hash')).toHaveTextContent('ddeeff2222');
    });
  });

  describe(`0700/0710 — CS Config Anchor [${label}] — anchor updates when signing key deleted (Browser Mode)`, () => {
    it('deleting a signing key triggers anchor re-fetch; updated anchor hash is displayed', async () => {
      const key1 = makeKey('key-1', sourceType);
      const key2 = { ...makeKey('key-2', sourceType), possible_actions: ['DELETE'] as ConfigurationSigningKey['possible_actions'] };

      let anchorCallCount = 0;
      let tokensCallCount = 0;
      await renderRoute(path, {
        permissions: basePermissions,
        msw: [
          specHttp.get('/tokens', ({ response }) => {
            tokensCallCount += 1;
            if (tokensCallCount === 1) return response(200).json([loggedInToken([key1, key2])]);
            return response(200).json([loggedInToken([key1])]);
          }),
          specHttp.get('/configuration-sources/{configuration_type}/anchor', ({ response }) => {
            anchorCallCount += 1;
            return anchorCallCount === 1
              ? response(200).json({ anchor: ANCHOR_V2 })
              : response(200).json({ anchor: ANCHOR_V1 });
          }),
          specHttp.delete('/signing-keys/{sign_key_id}', ({ response }) => response(204).empty()),
          downloadUrlHandler(),
          partsHandler(),
        ],
      });

      await expect.element(page.getByTestId('anchor-hash')).toBeVisible();
      await expect.element(page.getByTestId('anchor-hash')).toHaveTextContent('ddeeff2222');

      // Expand token and delete key-2
      await expect.element(page.getByTestId('token-name')).toBeVisible();
      await page.getByTestId('token-name').click();

      await expect.element(page.getByTestId('key-key-2-delete-button')).toBeVisible();
      await page.getByTestId('key-key-2-delete-button').click();
      await expect.element(page.getByTestId('dialog-save-button')).toBeVisible();
      await page.getByTestId('dialog-save-button').click();

      // Anchor hash updated after key delete re-triggers anchor fetch
      await expect.element(page.getByTestId('anchor-hash')).toHaveTextContent('aabbcc1111');
    });
  });
});
