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
import { clientsFixture } from '../setup/msw-handlers';
import type { Anchor, CertificateDetails, Token } from '@/openapi-types';
import { PossibleAction, TokenStatus, TokenType } from '@/openapi-types';

// ── AJV schemas ───────────────────────────────────────────────────────────────

const tokenSchema = {
  type: 'object',
  required: ['id', 'name', 'type', 'keys', 'status', 'logged_in', 'available', 'saved_to_configuration', 'read_only'],
  properties: {
    id: { type: 'string' },
    name: { type: 'string' },
    type: { type: 'string', enum: ['SOFTWARE', 'HARDWARE'] },
    keys: { type: 'array' },
    status: { type: 'string' },
    logged_in: { type: 'boolean' },
    available: { type: 'boolean' },
    saved_to_configuration: { type: 'boolean' },
    read_only: { type: 'boolean' },
  },
};

const certificateDetailsSchema = {
  type: 'object',
  required: [
    'issuer_distinguished_name',
    'issuer_common_name',
    'subject_distinguished_name',
    'subject_common_name',
    'not_before',
    'not_after',
    'serial',
    'version',
    'signature_algorithm',
    'signature',
    'public_key_algorithm',
    'rsa_public_key_modulus',
    'rsa_public_key_exponent',
    'hash',
    'key_usages',
    'subject_alternative_names',
  ],
  properties: {
    issuer_distinguished_name: { type: 'string' },
    issuer_common_name: { type: 'string' },
    subject_distinguished_name: { type: 'string' },
    subject_common_name: { type: 'string' },
    not_before: { type: 'string' },
    not_after: { type: 'string' },
    serial: { type: 'string' },
    version: { type: 'integer' },
    signature_algorithm: { type: 'string' },
    signature: { type: 'string' },
    public_key_algorithm: { type: 'string' },
    rsa_public_key_modulus: { type: 'string' },
    rsa_public_key_exponent: { type: 'integer' },
    hash: { type: 'string' },
    key_usages: { type: 'array' },
    subject_alternative_names: { type: 'string' },
  },
};

const anchorSchema = {
  type: 'object',
  required: ['hash', 'created_at'],
  properties: {
    hash: { type: 'string' },
    created_at: { type: 'string' },
  },
};

// ── Role-permission sets ───────────────────────────────────────────────────────

const sysAdminPermissions = [
  Permissions.VIEW_KEYS,
  Permissions.VIEW_SYS_PARAMS,
  Permissions.DIAGNOSTICS,
  Permissions.GENERATE_KEY,
  Permissions.GENERATE_INTERNAL_TLS_KEY_CERT,
  Permissions.VIEW_INTERNAL_TLS_CERT,
  Permissions.VIEW_ADMIN_USERS,
];

const regOfficerPermissions = [
  Permissions.VIEW_CLIENTS,
  Permissions.ADD_CLIENT,
  Permissions.VIEW_CLIENT_DETAILS,
  Permissions.VIEW_KEYS,
  Permissions.VIEW_INTERNAL_TLS_CERT,
  Permissions.EXPORT_INTERNAL_TLS_CERT,
];

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

const observerPermissions = [
  Permissions.VIEW_CLIENTS,
  Permissions.VIEW_CLIENT_DETAILS,
  Permissions.VIEW_CLIENT_LOCAL_GROUPS,
  Permissions.VIEW_KEYS,
  Permissions.VIEW_API_KEYS,
  Permissions.DIAGNOSTICS,
  Permissions.VIEW_SYS_PARAMS,
];

const serviceAdminPermissions = [
  Permissions.VIEW_CLIENTS,
  Permissions.VIEW_CLIENT_DETAILS,
  Permissions.VIEW_KEYS,
  Permissions.VIEW_CLIENT_LOCAL_GROUPS,
  Permissions.ADD_LOCAL_GROUP,
  Permissions.EDIT_LOCAL_GROUP_DESC,
  Permissions.EDIT_LOCAL_GROUP_MEMBERS,
];

// ── Fixtures ──────────────────────────────────────────────────────────────────

const loggedInTokenFixture: Token = {
  id: 'softToken-0',
  name: 'softToken-0',
  type: TokenType.SOFTWARE,
  keys: [],
  status: TokenStatus.OK,
  logged_in: true,
  available: true,
  saved_to_configuration: true,
  read_only: false,
  possible_actions: [PossibleAction.LOGOUT, PossibleAction.EDIT_FRIENDLY_NAME],
};

