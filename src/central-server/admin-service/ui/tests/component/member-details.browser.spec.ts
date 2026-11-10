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
import { submitDialogForm } from '../setup/dialog-helpers';
import { refusalCase, registerDidCheckSpecs } from '../setup/did-check-specs';
import { Permissions } from '@/global';
import type { RequestHandler } from 'msw';
import type { Client, SecurityServer } from '@/openapi-types';

const MEMBER_ID = 'CS:E2E-TC1:e2e-tc1-member-subsystem';
const SERVER_ID = 'CS:E2E-TC1:e2e-tc1-member-subsystem:E2E-SS1';
const SERVER_CODE = 'E2E-SS1';

const memberFixture: Client = {
  client_id: {
    instance_id: 'CS',
    member_class: 'E2E-TC1',
    member_code: 'e2e-tc1-member-subsystem',
    type: 'MEMBER',
    encoded_id: MEMBER_ID,
  },
  member_name: 'E2E TC1 Member with Subsystems',
};

const serverFixture: SecurityServer = {
  server_id: {
    instance_id: 'CS',
    member_class: 'E2E-TC1',
    member_code: 'e2e-tc1-member-subsystem',
    server_code: SERVER_CODE,
    type: 'SERVER',
    encoded_id: SERVER_ID,
  },
  owner_name: 'E2E TC1 Member with Subsystems',
  server_address: 'security-server-address-E2E-SS1',
  created_at: '2024-01-15T10:00:00Z',
  in_maintenance_mode: false,
};

const navPermissions = [
  Permissions.VIEW_MEMBERS,
  Permissions.VIEW_MEMBER_DETAILS,
  Permissions.VIEW_SECURITY_SERVERS,
  Permissions.VIEW_SECURITY_SERVER_DETAILS,
];

const unregisterPermissions = [
  ...navPermissions,
  Permissions.UNREGISTER_MEMBER,
];

function memberDetailHandlers() {
  return [
    specHttp.get('/members/{member_id}', ({ response }) => response(200).json(memberFixture as never)),
    specHttp.get('/members/{member_id}/owned-servers', ({ response }) =>
      response(200).json([serverFixture] as never),
    ),
    specHttp.get('/members/{member_id}/used-servers', ({ response }) =>
      response(200).json([] as never),
    ),
    specHttp.get('/members/{member_id}/global-groups', ({ response }) =>
      response(200).json([] as never),
    ),
  ];
}

describe('1040 — CS Member Details — cross-page navigation member details → SS details → back (Browser Mode)', () => {
  it('clicking owned server navigates to SS details; router.back() returns to member details', async () => {
    const { router } = await renderRoute(`/members/${encodeURIComponent(MEMBER_ID)}/details`, {
      permissions: navPermissions,
      msw: [
        ...memberDetailHandlers(),
        specHttp.get('/security-servers/{server_id}', ({ response }) => response(200).json(serverFixture as never)),
      ],
    });

    await expect.element(page.getByTestId('member-details')).toBeVisible();
    await expect.element(page.getByTestId('owned-servers-table')).toBeVisible();

    await expect.element(page.getByTestId(`server-${SERVER_CODE}`)).toBeVisible();
    await page.getByTestId(`server-${SERVER_CODE}`).click();

    await expect.element(page.getByTestId('security-server-details-view')).toBeVisible();
    await expect.element(page.getByTestId('security-server-owner-name')).toMatchTextContent('E2E TC1 Member with Subsystems');
    await expect.element(page.getByTestId('security-server-owner-class')).toMatchTextContent('E2E-TC1');
    await expect.element(page.getByTestId('security-server-owner-code')).toMatchTextContent('e2e-tc1-member-subsystem');

    router.back();

    await expect.element(page.getByTestId('member-details')).toBeVisible();
    await expect.element(page.getByTestId('member-name')).toMatchTextContent('E2E TC1 Member with Subsystems');
  });
});

