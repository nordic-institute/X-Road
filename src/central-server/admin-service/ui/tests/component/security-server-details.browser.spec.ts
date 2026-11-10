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
import type { RequestHandler } from 'msw';
import { renderRoute } from '../setup/render-route';
import { specHttp } from '../setup/spec-http';
import { submitDialogForm } from '../setup/dialog-helpers';
import { refusalCase, registerDidCheckSpecs } from '../setup/did-check-specs';
import { Permissions } from '@/global';
import type { SecurityServer } from '@/openapi-types';

const SERVER_ID = 'CS:E2E-TC1:e2e-tc1-member-subsystem:E2E-SS1';
const SERVER_CODE = 'E2E-SS1';
const OWNER_NAME = 'E2E TC1 Member with Subsystems';
const OWNER_CLASS = 'E2E-TC1';
const OWNER_CODE = 'e2e-tc1-member-subsystem';
const ADDRESS = 'security-server-address-E2E-SS1';

const serverFixture: SecurityServer = {
  server_id: {
    instance_id: 'CS',
    member_class: OWNER_CLASS,
    member_code: OWNER_CODE,
    server_code: SERVER_CODE,
    type: 'SERVER',
    encoded_id: SERVER_ID,
  },
  owner_name: OWNER_NAME,
  server_address: ADDRESS,
  created_at: '2024-01-15T10:00:00Z',
  in_maintenance_mode: false,
};

const detailsPermissions = [
  Permissions.VIEW_SECURITY_SERVERS,
  Permissions.VIEW_SECURITY_SERVER_DETAILS,
];

const editPermissions = [
  ...detailsPermissions,
  Permissions.EDIT_SECURITY_SERVER_ADDRESS,
];

const deletePermissions = [
  ...detailsPermissions,
  Permissions.DELETE_SECURITY_SERVER,
];

function baseHandlers(fixture: SecurityServer = serverFixture): RequestHandler[] {
  return [
    specHttp.get('/security-servers/{server_id}', ({ response }) => response(200).json(fixture as never)),
  ];
}

describe('1000 — CS Security Server Details — detail fields render (Browser Mode)', () => {
  it('renders owner name, class, code, server code, and address from API response', async () => {
    await renderRoute(`/security-servers/${encodeURIComponent(SERVER_ID)}/details`, {
      permissions: detailsPermissions,
      msw: baseHandlers(),
    });

    await expect.element(page.getByTestId('security-server-details-view')).toBeVisible();
    await expect.element(page.getByTestId('security-server-owner-name')).toMatchTextContent(OWNER_NAME);
    await expect.element(page.getByTestId('security-server-owner-class')).toMatchTextContent(OWNER_CLASS);
    await expect.element(page.getByTestId('security-server-owner-code')).toMatchTextContent(OWNER_CODE);
    await expect.element(page.getByTestId('security-server-server-code')).toMatchTextContent(SERVER_CODE);
    await expect.element(page.getByTestId('security-server-address')).toMatchTextContent(ADDRESS);
    await expect.element(page.getByTestId('security-server-registered')).toBeVisible();
  });
});

describe('1000 — CS Security Server Details — SYSTEM pair and stale warning (Browser Mode)', () => {
  const SYSTEM_DID = 'did:web:security-server-address-e2e-ss1%3A7183:v1:system';
  const DSP_BASE_URL = 'https://security-server-address-e2e-ss1:8183/api/dsp';

  async function renderDetails(fixture: SecurityServer) {
    await renderRoute(`/security-servers/${encodeURIComponent(SERVER_ID)}/details`, {
      permissions: detailsPermissions,
      msw: baseHandlers(fixture),
    });
    await expect.element(page.getByTestId('security-server-details-view')).toBeVisible();
    await expect.element(page.getByTestId('security-server-address')).toMatchTextContent(fixture.server_address);
  }

  it('shows both values and no warning when the hosts match the address', async () => {
    await renderDetails({
      ...serverFixture,
      system_did: SYSTEM_DID,
      dsp_base_url: DSP_BASE_URL,
      system_pair_stale: false,
    } as SecurityServer);

    await expect.element(page.getByTestId('security-server-system-did')).toMatchTextContent(SYSTEM_DID);
    await expect.element(page.getByTestId('security-server-dsp-base-url')).toMatchTextContent(DSP_BASE_URL);
    await expect.element(page.getByTestId('security-server-stale-values-warning')).not.toBeInTheDocument();
  });

  it('shows both values and the warning when the server flags the pair as stale', async () => {
    await renderDetails({
      ...serverFixture,
      server_address: 'new-address.example.org',
      system_did: SYSTEM_DID,
      dsp_base_url: DSP_BASE_URL,
      system_pair_stale: true,
    } as SecurityServer);

    await expect.element(page.getByTestId('security-server-system-did')).toMatchTextContent(SYSTEM_DID);
    await expect.element(page.getByTestId('security-server-dsp-base-url')).toMatchTextContent(DSP_BASE_URL);
    await expect.element(page.getByTestId('security-server-stale-values-warning')).toBeVisible();
  });

  it('shows neither values nor warning for a server without a pair', async () => {
    await renderDetails(serverFixture);

    await expect.element(page.getByTestId('security-server-system-did')).not.toBeInTheDocument();
    await expect.element(page.getByTestId('security-server-dsp-base-url')).not.toBeInTheDocument();
    await expect.element(page.getByTestId('security-server-stale-values-warning')).not.toBeInTheDocument();
  });
});