const tlsCertFixture: CertificateDetails = {
  issuer_distinguished_name: 'CN=Test CA',
  issuer_common_name: 'Test CA',
  subject_distinguished_name: 'CN=ss1',
  subject_common_name: 'ss1',
  not_before: '2024-01-01T00:00:00Z',
  not_after: '2099-12-31T00:00:00Z',
  serial: '1',
  version: 3,
  signature_algorithm: 'SHA256withRSA',
  signature: 'aabb',
  public_key_algorithm: 'RSA',
  rsa_public_key_modulus: 'aabb',
  rsa_public_key_exponent: 65537,
  hash: 'AABB:CCDD:EEFF',
  key_usages: [],
  subject_alternative_names: '',
};

const anchorFixture: Anchor = {
  hash: 'AA:BB:CC:DD',
  created_at: '2024-01-01T00:00:00Z',
};

validateBody(tokenSchema, loggedInTokenFixture);
validateBody(certificateDetailsSchema, tlsCertFixture);
validateBody(anchorSchema, anchorFixture);

// ── Shared handlers ───────────────────────────────────────────────────────────

const clientsHandler = specHttp.get('/clients', ({ response }) => response(200).json(clientsFixture));
const tokensHandler = specHttp.get('/tokens', ({ response }) => response(200).json([loggedInTokenFixture]));
const tlsCertHandler = specHttp.get('/system/certificate', ({ response }) => response(200).json(tlsCertFixture));
const anchorHandler = specHttp.get('/system/anchor', ({ response }) => response(200).json(anchorFixture));

// ── Helper ────────────────────────────────────────────────────────────────────

function mainNavTabNames(): string[] {
  return page
    .getByTestId('main-navigation-item-name')
    .elements()
    .map((el) => el.textContent?.trim() ?? '');
}

// ═══════════════════════════════════════════════════════════════════════════════
// System administrator
// ═══════════════════════════════════════════════════════════════════════════════

