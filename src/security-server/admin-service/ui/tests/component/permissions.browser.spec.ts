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
 * Role gating: pass `{ permissions }` to `renderRoute`. `renderRoute` calls
 * `useUser().setPermissions()` before mount, which computes `bannedRoutes` from
 * `routePermissions`. Assertions target the rendered DOM only — no store internals.
 *
 * Main nav tabs: `[data-test="main-navigation-item"]` elements; their text
 *   is in a child `[data-test="main-navigation-item-name"]`.
 * Keys sub-tabs: `[data-test="sign-and-auth-keys-tab-button"]`,
 *   `[data-test="api-key-tab-button"]`, `[data-test="ss-tls-certificate-tab-button"]`.
 * Settings sub-tabs: `[data-test="backup-and-restore-tab-button"]`.
 * Add-client button: `[data-test="add-client-button"]`.
 */
import { describe, it, expect } from 'vitest';
import { page } from 'vitest/browser';
import { renderRoute } from '../setup/render-route';
import { specHttp } from '../setup/spec-http';
import { Permissions } from '@/global';
import { clientsFixture } from '../setup/msw-handlers';

function mainNavTabNames(): string[] {
  return page
    .getByTestId('main-navigation-item-name')
    .elements()
    .map((el) => el.textContent?.trim() ?? '');
}

const clientsHandler = specHttp.get('/clients', ({ response }) => response(200).json(clientsFixture));

// MIGRATED-FROM: 0700-ss-permissions.feature :: "System administrator sees only relevant pages"
describe('Permissions — System administrator (Browser Mode)', () => {
  it('shows Keys, Diagnostics and Settings tabs but not Clients tab', async () => {
    const sysAdminPermissions = [
      Permissions.VIEW_KEYS,
      Permissions.VIEW_SYS_PARAMS,
      Permissions.DIAGNOSTICS,
      Permissions.GENERATE_KEY,
      Permissions.GENERATE_INTERNAL_TLS_KEY_CERT,
      Permissions.VIEW_INTERNAL_TLS_CERT,
      Permissions.VIEW_ADMIN_USERS,
    ];

    await renderRoute('/clients', {
      permissions: sysAdminPermissions,
      msw: [clientsHandler],
    });

    await expect.element(page.getByTestId('main-navigation-item').first()).toBeVisible();

    const names = mainNavTabNames();
    expect(names).not.toContain('Clients');
    expect(names).toContain('Settings');
    expect(names).toContain('Diagnostics');
    expect(names).toContain('Keys and certificates');
  });
});

// MIGRATED-FROM: 0700-ss-permissions.feature :: "Registration officer sees only relevant pages"
describe('Permissions — Registration officer (Browser Mode)', () => {
  it('shows Clients and add-client button; hides Settings and Diagnostics', async () => {
    const regOfficerPermissions = [
      Permissions.VIEW_CLIENTS,
      Permissions.ADD_CLIENT,
      Permissions.VIEW_CLIENT_DETAILS,
      Permissions.VIEW_KEYS,
      Permissions.VIEW_INTERNAL_TLS_CERT,
      Permissions.EXPORT_INTERNAL_TLS_CERT,
    ];

    await renderRoute('/clients', {
      permissions: regOfficerPermissions,
      msw: [clientsHandler],
    });

    await expect.element(page.getByTestId('client-name').first()).toBeVisible();

    const names = mainNavTabNames();
    expect(names).toContain('Clients');
    expect(names).not.toContain('Settings');
    expect(names).not.toContain('Diagnostics');

    await expect.element(page.getByTestId('add-client-button')).toBeVisible();
  });
});

// MIGRATED-FROM: 0700-ss-permissions.feature :: "Security officer sees only relevant pages"
describe('Permissions — Security officer (Browser Mode)', () => {
  it('shows Clients and Settings but not Diagnostics; no add-client button', async () => {
    const secOfficerPermissions = [
      Permissions.VIEW_CLIENTS,
      Permissions.VIEW_CLIENT_DETAILS,
      Permissions.VIEW_KEYS,
      Permissions.ACTIVATE_DEACTIVATE_TOKEN,
      Permissions.UPDATE_TOKEN_PIN,
      Permissions.GENERATE_INTERNAL_TLS_KEY_CERT,
      Permissions.VIEW_INTERNAL_TLS_CERT,
      Permissions.EXPORT_INTERNAL_TLS_CERT,
      Permissions.VIEW_SYS_PARAMS,
    ];

    await renderRoute('/clients', {
      permissions: secOfficerPermissions,
      msw: [clientsHandler],
    });

    await expect.element(page.getByTestId('client-name').first()).toBeVisible();

    const names = mainNavTabNames();
    expect(names).toContain('Clients');
    expect(names).toContain('Settings');
    expect(names).not.toContain('Diagnostics');

    expect(page.getByTestId('add-client-button').query()).toBeNull();
  });
});

// MIGRATED-FROM: 0700-ss-permissions.feature :: "Observer sees only relevant pages"
describe('Permissions — Observer (Browser Mode)', () => {
  it('shows Clients, Settings and Diagnostics; no add-client button', async () => {
    const observerPermissions = [
      Permissions.VIEW_CLIENTS,
      Permissions.VIEW_CLIENT_DETAILS,
      Permissions.VIEW_CLIENT_LOCAL_GROUPS,
      Permissions.VIEW_KEYS,
      Permissions.VIEW_API_KEYS,
      Permissions.DIAGNOSTICS,
      Permissions.VIEW_SYS_PARAMS,
    ];

    await renderRoute('/clients', {
      permissions: observerPermissions,
      msw: [clientsHandler],
    });

    await expect.element(page.getByTestId('client-name').first()).toBeVisible();

    const names = mainNavTabNames();
    expect(names).toContain('Clients');
    expect(names).toContain('Settings');
    expect(names).toContain('Diagnostics');

    expect(page.getByTestId('add-client-button').query()).toBeNull();
  });
});

// MIGRATED-FROM: 0700-ss-permissions.feature :: "Service administrator sees only relevant pages"
describe('Permissions — Service administrator (Browser Mode)', () => {
  it('shows Clients and Keys tabs; hides Settings and Diagnostics; no add-client button', async () => {
    const serviceAdminPermissions = [
      Permissions.VIEW_CLIENTS,
      Permissions.VIEW_CLIENT_DETAILS,
      Permissions.VIEW_KEYS,
      Permissions.VIEW_CLIENT_LOCAL_GROUPS,
      Permissions.ADD_LOCAL_GROUP,
      Permissions.EDIT_LOCAL_GROUP_DESC,
      Permissions.EDIT_LOCAL_GROUP_MEMBERS,
    ];

    await renderRoute('/clients', {
      permissions: serviceAdminPermissions,
      msw: [clientsHandler],
    });

    await expect.element(page.getByTestId('client-name').first()).toBeVisible();

    const names = mainNavTabNames();
    expect(names).toContain('Clients');
    expect(names).toContain('Keys and certificates');
    expect(names).not.toContain('Settings');
    expect(names).not.toContain('Diagnostics');

    expect(page.getByTestId('add-client-button').query()).toBeNull();
  });
});