describe('1000 — CS Security Server Details — edit address dialog cancel and save (Browser Mode)', () => {
  it('cancel keeps the old address; save updates the rendered address', async () => {
    const updatedAddress = 'security-server-address-E2E-SS1-updated';

    let serverCallCount = 0;
    await renderRoute(`/security-servers/${encodeURIComponent(SERVER_ID)}/details`, {
      permissions: editPermissions,
      msw: [
        specHttp.get('/security-servers/{server_id}', ({ response }) => {
          serverCallCount += 1;
          return serverCallCount === 1
            ? response(200).json(serverFixture as never)
            : response(200).json({ ...serverFixture, server_address: updatedAddress } as never);
        }),
        specHttp.patch('/security-servers/{server_id}', ({ response }) =>
          response(200).json({ ...serverFixture, server_address: updatedAddress } as never),
        ),
      ],
    });

    await expect.element(page.getByTestId('security-server-address')).toMatchTextContent(ADDRESS);

    await page.getByTestId('security-server-address').getByRole('button').click();

    const editDialog = page.getByTestId('security-server-address-edit-dialog');
    await expect.element(editDialog).toBeVisible();

    const addressField = editDialog.getByTestId('security-server-address-edit-field').getByRole('textbox');
    await addressField.fill(updatedAddress);

    await page.getByTestId('dialog-cancel-button').click();

    await expect.element(page.getByTestId('security-server-address-edit-dialog')).not.toBeInTheDocument();
    await expect.element(page.getByTestId('security-server-address')).toMatchTextContent(ADDRESS);

    await page.getByTestId('security-server-address').getByRole('button').click();

    const editDialog2 = page.getByTestId('security-server-address-edit-dialog');
    await expect.element(editDialog2).toBeVisible();

    const addressField2 = editDialog2.getByTestId('security-server-address-edit-field').getByRole('textbox');
    await addressField2.fill(updatedAddress);

    await expect.element(page.getByTestId('dialog-save-button')).not.toBeDisabled();
    submitDialogForm();

    await expect.element(page.getByTestId('security-server-address-edit-dialog')).not.toBeInTheDocument();
    await expect.element(page.getByTestId('security-server-address')).toMatchTextContent(updatedAddress);
  });
});