describe('Permissions — System administrator (Browser Mode)', () => {
  it('shows Keys, Diagnostics and Settings tabs but not Clients tab', async () => {
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

// ═══════════════════════════════════════════════════════════════════════════════
// Registration officer — nav tabs (existing) + deep gating (new)
// ═══════════════════════════════════════════════════════════════════════════════

describe('Permissions — Registration officer (Browser Mode)', () => {
  it('shows Clients and add-client button; hides Settings and Diagnostics', async () => {
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

  it('keys sub-tabs: sign-and-auth and TLS present; api-key absent', async () => {
    await renderRoute('/keys/sign-and-auth', {
      permissions: regOfficerPermissions,
      msw: [tokensHandler],
    });

    await expect.element(page.getByTestId('sign-and-auth-keys-tab-button')).toBeVisible();
    await expect.element(page.getByTestId('ss-tls-certificate-tab-button')).toBeVisible();
    expect(page.getByTestId('api-key-tab-button').query()).toBeNull();
  });

  it('token login/logout buttons absent (two-sided: present for security officer)', async () => {
    await renderRoute('/keys/sign-and-auth', {
      permissions: regOfficerPermissions,
      msw: [tokensHandler],
    });

    await expect.element(page.getByText('softToken-0').first()).toBeVisible();
    expect(page.getByTestId('token-login-button').query()).toBeNull();
    expect(page.getByTestId('token-logout-button').query()).toBeNull();

    // Contrast: security officer has ACTIVATE_DEACTIVATE_TOKEN → logout button appears
    await renderRoute('/keys/sign-and-auth', {
      permissions: secOfficerPermissions,
      msw: [tokensHandler],
    });

    await expect.element(page.getByText('softToken-0').first()).toBeVisible();
    await expect.element(page.getByTestId('token-logout-button').first()).toBeVisible();
  });

  it('TLS sub-tab: generate-key button absent (two-sided: present for security officer); export present', async () => {
    await renderRoute('/keys/tls-cert', {
      permissions: regOfficerPermissions,
      msw: [tlsCertHandler],
    });

    await expect.element(page.getByTestId('tls-certificates-view').first()).toBeVisible();
    expect(page.getByTestId('management-service-certificate-generateKey').query()).toBeNull();
    await expect.element(page.getByTestId('download-management-service-certificate')).toBeVisible();

    // Contrast: security officer has GENERATE_INTERNAL_TLS_KEY_CERT → generate-key button present
    await renderRoute('/keys/tls-cert', {
      permissions: secOfficerPermissions,
      msw: [tlsCertHandler],
    });

    await expect.element(page.getByTestId('management-service-certificate-generateKey').first()).toBeVisible();
  });
});

// ═══════════════════════════════════════════════════════════════════════════════
// Security officer — nav tabs (existing) + deep gating (new)
// ═══════════════════════════════════════════════════════════════════════════════

describe('Permissions — Security officer (Browser Mode)', () => {
  it('shows Clients and Settings but not Diagnostics; no add-client button', async () => {
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

  it('keys sub-tabs: api-key absent (two-sided: present for observer)', async () => {
    await renderRoute('/keys/sign-and-auth', {
      permissions: secOfficerPermissions,
      msw: [tokensHandler],
    });

    await expect.element(page.getByTestId('sign-and-auth-keys-tab-button')).toBeVisible();
    expect(page.getByTestId('api-key-tab-button').query()).toBeNull();

    // Contrast: observer has VIEW_API_KEYS → api-key tab present
    await renderRoute('/keys/sign-and-auth', {
      permissions: observerPermissions,
      msw: [tokensHandler],
    });

    await expect.element(page.getByTestId('api-key-tab-button')).toBeVisible();
  });

  it('settings: backup-and-restore tab absent (two-sided: present for role with BACKUP_CONFIGURATION)', async () => {
    await renderRoute('/settings', {
      permissions: secOfficerPermissions,
    });

    await expect.element(page.getByTestId('system-parameters-tab-button')).toBeVisible();
    expect(page.getByTestId('backup-and-restore-tab-button').query()).toBeNull();

    // Contrast: role with BACKUP_CONFIGURATION → backup tab present
    const withBackupPermissions = [
      Permissions.VIEW_SYS_PARAMS,
      Permissions.BACKUP_CONFIGURATION,
    ];

    await renderRoute('/settings', {
      permissions: withBackupPermissions,
    });

    await expect.element(page.getByTestId('system-parameters-tab-button').first()).toBeVisible();
    await expect.element(page.getByTestId('backup-and-restore-tab-button').first()).toBeVisible();
  });
});

// ═══════════════════════════════════════════════════════════════════════════════
// Observer — nav tabs (existing) + deep gating (new)
// ═══════════════════════════════════════════════════════════════════════════════

describe('Permissions — Observer (Browser Mode)', () => {
  it('shows Clients, Settings and Diagnostics; no add-client button', async () => {
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

  it('local groups: add-local-group button absent (two-sided: present for service administrator)', async () => {
    const clientId = 'CS:GOV:1234:SUBS1';
    const encodedClientId = encodeURIComponent(clientId);

    const clientHandler = specHttp.get('/clients/{id}', ({ response }) =>
      response(200).json({
        id: clientId,
        instance_id: 'CS',
        member_name: 'Test member',
        member_class: 'GOV',
        member_code: '1234',
        subsystem_code: 'SUBS1',
        owner: false,
        has_valid_local_sign_cert: false,
        connection_type: 'HTTPS' as const,
        status: 'REGISTERED' as const,
      }),
    );
    const localGroupsHandler = specHttp.get('/clients/{id}/local-groups', ({ response }) => response(200).json([]));

    await renderRoute(`/clients/subsystem/${encodedClientId}/local-groups`, {
      permissions: observerPermissions,
      msw: [clientHandler, clientsHandler, localGroupsHandler],
    });

    await expect.element(page.getByTestId('local-groups-table')).toBeVisible();
    expect(page.getByTestId('add-local-group-button').query()).toBeNull();

    // Contrast: service administrator has ADD_LOCAL_GROUP → button present
    await renderRoute(`/clients/subsystem/${encodedClientId}/local-groups`, {
      permissions: serviceAdminPermissions,
      msw: [clientHandler, clientsHandler, localGroupsHandler],
    });

    await expect.element(page.getByTestId('add-local-group-button')).toBeVisible();
  });

  it('token login/logout absent and add-key absent', async () => {
    await renderRoute('/keys/sign-and-auth', {
      permissions: observerPermissions,
      msw: [tokensHandler],
    });

    await expect.element(page.getByText('softToken-0').first()).toBeVisible();
    expect(page.getByTestId('token-login-button').query()).toBeNull();
    expect(page.getByTestId('token-logout-button').query()).toBeNull();
    expect(page.getByTestId('token-add-key-button').query()).toBeNull();
  });

  it('add-key button present for system administrator (two-sided contrast for add-key absent above)', async () => {
    await renderRoute('/keys/sign-and-auth', {
      permissions: sysAdminPermissions,
      msw: [tokensHandler],
    });

    await expect.element(page.getByTestId('token-name').first()).toBeVisible();
    await page.getByTestId('token-name').first().click();
    await expect.element(page.getByTestId('token-add-key-button').first()).toBeVisible();
  });

  it('TLS sub-tab: generate-key absent and export absent (two-sided: export present for registration officer)', async () => {
    const observerPermissions = [
      Permissions.VIEW_CLIENTS,
      Permissions.VIEW_CLIENT_DETAILS,
      Permissions.VIEW_CLIENT_LOCAL_GROUPS,
      Permissions.VIEW_KEYS,
      Permissions.VIEW_API_KEYS,
      Permissions.DIAGNOSTICS,
      Permissions.VIEW_SYS_PARAMS,
      Permissions.VIEW_INTERNAL_TLS_CERT,
    ];

    await renderRoute('/keys/tls-cert', {
      permissions: observerPermissions,
      msw: [tlsCertHandler],
    });

    await expect.element(page.getByTestId('tls-certificates-view').first()).toBeVisible();
    expect(page.getByTestId('management-service-certificate-generateKey').query()).toBeNull();
    expect(page.getByTestId('download-management-service-certificate').query()).toBeNull();

    // Contrast: registration officer has EXPORT_INTERNAL_TLS_CERT → export present
    await renderRoute('/keys/tls-cert', {
      permissions: regOfficerPermissions,
      msw: [tlsCertHandler],
    });

    await expect.element(page.getByTestId('download-management-service-certificate').first()).toBeVisible();
  });

  it('settings: backup-and-restore tab absent', async () => {
    await renderRoute('/settings', {
      permissions: observerPermissions,
    });

    await expect.element(page.getByTestId('system-parameters-tab-button')).toBeVisible();
    expect(page.getByTestId('backup-and-restore-tab-button').query()).toBeNull();
  });

  it('system-parameters: configuration anchor download/upload buttons absent (two-sided: download present with DOWNLOAD_ANCHOR)', async () => {
    const observerPermissions = [
      Permissions.VIEW_CLIENTS,
      Permissions.VIEW_CLIENT_DETAILS,
      Permissions.VIEW_CLIENT_LOCAL_GROUPS,
      Permissions.VIEW_KEYS,
      Permissions.VIEW_API_KEYS,
      Permissions.DIAGNOSTICS,
      Permissions.VIEW_SYS_PARAMS,
      Permissions.VIEW_ANCHOR,
    ];

    await renderRoute('/settings/system-parameters', {
      permissions: observerPermissions,
      msw: [anchorHandler],
    });

    await expect.element(page.getByTestId('system-parameters-tab-view')).toBeVisible();
    await expect.element(page.getByTestId('system-parameters-configuration-anchor-table-body')).toBeVisible();
    expect(page.getByTestId('system-parameters-configuration-anchor-download-button').query()).toBeNull();
    expect(page.getByTestId('system-parameters-configuration-anchor-upload-button').query()).toBeNull();

    // Contrast: role with DOWNLOAD_ANCHOR → download button present
    const withDownloadAnchorPermissions = [
      Permissions.VIEW_SYS_PARAMS,
      Permissions.VIEW_ANCHOR,
      Permissions.DOWNLOAD_ANCHOR,
    ];

    await renderRoute('/settings/system-parameters', {
      permissions: withDownloadAnchorPermissions,
      msw: [anchorHandler],
    });

    await expect.element(page.getByTestId('system-parameters-configuration-anchor-download-button')).toBeVisible();
  });
});

// ═══════════════════════════════════════════════════════════════════════════════
// Service administrator — nav tabs (existing) + deep gating (new)
// ═══════════════════════════════════════════════════════════════════════════════

describe('Permissions — Service administrator (Browser Mode)', () => {
  it('shows Clients and Keys tabs; hides Settings and Diagnostics; no add-client button', async () => {
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

  it('client details navigation link is clickable with VIEW_CLIENT_DETAILS', async () => {
    await renderRoute('/clients', {
      permissions: serviceAdminPermissions,
      msw: [clientsHandler],
    });

    await expect.element(page.getByTestId('client-name').first()).toBeVisible();

    const detailLinks = page.getByTestId('btn-client-details').elements();
    expect(detailLinks.length).toBeGreaterThan(0);
    const isClickable = detailLinks.some((el) => el.classList.contains('cursor-pointer'));
    expect(isClickable).toBe(true);
  });

  it('client details navigation link is not clickable without VIEW_CLIENT_DETAILS', async () => {
    const withoutDetailsPermissions = [
      Permissions.VIEW_CLIENTS,
      Permissions.VIEW_KEYS,
    ];

    await renderRoute('/clients', {
      permissions: withoutDetailsPermissions,
      msw: [clientsHandler],
    });

    await expect.element(page.getByTestId('client-name').first()).toBeVisible();

    const noDetailLinks = page.getByTestId('btn-client-details').elements();
    const noneClickable = noDetailLinks.every((el) => !el.classList.contains('cursor-pointer'));
    expect(noneClickable).toBe(true);
  });
});
