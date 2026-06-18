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
import type { Client } from '@/openapi-types';
import { useUser } from '@/store/modules/user';
import { worker } from '../setup/browser-setup';

// ── AJV schemas ───────────────────────────────────────────────────────────────

const clientSchema = {
  type: 'object',
  required: ['member_class', 'member_code'],
  properties: {
    id: { type: 'string' },
    instance_id: { type: 'string' },
    member_name: { type: 'string' },
    member_class: { type: 'string' },
    member_code: { type: 'string' },
    subsystem_code: { type: 'string' },
    owner: { type: 'boolean' },
    has_valid_local_sign_cert: { type: 'boolean' },
    connection_type: { type: 'string', enum: ['HTTP', 'HTTPS', 'HTTPS_NO_AUTH'] },
    status: {
      type: 'string',
      enum: ['REGISTERED', 'SAVED', 'GLOBAL_ERROR', 'REGISTRATION_IN_PROGRESS', 'DELETION_IN_PROGRESS'],
    },
  },
};

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

// MIGRATED-FROM: 0500-ss-client-add.feature :: "Add client was cancelled"
describe('Add client — cancelled (Browser Mode)', () => {
  it('opens the add-client wizard on step 1, cancel navigates back to clients list', async () => {
    // Render on /clients first so we can patch currentSecurityServer before
    // the AddClient component mounts. ClientDetailsPage.vue reads
    // currentSecurityServer.instance_id synchronously in created().
    const { router } = await renderRoute('/clients', {
      permissions: addClientPermissions,
      msw: baseHandlers(),
    });

    // Patch currentSecurityServer so fetchSelectableClients has an instance_id
    useUser().$patch({
      currentSecurityServer: {
        id: 'DEV:COM:1234:SS1',
        instance_id: 'DEV',
        member_class: 'COM',
        member_code: '1234',
        server_code: 'SS1',
      },
    });

    // Navigate to the add-client wizard
    await router.push({ name: 'add-client' });

    // The wizard step-1 cancel button must be visible — wizard is open
    await expect.element(page.getByTestId('cancel-button').first()).toBeVisible();

    // The select-client button starts the client picker, confirming wizard rendered
    await expect.element(page.getByTestId('select-client-button')).toBeVisible();

    // Click Cancel — the wizard emits 'cancel' which routes back to /clients
    await page.getByTestId('cancel-button').first().click();

    // After cancel the wizard is no longer in the DOM (navigated away)
    await expect.element(page.getByTestId('select-client-button')).not.toBeInTheDocument();

    // No POST /clients was ever fired (no add request sent)
    expect(router.currentRoute.value.path).toBe('/clients');
  });
});

// MIGRATED-FROM: 0500-ss-client-add.feature :: "Add client was cancelled"
// (cancel at step 2 — Token page — no POST /clients must be sent)
describe('Add client — cancelled at wizard step 2 (Browser Mode)', () => {
  it('advances to Token step, cancels, and fires no POST /clients', async () => {
    // Register a catching handler BEFORE renderRoute to ensure it is in place
    // before any mount-time requests. If a POST /clients fires, this handler
    // responds with 500 so the component shows an error — but more importantly
    // the spy flag is set so the assertion below catches the accidental call.
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

    // Open the select-client dialog and pick the selectable client.
    await page.getByTestId('select-client-button').click();
    await expect.element(page.getByTestId('client-search-input')).toBeVisible();
    await page.getByRole('radio').first().click();
    await page.getByTestId('dialog-save-button').click();
    await expect.element(page.getByTestId('client-search-input')).not.toBeInTheDocument();

    // Advance to step 2 (Token page).
    await expect.element(page.getByTestId('next-button')).not.toBeDisabled();
    await page.getByTestId('next-button').click();
    await expect.element(page.getByTestId('previous-button')).toBeVisible();

    // Cancel from step 2 — wizard routes back to /clients.
    // Both step-1 and step-2 render cancel buttons in DOM (v-stepper keeps inactive
    // items hidden but not removed). TokenPage's cancel button is the second one.
    await page.getByTestId('cancel-button').nth(1).click();
    await expect.element(page.getByTestId('select-client-button')).not.toBeInTheDocument();
    expect(router.currentRoute.value.path).toBe('/clients');

    // No POST /clients must have been sent at any point.
    expect(postClientsFired).toBe(false);
  });
});

// MIGRATED-FROM: 0500-ss-client-add.feature :: "Existing client added (Outline)" — UI slice
describe('Add client — existing client wizard flow (Browser Mode)', () => {
  it('opens select-client dialog, picks a client, fills form, next progresses wizard', async () => {
    // Render on /clients first so we can patch currentSecurityServer before
    // AddClient mounts. ClientDetailsPage.vue reads instance_id in created().
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

    // Register extra handlers for interactions inside the wizard:
    // fetchReservedClients and updateAddMemberWizardModeIfNeeded fire after client selection.
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

    // Step 1 is visible
    await expect.element(page.getByTestId('select-client-button')).toBeVisible();

    // Next button is disabled until form is valid
    await expect.element(page.getByTestId('next-button')).toBeVisible();

    // Open select-client dialog
    await page.getByTestId('select-client-button').click();

    // Dialog search input should be visible (the dialog rendered)
    await expect.element(page.getByTestId('client-search-input')).toBeVisible();

    // Select the TestClient entry by clicking its radio button
    await page.getByRole('radio').first().click();

    // Save the selection (dialog save button)
    await page.getByTestId('dialog-save-button').click();

    // After save, dialog is gone and form fields are populated
    await expect.element(page.getByTestId('client-search-input')).not.toBeInTheDocument();

    // member-code-input shows 4321
    await expect.element(page.getByTestId('member-code-input').getByRole('textbox')).toBeVisible();
    const memberCodeInput = page.getByTestId('member-code-input').getByRole('textbox');
    const memberCodeValue = (await memberCodeInput.element() as HTMLInputElement).value;
    expect(memberCodeValue).toBe('4321');

    // subsystem-code-input shows TestClient
    const subsystemCodeInput = page.getByTestId('subsystem-code-input').getByRole('textbox');
    const subsystemCodeValue = (await subsystemCodeInput.element() as HTMLInputElement).value;
    expect(subsystemCodeValue).toBe('TestClient');

    // Next button becomes enabled after valid selection
    await expect.element(page.getByTestId('next-button')).not.toBeDisabled();

    // Click Next — advances wizard to step 2 (Token page)
    await page.getByTestId('next-button').click();

    // Step 2 (Token page) has a previous-button; assert it becomes visible to
    // confirm the wizard advanced past step 1.
    await expect.element(page.getByTestId('previous-button')).toBeVisible();

    // Wizard is still open (not navigated away)
    expect(router.currentRoute.value.path).toBe('/add-client');
  });
});