describe('1040 — CS Member Details — used servers list render and unregister confirm dialog (Browser Mode)', () => {
  it('used server appears in table; cancel keeps it; confirm removes it from table', async () => {
    const usedServerMemberFixture: Client = {
      client_id: {
        instance_id: 'CS',
        member_class: 'E2E-TC1',
        member_code: 'e2e-tc4-test-member',
        type: 'MEMBER',
        encoded_id: 'CS:E2E-TC1:e2e-tc4-test-member',
      },
      member_name: 'E2E TC4 Test Member',
    };

    let usedServersCallCount = 0;
    await renderRoute(`/members/${encodeURIComponent('CS:E2E-TC1:e2e-tc4-test-member')}/details`, {
      permissions: unregisterPermissions,
      msw: [
        specHttp.get('/members/{member_id}', ({ response }) => response(200).json(usedServerMemberFixture as never)),
        specHttp.get('/members/{member_id}/owned-servers', ({ response }) =>
          response(200).json([] as never),
        ),
        specHttp.get('/members/{member_id}/used-servers', ({ response }) => {
          usedServersCallCount += 1;
          return usedServersCallCount === 1
            ? response(200).json([serverFixture] as never)
            : response(200).json([] as never);
        }),
        specHttp.get('/members/{member_id}/global-groups', ({ response }) =>
          response(200).json([] as never),
        ),
        specHttp.delete('/members/{member_id}/servers/{server_id}', ({ response }) => response(204).empty()),
      ],
    });

    await expect.element(page.getByTestId('used-servers-table')).toBeVisible();
    await expect.element(page.getByTestId(`server-${SERVER_CODE}`)).toBeVisible();

    await page.getByTestId(`unregister-${SERVER_CODE}`).click();

    await expect.element(page.getByTestId('dialog-simple')).toBeVisible();

    await page.getByTestId('dialog-cancel-button').click();

    await expect.element(page.getByTestId('dialog-simple')).not.toBeInTheDocument();
    await expect.element(page.getByTestId(`server-${SERVER_CODE}`)).toBeVisible();

    await page.getByTestId(`unregister-${SERVER_CODE}`).click();

    await expect.element(page.getByTestId('dialog-simple')).toBeVisible();

    await page.getByTestId('dialog-save-button').click();

    await expect.element(page.getByTestId(`server-${SERVER_CODE}`)).not.toBeInTheDocument();
  });
});

