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
import { clientSchema } from '../setup/schemas';
import { Permissions } from '@/global';
import type { Client } from '@/openapi-types';
import { useUser } from '@/store/modules/user';
import { worker } from '../setup/browser-setup';

// ── Fixtures ──────────────────────────────────────────────────────────────────

const localClientsFixture: Client[] = [
  {
    id: 'DEV:COM:1234',
    instance_id: 'DEV',
    member_name: 'Test member',
    member_class: 'COM',
    member_code: '1234',
    owner: true,
    has_valid_local_sign_cert: true,
    connection_type: 'HTTPS',
    status: 'REGISTERED',
  },
];

const selectableClientsFixture: Client[] = [
  {
    id: 'DEV:COM:4321:TestClient',
    instance_id: 'DEV',
    member_name: 'Other Corp',
    member_class: 'COM',
    member_code: '4321',
    subsystem_code: 'TestClient',
    owner: false,
    has_valid_local_sign_cert: false,
    connection_type: 'HTTPS',
    status: 'REGISTERED',
  },
];

validateBody({ type: 'array', items: clientSchema }, localClientsFixture);
validateBody({ type: 'array', items: clientSchema }, selectableClientsFixture);

// ── Permissions ───────────────────────────────────────────────────────────────

const addClientPermissions = [
  Permissions.VIEW_CLIENTS,
  Permissions.ADD_CLIENT,
  Permissions.VIEW_CLIENT_DETAILS,
];

// ── Base MSW handlers needed for the AddClient wizard to mount ────────────────

function baseHandlers() {
  return [
    // fetchSelectableClients: global clients filtered list
    specHttp.get('/clients', ({ request, response }) => {
      const url = new URL(request.url);
      if (url.searchParams.get('exclude_local') === 'true') {
        return response(200).json(selectableClientsFixture);
      }
      // local clients + any other GET /clients call
      return response(200).json(localClientsFixture);
    }),
    // fetchMemberClassesForCurrentInstance
    specHttp.get('/member-classes', ({ response }) => response(200).json(['COM', 'GOV'])),
    // fetchCertificateAuthorities (called onMounted by AddClient.vue)
    specHttp.get('/certificate-authorities', ({ response }) => response(200).json([])),
  ];
}

// ── Specs ─────────────────────────────────────────────────────────────────────

describe('Add client — cancelled (Browser Mode)', () => {
  it('opens the add-client wizard on step 1, cancel navigates back to clients list', async () => {
    const { router } = await renderRoute('/clients', {
      permissions: addClientPermissions,
      msw: baseHandlers(),
    });

    useUser().$patch({
      currentSecurityServer: {
        id: 'DEV:COM:1234:SS1',
        instance_id: 'DEV',
        member_class: 'COM',
        member_code: '1234',
        server_code: 'SS1',
      },
    });

    await router.push({ name: 'add-client' });

    await expect.element(page.getByTestId('cancel-button').first()).toBeVisible();
    await expect.element(page.getByTestId('select-client-button')).toBeVisible();

    await page.getByTestId('cancel-button').first().click();

    await expect.element(page.getByTestId('select-client-button')).not.toBeInTheDocument();
    expect(router.currentRoute.value.path).toBe('/clients');
  });
});

describe('Add client — cancelled at wizard step 2 (Browser Mode)', () => {
  it('advances to Token step, cancels, and fires no POST /clients', async () => {
    let postClientsFired = false;
    worker.use(
      specHttp.post('/clients', ({ response }) => {
        postClientsFired = true;
        return response(500).empty();
      }),
    );

    const { router } = await renderRoute('/clients', {
      permissions: addClientPermissions,
      msw: [
        ...baseHandlers(),
        specHttp.get('/tokens', ({ response }) => response(200).json([])),
      ],
    });

    useUser().$patch({
      currentSecurityServer: {
        id: 'DEV:COM:1234:SS1',
        instance_id: 'DEV',
        member_class: 'COM',
        member_code: '1234',
        server_code: 'SS1',
      },
    });

    await router.push({ name: 'add-client' });
    await expect.element(page.getByTestId('select-client-button')).toBeVisible();

    await page.getByTestId('select-client-button').click();
    await expect.element(page.getByTestId('client-search-input')).toBeVisible();
    await page.getByRole('radio').first().click();
    await page.getByTestId('dialog-save-button').click();
    await expect.element(page.getByTestId('client-search-input')).not.toBeInTheDocument();

    await expect.element(page.getByTestId('next-button')).not.toBeDisabled();
    await page.getByTestId('next-button').click();
    await expect.element(page.getByTestId('previous-button')).toBeVisible();

    await page.getByTestId('cancel-button').nth(1).click();
    await expect.element(page.getByTestId('select-client-button')).not.toBeInTheDocument();
    expect(router.currentRoute.value.path).toBe('/clients');

    expect(postClientsFired).toBe(false);
  });
});

describe('Add client — existing client wizard flow (Browser Mode)', () => {
  it('opens select-client dialog, picks a client, fills form, next progresses wizard', async () => {
    const { router } = await renderRoute('/clients', {
      permissions: addClientPermissions,
      msw: baseHandlers(),
    });

    useUser().$patch({
      currentSecurityServer: {
        id: 'DEV:COM:1234:SS1',
        instance_id: 'DEV',
        member_class: 'COM',
        member_code: '1234',
        server_code: 'SS1',
      },
    });

    worker.use(
      specHttp.get('/clients', ({ request, response }) => {
        const url = new URL(request.url);
        if (url.searchParams.get('exclude_local') === 'true') {
          return response(200).json(selectableClientsFixture);
        }
        return response(200).json(localClientsFixture);
      }),
      specHttp.get('/tokens', ({ response }) => response(200).json([])),
    );

    await router.push({ name: 'add-client' });

    await expect.element(page.getByTestId('select-client-button')).toBeVisible();
    await expect.element(page.getByTestId('next-button')).toBeVisible();

    await page.getByTestId('select-client-button').click();

    await expect.element(page.getByTestId('client-search-input')).toBeVisible();

    await page.getByRole('radio').first().click();

    await page.getByTestId('dialog-save-button').click();

    await expect.element(page.getByTestId('client-search-input')).not.toBeInTheDocument();

    await expect.element(page.getByTestId('member-code-input').getByRole('textbox')).toBeVisible();
    const memberCodeInput = page.getByTestId('member-code-input').getByRole('textbox');
    const memberCodeValue = (await memberCodeInput.element() as HTMLInputElement).value;
    expect(memberCodeValue).toBe('4321');

    const subsystemCodeInput = page.getByTestId('subsystem-code-input').getByRole('textbox');
    const subsystemCodeValue = (await subsystemCodeInput.element() as HTMLInputElement).value;
    expect(subsystemCodeValue).toBe('TestClient');

    await expect.element(page.getByTestId('next-button')).not.toBeDisabled();

    await page.getByTestId('next-button').click();

    await expect.element(page.getByTestId('previous-button')).toBeVisible();

    expect(router.currentRoute.value.path).toBe('/add-client');
  });
});