describe('1000 — CS Security Server Details — edit dialog with SYSTEM pair (Browser Mode)', () => {
  const SYSTEM_DID = 'did:web:security-server-address-e2e-ss1%3A7183:v1:system';
  const DSP_BASE_URL = 'https://security-server-address-e2e-ss1:8183/api/dsp';
  const NEW_DID = 'did:web:new-address.example.org%3A7183:v1:system';
  const NEW_URL = 'https://new-address.example.org:8183/api/dsp';
  const NEW_ADDRESS = 'new-address.example.org';
  const STALE_TEXT =
    'The SYSTEM DID or DSP base URL does not match the Security Server address. Other servers use the old values to reach this server. Edit the values, or ask the Security Server operator to re-identify the server.';
  const OLD_HOST_TEXT =
    'The SYSTEM DID and DSP base URL still use the old host. Change them too, or leave them for the Security Server operator to re-identify the server.';
  const NOTIFY_TEXT =
    'Inform the Security Server operator about this change, for example by email. The Security Server keeps its own values until the operator re-identifies it.';

  const pairFixture = {
    ...serverFixture,
    system_did: SYSTEM_DID,
    dsp_base_url: DSP_BASE_URL,
    system_pair_stale: false,
  } as SecurityServer;

  async function openDialog(fixture: SecurityServer, extra: RequestHandler[] = []) {
    await renderRoute(`/security-servers/${encodeURIComponent(SERVER_ID)}/details`, {
      permissions: editPermissions,
      msw: [...baseHandlers(fixture), ...extra],
    });
    await expect.element(page.getByTestId('security-server-address')).toMatchTextContent(fixture.server_address);
    await page.getByTestId('security-server-address').getByRole('button').click();
    const dialog = page.getByTestId('security-server-address-edit-dialog');
    await expect.element(dialog).toBeVisible();
    return dialog;
  }

  const field = (dialog: ReturnType<typeof page.getByTestId>, testId: string) => dialog.getByTestId(testId).getByRole('textbox');

  it('prefills the address and the pair', async () => {
    const dialog = await openDialog(pairFixture);

    await expect.element(field(dialog, 'security-server-address-edit-field')).toHaveValue(ADDRESS);
    await expect.element(field(dialog, 'security-server-system-did-edit-field')).toHaveValue(SYSTEM_DID);
    await expect.element(field(dialog, 'security-server-dsp-base-url-edit-field')).toHaveValue(DSP_BASE_URL);
  });

  it('shows only the address field for a server without a pair', async () => {
    const dialog = await openDialog(serverFixture);

    await expect.element(field(dialog, 'security-server-address-edit-field')).toHaveValue(ADDRESS);
    await expect.element(dialog.getByTestId('security-server-system-did-edit-field')).not.toBeInTheDocument();
    await expect.element(dialog.getByTestId('security-server-dsp-base-url-edit-field')).not.toBeInTheDocument();
    await expect.element(dialog.getByTestId('security-server-system-did-check-button')).not.toBeInTheDocument();
    await expect.element(dialog.getByTestId('security-server-edit-warning')).not.toBeInTheDocument();
  });

  it('warns from the stored stale flag, then about the old host after an address edit, then not after a pair edit', async () => {
    const dialog = await openDialog({ ...pairFixture, system_pair_stale: true } as SecurityServer);
    const warning = dialog.getByTestId('security-server-edit-warning');

    await expect.element(warning).toMatchTextContent(STALE_TEXT);

    await field(dialog, 'security-server-address-edit-field').fill(NEW_ADDRESS);
    await expect.element(warning).toMatchTextContent(OLD_HOST_TEXT);

    await field(dialog, 'security-server-system-did-edit-field').fill(NEW_DID);
    await expect.element(warning).not.toBeInTheDocument();

    await field(dialog, 'security-server-system-did-edit-field').fill(SYSTEM_DID);
    await expect.element(warning).toMatchTextContent(OLD_HOST_TEXT);

    await field(dialog, 'security-server-address-edit-field').fill(ADDRESS);
    await expect.element(warning).toMatchTextContent(STALE_TEXT);
  });

  it('treats an address that differs only in letter case as unchanged', async () => {
    const dialog = await openDialog(pairFixture);
    const warning = dialog.getByTestId('security-server-edit-warning');

    await field(dialog, 'security-server-address-edit-field').fill(ADDRESS.toLowerCase());
    await expect.element(warning).not.toBeInTheDocument();

    await field(dialog, 'security-server-address-edit-field').fill(NEW_ADDRESS);
    await expect.element(warning).toMatchTextContent(OLD_HOST_TEXT);
  });

  it('shows no notify banner after saving an address that differs only in letter case', async () => {
    const dialog = await openDialog(pairFixture, [
      specHttp.patch('/security-servers/{server_id}', ({ response }) =>
        response(200).json({ ...pairFixture, server_address: ADDRESS.toLowerCase() } as never),
      ),
    ]);

    await field(dialog, 'security-server-address-edit-field').fill(ADDRESS.toLowerCase());
    submitDialogForm();

    await expect.element(page.getByTestId('security-server-address-edit-dialog')).not.toBeInTheDocument();
    await expect.element(page.getByTestId('success-snackbar')).toBeVisible();
    await expect.element(page.getByTestId('contextual-alert')).not.toBeInTheDocument();
  });

  it('shows no warning for an unchanged, non-stale pair', async () => {
    const dialog = await openDialog(pairFixture);

    await expect.element(dialog.getByTestId('security-server-edit-warning')).not.toBeInTheDocument();
  });

  describe('check DID button', () => {
    registerDidCheckSpecs({
      subject: 'SYSTEM DID',
      checkPath: '/api/v1/security-servers/:server_id/system-did-check',
      buttonTestId: 'security-server-system-did-check-button',
      resultTestId: 'security-server-system-did-check-result',
      openDialog: (extraHandlers) => openDialog(pairFixture, extraHandlers),
      fillForCheck: async (dialog) => {
        await field(dialog, 'security-server-address-edit-field').fill(NEW_ADDRESS);
        await field(dialog, 'security-server-system-did-edit-field').fill(NEW_DID);
      },
      expectedRequest: { server_address: NEW_ADDRESS, system_did: NEW_DID },
      editWhilePending: (dialog) => field(dialog, 'security-server-dsp-base-url-edit-field').fill(NEW_URL),
      editBeforeNewCheck: (dialog) => field(dialog, 'security-server-system-did-edit-field').fill(NEW_DID),
      refusalCases: [refusalCase('FOREIGN_SYSTEM_DID', "The DID is not this security server's SYSTEM DID")],
    });

    it('is disabled with an empty DID', async () => {
      const dialog = await openDialog(pairFixture);

      await expect.element(dialog.getByTestId('security-server-system-did-check-button')).not.toBeDisabled();
      await field(dialog, 'security-server-system-did-edit-field').fill('');
      await expect.element(dialog.getByTestId('security-server-system-did-check-button')).toBeDisabled();
    });

    it('is disabled while the address is invalid', async () => {
      const dialog = await openDialog(pairFixture);
      const button = dialog.getByTestId('security-server-system-did-check-button');

      await field(dialog, 'security-server-address-edit-field').fill('bad address!');
      await expect.element(button).toBeDisabled();

      await field(dialog, 'security-server-address-edit-field').fill(NEW_ADDRESS);
      await expect.element(button).not.toBeDisabled();
    });

    it('clears the result when the address, the DID or the DSP base URL changes', async () => {
      const dialog = await openDialog(pairFixture, [
        specHttp.post('/security-servers/{server_id}/system-did-check', ({ response }) =>
          response(200).json({ status: 'RESOLVED' } as never),
        ),
      ]);
      const result = dialog.getByTestId('security-server-system-did-check-result');
      const edits = [
        ['security-server-address-edit-field', NEW_ADDRESS],
        ['security-server-system-did-edit-field', NEW_DID],
        ['security-server-dsp-base-url-edit-field', NEW_URL],
      ];

      for (const [testId, value] of edits) {
        await dialog.getByTestId('security-server-system-did-check-button').click();
        await expect.element(result).toBeVisible();
        await field(dialog, testId).fill(value);
        await expect.element(result).not.toBeInTheDocument();
      }
    });

    it('removes the earlier answer when a re-check fails', async () => {
      let failing = false;
      const handler = specHttp.untyped.post('/api/v1/security-servers/:server_id/system-did-check', () =>
        failing
          ? HttpResponse.json({ status: 500, error: { code: 'internal_error' } }, { status: 500 })
          : HttpResponse.json({ status: 'RESOLVED' }),
      );
      const dialog = await openDialog(pairFixture, [handler]);
      const result = dialog.getByTestId('security-server-system-did-check-result');
      const checkButton = dialog.getByTestId('security-server-system-did-check-button');

      await checkButton.click();
      await expect.element(result).toBeVisible();
      failing = true;
      await checkButton.click();

      await expect.element(page.getByTestId('contextual-alert')).toBeVisible();
      await expect.element(result).not.toBeInTheDocument();
    });

    it('never blocks saving', async () => {
      const dialog = await openDialog(pairFixture, [
        specHttp.post('/security-servers/{server_id}/system-did-check', ({ response }) =>
          response(200).json({ status: 'NOT_RESOLVED', resolution_reason: 'NO_DOCUMENT' } as never),
        ),
      ]);

      await field(dialog, 'security-server-address-edit-field').fill(NEW_ADDRESS);
      await dialog.getByTestId('security-server-system-did-check-button').click();
      await expect.element(dialog.getByTestId('security-server-system-did-check-result')).toBeVisible();

      await expect.element(page.getByTestId('dialog-save-button')).not.toBeDisabled();
    });
  });

  describe('save button', () => {
    const saveButton = () => page.getByTestId('dialog-save-button');

    it('is disabled when the dialog opens unchanged', async () => {
      await openDialog(pairFixture);

      await expect.element(saveButton()).toBeDisabled();
    });

    it('is enabled after a change', async () => {
      const dialog = await openDialog(pairFixture);

      await field(dialog, 'security-server-address-edit-field').fill(NEW_ADDRESS);

      await expect.element(saveButton()).not.toBeDisabled();
    });

    it.each([
      ['address', 'security-server-address-edit-field'],
      ['SYSTEM DID', 'security-server-system-did-edit-field'],
      ['DSP base URL', 'security-server-dsp-base-url-edit-field'],
    ])('is disabled when the %s is emptied', async (_name, testId) => {
      const dialog = await openDialog(pairFixture);

      await field(dialog, 'security-server-address-edit-field').fill(NEW_ADDRESS);
      await expect.element(saveButton()).not.toBeDisabled();
      await field(dialog, testId).fill('');

      await expect.element(saveButton()).toBeDisabled();
    });
  });

  it('sends only the address for an address-only edit', async () => {
    const patchSpy = vi.fn();
    const dialog = await openDialog(pairFixture, [
      specHttp.patch('/security-servers/{server_id}', async ({ request, response }) => {
        patchSpy(await request.json());
        return response(200).json({ ...pairFixture, server_address: NEW_ADDRESS } as never);
      }),
    ]);

    await field(dialog, 'security-server-address-edit-field').fill(NEW_ADDRESS);
    submitDialogForm();

    await expect.element(page.getByTestId('security-server-address-edit-dialog')).not.toBeInTheDocument();
    expect(patchSpy).toHaveBeenCalledTimes(1);
    expect(patchSpy).toHaveBeenCalledWith({ server_address: NEW_ADDRESS });
  });

  it('sends both pair values when one pair field changed', async () => {
    const patchSpy = vi.fn();
    const dialog = await openDialog(pairFixture, [
      specHttp.patch('/security-servers/{server_id}', async ({ request, response }) => {
        patchSpy(await request.json());
        return response(200).json({ ...pairFixture, system_did: NEW_DID } as never);
      }),
    ]);

    await field(dialog, 'security-server-system-did-edit-field').fill(NEW_DID);
    submitDialogForm();

    await expect.element(page.getByTestId('security-server-address-edit-dialog')).not.toBeInTheDocument();
    expect(patchSpy).toHaveBeenCalledWith({ server_address: ADDRESS, system_did: NEW_DID, dsp_base_url: DSP_BASE_URL });
  });

  it('shows a refusal inside the dialog and keeps the dialog open with the entered values', async () => {
    const dialog = await openDialog(pairFixture, [
      specHttp.untyped.patch('/api/v1/security-servers/:server_id', () =>
        HttpResponse.json({ status: 400, error: { code: 'did_host_differs' } }, { status: 400 }),
      ),
    ]);

    await field(dialog, 'security-server-address-edit-field').fill(NEW_ADDRESS);
    await field(dialog, 'security-server-system-did-edit-field').fill(NEW_DID);
    submitDialogForm();

    await expect
      .element(dialog.getByTestId('contextual-alert'))
      .toMatchTextContent('The host of the DID must match the security server address');
    await expect.element(dialog).toBeVisible();
    await expect.element(field(dialog, 'security-server-address-edit-field')).toHaveValue(NEW_ADDRESS);
    await expect.element(field(dialog, 'security-server-system-did-edit-field')).toHaveValue(NEW_DID);
    await expect.element(page.getByTestId('security-server-address')).toMatchTextContent(ADDRESS);
  });

  it('puts a validation failure on the address field', async () => {
    const dialog = await openDialog(pairFixture, [
      specHttp.untyped.patch('/api/v1/security-servers/:server_id', () =>
        HttpResponse.json(
          {
            status: 400,
            error: { code: 'validation_failure', validation_errors: { 'securityServerEditDto.serverAddress': ['IdentifierChars'] } },
          },
          { status: 400 },
        ),
      ),
    ]);

    await field(dialog, 'security-server-address-edit-field').fill(NEW_ADDRESS);
    submitDialogForm();

    await expect.element(dialog.getByText('Use valid identifier characters only')).toBeVisible();
    await expect.element(dialog.getByTestId('contextual-alert')).not.toBeInTheDocument();
  });

  it('shows the response values, the success snackbar and the notify banner after a save', async () => {
    const dialog = await openDialog(pairFixture, [
      specHttp.patch('/security-servers/{server_id}', ({ response }) =>
        response(200).json({
          ...pairFixture,
          server_address: NEW_ADDRESS,
          system_did: NEW_DID,
          dsp_base_url: NEW_URL,
        } as never),
      ),
    ]);

    await field(dialog, 'security-server-address-edit-field').fill(NEW_ADDRESS);
    await field(dialog, 'security-server-system-did-edit-field').fill(NEW_DID);
    await field(dialog, 'security-server-dsp-base-url-edit-field').fill(NEW_URL);
    submitDialogForm();

    await expect.element(page.getByTestId('security-server-address-edit-dialog')).not.toBeInTheDocument();
    await expect.element(page.getByTestId('security-server-address')).toMatchTextContent(NEW_ADDRESS);
    await expect.element(page.getByTestId('security-server-system-did')).toMatchTextContent(NEW_DID);
    await expect.element(page.getByTestId('security-server-dsp-base-url')).toMatchTextContent(NEW_URL);
    await expect.element(page.getByTestId('success-snackbar')).toMatchTextContent('Security server address successfully updated');
    const banner = page.getByTestId('contextual-alert');
    await expect.element(banner).toMatchTextContent(NOTIFY_TEXT);
    expect(banner.element().classList).toContain('border-warning');
    expect(banner.element().classList).not.toContain('border-error');
  });

  it('shows the success snackbar and no notify banner after a save for a server without a pair', async () => {
    const dialog = await openDialog(serverFixture, [
      specHttp.patch('/security-servers/{server_id}', ({ response }) =>
        response(200).json({ ...serverFixture, server_address: NEW_ADDRESS } as never),
      ),
    ]);

    await field(dialog, 'security-server-address-edit-field').fill(NEW_ADDRESS);
    submitDialogForm();

    await expect.element(page.getByTestId('security-server-address-edit-dialog')).not.toBeInTheDocument();
    await expect.element(page.getByTestId('security-server-address')).toMatchTextContent(NEW_ADDRESS);
    await expect.element(page.getByTestId('success-snackbar')).toMatchTextContent('Security server address successfully updated');
    await expect.element(page.getByTestId('contextual-alert')).not.toBeInTheDocument();
  });
});