describe('CS Member Details — member DID column on owned and used servers (Browser Mode)', () => {
  const CURRENT_DID = 'did:web:security-server-address-E2E-SS1%3A7183:v1:E2E-TC1:e2e-tc1-member-subsystem';
  const OLD_DID = 'did:web:old-address%3A7183:v1:E2E-TC1:e2e-tc1-member-subsystem';
  const serverWithout = (code: string): SecurityServer => ({
    ...serverFixture,
    server_id: { ...serverFixture.server_id, server_code: code, encoded_id: `${MEMBER_ID}:${code}` },
  });

  async function renderWith(owned: object[], used: object[]) {
    await renderRoute(`/members/${encodeURIComponent(MEMBER_ID)}/details`, {
      permissions: unregisterPermissions,
      msw: [
        specHttp.get('/members/{member_id}', ({ response }) => response(200).json(memberFixture as never)),
        specHttp.get('/members/{member_id}/owned-servers', ({ response }) => response(200).json(owned as never)),
        specHttp.get('/members/{member_id}/used-servers', ({ response }) => response(200).json(used as never)),
        specHttp.get('/members/{member_id}/global-groups', ({ response }) => response(200).json([] as never)),
      ],
    });
  }

  it('shows the member DID without a warning when it is not stale', async () => {
    await renderWith([{ ...serverFixture, member_did: CURRENT_DID, member_did_stale: false }], []);

    const owned = page.getByTestId('owned-servers-table');
    await expect.element(owned.getByTestId('member-did')).toMatchTextContent(CURRENT_DID);
    await expect.element(owned.getByTestId('member-did-stale-warning')).not.toBeInTheDocument();
  });

  it('shows the member DID with a warning when it is stale', async () => {
    await renderWith([{ ...serverFixture, member_did: OLD_DID, member_did_stale: true }], []);

    const owned = page.getByTestId('owned-servers-table');
    await expect.element(owned.getByTestId('member-did')).toMatchTextContent(OLD_DID);
    await expect.element(owned.getByTestId('member-did-stale-warning')).toBeVisible();
  });

  it('shows a dash for a server without a member DID', async () => {
    await renderWith([serverFixture], [serverWithout('E2E-SS2')]);

    await expect.element(page.getByTestId('owned-servers-table').getByTestId('member-did-missing')).toMatchTextContent('-');
    await expect.element(page.getByTestId('used-servers-table').getByTestId('member-did-missing')).toMatchTextContent('-');
    await expect.element(page.getByTestId('member-did-stale-warning')).not.toBeInTheDocument();
  });

  it('shows the member DID and warning in the used servers list next to the unregister action', async () => {
    await renderWith([], [{ ...serverFixture, member_did: OLD_DID, member_did_stale: true }]);

    const used = page.getByTestId('used-servers-table');
    await expect.element(used.getByTestId('member-did')).toMatchTextContent(OLD_DID);
    await expect.element(used.getByTestId('member-did-stale-warning')).toBeVisible();
    await expect.element(used.getByTestId(`server-${SERVER_CODE}`)).toBeVisible();
    await expect.element(used.getByTestId(`unregister-${SERVER_CODE}`)).toBeVisible();
  });
});