describe('1000 — CS Security Server Details — delete dialog confirm-code gating (Browser Mode)', () => {
  it('save disabled with no/wrong code, enabled with exact server code, server removed after submit', async () => {
    await renderRoute(`/security-servers/${encodeURIComponent(SERVER_ID)}/details`, {
      permissions: deletePermissions,
      msw: [
        ...baseHandlers(),
        specHttp.delete('/security-servers/{server_id}', ({ response }) => response(204).empty()),
        specHttp.get('/security-servers', ({ response }) =>
          response(200).json({ security_servers: [], paging_metadata: { total_items: 0, items: 0, limit: 25, offset: 0 } } as never),
        ),
      ],
    });

    await expect.element(page.getByTestId('security-server-view')).toBeVisible();
    await expect.element(page.getByTestId('btn-delete-security-server')).toBeVisible();

    await page.getByTestId('btn-delete-security-server').click();

    const deleteDialog = page.getByTestId('security-server-delete-dialog');
    await expect.element(deleteDialog).toBeVisible();

    const codeInput = deleteDialog.getByTestId('verify-server-code').getByRole('textbox');
    await expect.element(page.getByTestId('dialog-save-button')).toBeDisabled();

    await codeInput.fill('invalid-code');
    await expect.element(page.getByTestId('dialog-save-button')).toBeDisabled();

    await codeInput.fill(SERVER_CODE);
    await expect.element(page.getByTestId('dialog-save-button')).not.toBeDisabled();

    submitDialogForm();

    await expect.element(page.getByTestId('security-server-view')).not.toBeInTheDocument();
  });
});