describe('CS Member Details — edit member DID (Browser Mode)', () => {
  const CURRENT_DID = 'did:web:security-server-address-E2E-SS1%3A7183:v1:E2E-TC1:e2e-tc1-member-subsystem';
  const NEW_DID = 'did:web:new-address%3A7183:v1:E2E-TC1:e2e-tc1-member-subsystem';
  const NOTIFY_TEXT =
    'Inform the Security Server operator about this change, for example by email. The Security Server keeps its own values until the operator re-identifies it.';
  const editPermissions = [...navPermissions, Permissions.EDIT_MEMBER_DID];
  const withDid = { ...serverFixture, member_did: CURRENT_DID, member_did_stale: false };
  const withoutDid = {
    ...serverFixture,
    server_id: { ...serverFixture.server_id, server_code: 'E2E-SS2', encoded_id: `${MEMBER_ID}:E2E-SS2` },
  };

  async function renderWith(owned: object[], used: object[], extra: RequestHandler[] = [], permissions = editPermissions) {
    await renderRoute(`/members/${encodeURIComponent(MEMBER_ID)}/details`, {
      permissions,
      msw: [
        ...extra,
        specHttp.get('/members/{member_id}', ({ response }) => response(200).json(memberFixture as never)),
        specHttp.get('/members/{member_id}/owned-servers', ({ response }) => response(200).json(owned as never)),
        specHttp.get('/members/{member_id}/used-servers', ({ response }) => response(200).json(used as never)),
        specHttp.get('/members/{member_id}/global-groups', ({ response }) => response(200).json([] as never)),
      ],
    });
  }

  async function openDialog(extra: RequestHandler[] = []) {
    await renderWith([withDid], [], extra);
    await page.getByTestId(`member-did-edit-${SERVER_CODE}`).click();
    const dialog = page.getByTestId('member-did-edit-dialog');
    await expect.element(dialog).toBeVisible();
    return dialog;
  }

  const didField = (dialog: ReturnType<typeof page.getByTestId>) => dialog.getByTestId('member-did-edit-field').getByRole('textbox');

  describe('edit button', () => {
    it('is shown with the permission in both lists, and disabled for a server without a member DID', async () => {
      await renderWith([withDid, withoutDid], [withDid, withoutDid]);

      for (const table of ['owned-servers-table', 'used-servers-table']) {
        const list = page.getByTestId(table);
        await expect.element(list.getByTestId(`member-did-edit-${SERVER_CODE}`)).toBeVisible();
        await expect.element(list.getByTestId(`member-did-edit-${SERVER_CODE}`)).not.toBeDisabled();
        await expect.element(list.getByTestId('member-did-edit-E2E-SS2')).toBeDisabled();
      }
    });

    it('is hidden without the permission', async () => {
      await renderWith([withDid], [withDid], [], navPermissions);

      await expect.element(page.getByTestId('owned-servers-table').getByTestId('member-did')).toBeVisible();
      await expect.element(page.getByTestId(`member-did-edit-${SERVER_CODE}`)).not.toBeInTheDocument();
    });

    it('keeps the server code link and the unregister action working', async () => {
      await renderWith([], [withDid], [], [...editPermissions, Permissions.UNREGISTER_MEMBER]);

      const used = page.getByTestId('used-servers-table');
      await expect.element(used.getByTestId(`server-${SERVER_CODE}`)).toBeVisible();
      await expect.element(used.getByTestId(`unregister-${SERVER_CODE}`)).toBeVisible();
      await expect.element(used.getByTestId(`member-did-edit-${SERVER_CODE}`)).toBeVisible();
    });
  });

  describe('dialog', () => {
    it('opens prefilled and cancel closes it without a request', async () => {
      const patchSpy = vi.fn();
      const dialog = await openDialog([
        specHttp.patch('/members/{member_id}/servers/{server_id}', ({ response }) => {
          patchSpy();
          return response(204).empty();
        }),
      ]);

      await expect.element(didField(dialog)).toHaveValue(CURRENT_DID);
      await page.getByTestId('dialog-cancel-button').click();

      await expect.element(page.getByTestId('member-did-edit-dialog')).not.toBeInTheDocument();
      expect(patchSpy).not.toHaveBeenCalled();
    });

    it('saves through the PATCH, closes, reloads both lists and shows the notify banner', async () => {
      const patchSpy = vi.fn();
      let ownedCalls = 0;
      let usedCalls = 0;
      await renderWith(
        [],
        [],
        [
          specHttp.get('/members/{member_id}/owned-servers', ({ response }) => {
            ownedCalls += 1;
            return response(200).json((ownedCalls === 1 ? [withDid] : [{ ...withDid, member_did: NEW_DID }]) as never);
          }),
          specHttp.get('/members/{member_id}/used-servers', ({ response }) => {
            usedCalls += 1;
            return response(200).json((usedCalls === 1 ? [withDid] : [{ ...withDid, member_did: NEW_DID }]) as never);
          }),
          specHttp.patch('/members/{member_id}/servers/{server_id}', async ({ request, response }) => {
            patchSpy(await request.json());
            return response(204).empty();
          }),
        ],
      );
      await page.getByTestId('owned-servers-table').getByTestId(`member-did-edit-${SERVER_CODE}`).click();
      const dialog = page.getByTestId('member-did-edit-dialog');

      await didField(dialog).fill(NEW_DID);
      submitDialogForm();

      await expect.element(page.getByTestId('member-did-edit-dialog')).not.toBeInTheDocument();
      expect(patchSpy).toHaveBeenCalledWith({ member_did: NEW_DID });
      await expect.element(page.getByTestId('owned-servers-table').getByTestId('member-did')).toMatchTextContent(NEW_DID);
      await expect.element(page.getByTestId('used-servers-table').getByTestId('member-did')).toMatchTextContent(NEW_DID);
      expect(ownedCalls).toBe(2);
      expect(usedCalls).toBe(2);
      await expect.element(page.getByTestId('success-snackbar')).toMatchTextContent('Member DID successfully updated');
      const banner = page.getByTestId('contextual-alert');
      await expect.element(banner).toMatchTextContent(NOTIFY_TEXT);
      expect(banner.element().classList).toContain('border-warning');
    });

    describe('save button', () => {
      const saveButton = () => page.getByTestId('dialog-save-button');

      it('is disabled when the dialog opens unchanged', async () => {
        await openDialog();

        await expect.element(saveButton()).toBeDisabled();
      });

      it('is enabled after a change and disabled again when the DID is emptied', async () => {
        const dialog = await openDialog();

        await didField(dialog).fill(NEW_DID);
        await expect.element(saveButton()).not.toBeDisabled();
        await didField(dialog).fill('');

        await expect.element(saveButton()).toBeDisabled();
      });
    });

    const refusalCases: [code: string, text: string][] = [
      ['did_host_differs', 'The host of the DID must match the security server address'],
      ['did_held_by_another_server', 'The DID is already used by another security server'],
      ['did_not_stored', 'The security server has no stored DID to replace'],
    ];
    it.each(refusalCases)('shows the refusal %s inside the dialog, keeps it open and shows no notify banner', async (code, text) => {
      let usedCalls = 0;
      const dialog = await openDialog([
        specHttp.get('/members/{member_id}/used-servers', ({ response }) => {
          usedCalls += 1;
          return response(200).json([] as never);
        }),
        specHttp.untyped.patch('/api/v1/members/:member_id/servers/:server_id', () =>
          HttpResponse.json({ status: 400, error: { code } }, { status: 400 }),
        ),
      ]);

      await didField(dialog).fill(NEW_DID);
      submitDialogForm();

      await expect.element(dialog.getByTestId('contextual-alert')).toMatchTextContent(text);
      await expect.element(dialog).toBeVisible();
      await expect.element(didField(dialog)).toHaveValue(NEW_DID);
      await expect.element(page.getByText(NOTIFY_TEXT)).not.toBeInTheDocument();
      await expect.element(page.getByTestId('success-snackbar')).not.toBeInTheDocument();
      expect(usedCalls).toBe(1);
    });
  });

  describe('check DID button', () => {
    registerDidCheckSpecs({
      subject: 'member DID',
      checkPath: '/api/v1/members/:member_id/servers/:server_id/member-did-check',
      buttonTestId: 'member-did-check-button',
      resultTestId: 'member-did-check-result',
      openDialog,
      fillForCheck: (dialog) => didField(dialog).fill(NEW_DID),
      expectedRequest: { member_did: NEW_DID },
      editWhilePending: (dialog) => didField(dialog).fill(NEW_DID),
      editBeforeNewCheck: (dialog) => didField(dialog).fill(NEW_DID),
      refusalCases: [
        refusalCase('UNDECODABLE_DID', 'The DID cannot be decoded'),
        refusalCase('DID_OF_OTHER_MEMBER', 'The DID belongs to another member'),
        refusalCase('MEMBER_NOT_SERVED', 'The security server does not serve this member'),
        refusalCase('NO_STORED_VALUE', 'The security server has no stored DID to replace'),
      ],
    });

    it('is disabled with an empty DID and clears the result when the DID changes', async () => {
      const dialog = await openDialog([
        specHttp.post('/members/{member_id}/servers/{server_id}/member-did-check', ({ response }) =>
          response(200).json({ status: 'RESOLVED' } as never),
        ),
      ]);

      await expect.element(dialog.getByTestId('member-did-check-button')).not.toBeDisabled();
      await dialog.getByTestId('member-did-check-button').click();
      await expect.element(dialog.getByTestId('member-did-check-result')).toBeVisible();

      await didField(dialog).fill(NEW_DID);
      await expect.element(dialog.getByTestId('member-did-check-result')).not.toBeInTheDocument();

      await didField(dialog).fill('');
      await expect.element(dialog.getByTestId('member-did-check-button')).toBeDisabled();
    });
  });
});
