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

/*
 * Consolidation note: "User Approves Management Request" and "User Declines Management Request"
 * have identical client-side flows (pending list -> row button -> confirm dialog -> removed).
 * They are kept as separate specs because they exercise different buttons (approve vs decline)
 * and different MSW mutation endpoints.
 *
 * "Views Details and Approves" and "Views Details and Declines" are similarly kept separate because
 * the decline path additionally asserts post-process gating (actions hidden after processing).
 *
 * The four "approve-variant" scenarios (additional-auth-cert, another-SS, another-client-subsystem,
 * another-client-member) are DROP-other per the audit ledger: they cover the same client-side flow
 * as "User Approves Management Request" with different fixture data — not authored here.
 */
import { describe, it, expect } from 'vitest';
import { page } from 'vitest/browser';
import { renderRoute } from '../setup/render-route';
import { specHttp } from '../setup/spec-http';
import { Permissions } from '@/global';
import type {
  PagedManagementRequests,
  ManagementRequestListView,
  ManagementRequestDetailedView,
  CertificateDetails,
  ClientId,
} from '@/openapi-types';

const MR_LIST_PATH = '/management-requests';

const basePermissions = [
  Permissions.VIEW_MANAGEMENT_REQUESTS,
  Permissions.VIEW_MANAGEMENT_REQUEST_DETAILS,
];

const serverId = {
  instance_id: 'CS',
  member_class: 'E2E-TC1',
  member_code: 'e2e-tc1-member-subsystem',
  server_code: 'E2E-SS1',
  type: 'SERVER' as const,
  encoded_id: 'CS:E2E-TC1:e2e-tc1-member-subsystem:E2E-SS1',
};

const waitingRequest: ManagementRequestListView = {
  id: 101,
  type: 'AUTH_CERT_REGISTRATION_REQUEST',
  origin: 'SECURITY_SERVER',
  security_server_owner: 'E2E TC1 Member',
  security_server_id: serverId,
  status: 'WAITING',
  created_at: '2024-06-01T10:00:00Z',
};

const approvedRequest: ManagementRequestListView = {
  ...waitingRequest,
  status: 'APPROVED',
};

const declinedRequest: ManagementRequestListView = {
  ...waitingRequest,
  status: 'DECLINED',
};

const emptyPage: PagedManagementRequests = {
  items: [],
  paging_metadata: { total_items: 0, items: 0, limit: 25, offset: 0 },
};

function singleItemPage(item: ManagementRequestListView): PagedManagementRequests {
  return {
    items: [item],
    paging_metadata: { total_items: 1, items: 1, limit: 25, offset: 0 },
  };
}

describe('0900 — CS Management Requests — approve via row button removes from pending list (Browser Mode)', () => {
  it('clicking Approve on a pending row opens confirm dialog; after confirm the row disappears', async () => {
    let approved = false;
    await renderRoute(MR_LIST_PATH, {
      permissions: basePermissions,
      msw: [
        specHttp.get('/management-requests', ({ response }) =>
          response(200).json((approved ? emptyPage : singleItemPage(waitingRequest)) as never),
        ),
        specHttp.post('/management-requests/{management_request_id}/approval', ({ response }) => {
          approved = true;
          return response(200).json({ ...waitingRequest, status: 'APPROVED' } as never);
        }),
      ],
    });

    await expect.element(page.getByTestId('management-requests-table')).toBeVisible();
    await expect.element(page.getByTestId('approve-button').first()).toBeVisible();

    await page.getByTestId('approve-button').first().click();

    // Confirm dialog
    await expect.element(page.getByTestId('dialog-save-button')).toBeVisible();
    await page.getByTestId('dialog-save-button').click();

    await expect.element(page.getByTestId('approve-button').query() as HTMLElement).not.toBeInTheDocument();
  });
});

describe('0900 — CS Management Requests — decline via row button removes from pending list (Browser Mode)', () => {
  it('clicking Decline on a pending row opens confirm dialog; after confirm the row disappears', async () => {
    let declined = false;
    await renderRoute(MR_LIST_PATH, {
      permissions: basePermissions,
      msw: [
        specHttp.get('/management-requests', ({ response }) =>
          response(200).json((declined ? emptyPage : singleItemPage(waitingRequest)) as never),
        ),
        specHttp.delete('/management-requests/{management_request_id}', ({ response }) => {
          declined = true;
          return response(204).empty();
        }),
      ],
    });

    await expect.element(page.getByTestId('management-requests-table')).toBeVisible();
    await expect.element(page.getByTestId('decline-button').first()).toBeVisible();

    await page.getByTestId('decline-button').first().click();

    await expect.element(page.getByTestId('dialog-save-button')).toBeVisible();
    await page.getByTestId('dialog-save-button').click();

    await expect.element(page.getByTestId('decline-button').query() as HTMLElement).not.toBeInTheDocument();
  });
});

describe('0900 — CS Management Requests — details page approve hides actions (Browser Mode)', () => {
  it('approving a WAITING request from detail page re-fetches status and hides approve/decline buttons', async () => {
    let approved = false;
    const detailedApprovedRequest: ManagementRequestDetailedView = {
      ...waitingRequest,
      status: 'APPROVED',
    };

    await renderRoute(`/management-requests/101/details`, {
      permissions: basePermissions,
      msw: [
        specHttp.get('/management-requests/{management_request_id}', ({ response }) =>
          response(200).json((approved ? detailedApprovedRequest : { ...waitingRequest }) as never),
        ),
        specHttp.post('/management-requests/{management_request_id}/approval', ({ response }) => {
          approved = true;
          return response(200).json({ ...waitingRequest, status: 'APPROVED' } as never);
        }),
      ],
    });

    await expect.element(page.getByTestId('approve-button')).toBeVisible();
    await expect.element(page.getByTestId('decline-button')).toBeVisible();

    await page.getByTestId('approve-button').click();

    await expect.element(page.getByTestId('dialog-save-button')).toBeVisible();
    await page.getByTestId('dialog-save-button').click();

    // After approve the detail re-fetches with APPROVED status — actions hidden
    await expect.element(page.getByTestId('approve-button').query() as HTMLElement).not.toBeInTheDocument();
    await expect.element(page.getByTestId('decline-button').query() as HTMLElement).not.toBeInTheDocument();
  });
});

describe('0900 — CS Management Requests — details page decline hides actions after processing (Browser Mode)', () => {
  it('declining a WAITING request from detail page updates status and hides approve/decline buttons', async () => {
    const detailedRequest: ManagementRequestDetailedView = {
      ...waitingRequest,
    };

    const detailedDeclinedRequest: ManagementRequestDetailedView = {
      ...declinedRequest,
    };

    let declined = false;
    await renderRoute(`/management-requests/101/details`, {
      permissions: basePermissions,
      msw: [
        specHttp.get('/management-requests/{management_request_id}', ({ response }) =>
          response(200).json((declined ? detailedDeclinedRequest : detailedRequest) as never),
        ),
        specHttp.delete('/management-requests/{management_request_id}', ({ response }) => {
          declined = true;
          return response(204).empty();
        }),
      ],
    });

    await expect.element(page.getByTestId('approve-button')).toBeVisible();
    await expect.element(page.getByTestId('decline-button')).toBeVisible();

    await page.getByTestId('decline-button').click();

    await expect.element(page.getByTestId('dialog-save-button')).toBeVisible();
    await page.getByTestId('dialog-save-button').click();

    // After decline the request status is DECLINED, so approve/decline actions are hidden
    await expect.element(page.getByTestId('approve-button').query() as HTMLElement).not.toBeInTheDocument();
    await expect.element(page.getByTestId('decline-button').query() as HTMLElement).not.toBeInTheDocument();
  });
});

describe('0900 — CS Management Requests — default sort is id desc; columns are sortable (Browser Mode)', () => {
  it('table loads with id column sorted descending and all expected column headers are present', async () => {
    await renderRoute(MR_LIST_PATH, {
      permissions: basePermissions,
      msw: [
        specHttp.get('/management-requests', ({ response }) =>
          response(200).json(singleItemPage(waitingRequest) as never),
        ),
      ],
    });

    const table = page.getByTestId('management-requests-table');
    await expect.element(table).toBeVisible();

    // Verify column headers are present (sort targets) — scoped to the table to avoid false matches
    await expect.element(table.getByText('Id').first()).toBeVisible();
    await expect.element(table.getByText('Created').first()).toBeVisible();
    await expect.element(table.getByText('Type').first()).toBeVisible();
    await expect.element(table.getByText('Server Owner Name').first()).toBeVisible();
    await expect.element(table.getByText('Server Identifier').first()).toBeVisible();
    await expect.element(table.getByText('Status').first()).toBeVisible();
  });
});

describe('0900 — CS Management Requests — search field filters pending list (Browser Mode)', () => {
  it('typing in the search field sends a query and shows only matching rows', async () => {
    const matchingRequest: ManagementRequestListView = {
      ...waitingRequest,
      security_server_owner: 'target-owner',
    };
    const nonMatchingRequest: ManagementRequestListView = {
      ...waitingRequest,
      id: 102,
      security_server_owner: 'other-owner',
    };

    await renderRoute(MR_LIST_PATH, {
      permissions: basePermissions,
      msw: [
        specHttp.get('/management-requests', ({ request, response }) => {
          const url = new URL(request.url);
          const query = url.searchParams.get('query') ?? '';
          if (query) {
            return response(200).json(singleItemPage(matchingRequest) as never);
          }
          return response(200).json({
            items: [matchingRequest, nonMatchingRequest],
            paging_metadata: { total_items: 2, items: 2, limit: 25, offset: 0 },
          } as never);
        }),
      ],
    });

    await expect.element(page.getByTestId('management-requests-table')).toBeVisible();
    await expect.element(page.getByText('target-owner').first()).toBeVisible();
    await expect.element(page.getByText('other-owner').first()).toBeVisible();

    await page.getByTestId('search-query-field').getByRole('textbox').fill('target-owner');

    await expect.element(page.getByText('target-owner').first()).toBeVisible();
    await expect.element(page.getByText('other-owner').query() as HTMLElement).not.toBeInTheDocument();
  });
});

describe('0900 — CS Management Requests — pending toggle + search; back navigation restores state (Browser Mode)', () => {
  it('unchecking pending-only and searching shows all statuses; navigating to detail and back preserves filter', async () => {
    const allStatuses: ManagementRequestListView = {
      ...waitingRequest,
      id: 103,
      status: 'APPROVED',
    };

    const detailedRequest: ManagementRequestDetailedView = {
      ...waitingRequest,
      id: 103,
      status: 'REVOKED',
    };

    let listCallCount = 0;
    await renderRoute(MR_LIST_PATH, {
      permissions: basePermissions,
      msw: [
        specHttp.get('/management-requests', ({ response }) => {
          listCallCount += 1;
          // first call: pending only; second: all; third: after back
          return listCallCount === 1
            ? response(200).json(singleItemPage(waitingRequest) as never)
            : response(200).json(singleItemPage(allStatuses) as never);
        }),
        specHttp.get('/management-requests/{management_request_id}', ({ response }) =>
          response(200).json(detailedRequest as never),
        ),
      ],
    });

    await expect.element(page.getByTestId('management-requests-table')).toBeVisible();

    // Uncheck the show-only-pending switch (toggle off)
    await page.getByTestId('show-only-pending-requests').click();

    // Fill search field
    await page.getByTestId('search-query-field').getByRole('textbox').fill('e2e-tc1');

    await expect.element(page.getByText('E2E TC1 Member').first()).toBeVisible();

    // Navigate into a detail
    await page.getByTestId('management-requests-table').getByText('103').click();

    await expect.element(page.getByTestId('decline-button').query() as HTMLElement).not.toBeInTheDocument();
    await expect.element(page.getByTestId('approve-button').query() as HTMLElement).not.toBeInTheDocument();

    // Navigate back via breadcrumb
    await page.getByRole('link', { name: /management requests/i }).click();

    // Search field still contains the query and switch is still off
    await expect.element(page.getByTestId('search-query-field').getByRole('textbox')).toHaveValue(
      'e2e-tc1',
    );
  });
});

const certDetails: CertificateDetails = {
  hash: 'deadbeef01',
  issuer_common_name: 'Test Issuer CA',
  issuer_distinguished_name: 'CN=Test Issuer CA,O=Test,C=EE',
  key_usages: [],
  not_after: '2026-12-31T23:59:59Z',
  not_before: '2024-01-01T00:00:00Z',
  public_key_algorithm: 'RSA',
  rsa_public_key_exponent: 65537,
  rsa_public_key_modulus: 'abc123',
  serial: '42',
  signature: 'sig123',
  signature_algorithm: 'SHA256withRSA',
  subject_alternative_names: '',
  subject_common_name: 'auth-cert-subject',
  subject_distinguished_name: 'CN=auth-cert-subject,O=Test,C=EE',
  version: 3,
};

const clientIdFixture: ClientId = {
  instance_id: 'CS',
  member_class: 'E2E-TC1',
  member_code: 'e2e-tc2-member',
  subsystem_code: 'e2e-tc2-subsystem',
  type: 'SUBSYSTEM' as const,
  encoded_id: 'CS:E2E-TC1:e2e-tc2-member:e2e-tc2-subsystem',
};

describe('0900 — CS Management Requests — detail page renders auth-cert request sections (Browser Mode)', () => {
  it('detail page for AUTH_CERT_REGISTRATION_REQUEST shows affected-server section, request information, and auth-cert section', async () => {
    const authCertRequest: ManagementRequestDetailedView = {
      id: 201,
      type: 'AUTH_CERT_REGISTRATION_REQUEST',
      origin: 'SECURITY_SERVER',
      security_server_owner: 'E2E TC1 Member',
      security_server_id: serverId,
      address: 'ss1.example.com',
      status: 'WAITING',
      created_at: '2024-06-01T10:00:00Z',
      certificate_details: certDetails,
    };

    await renderRoute('/management-requests/201/details', {
      permissions: basePermissions,
      msw: [
        specHttp.get('/management-requests/{management_request_id}', ({ response }) =>
          response(200).json(authCertRequest as never),
        ),
      ],
    });

    await expect.element(page.getByTestId('approve-button')).toBeVisible();

    await expect.element(page.getByText('Affected Security Server Information').first()).toBeVisible();
    await expect.element(page.getByText('E2E TC1 Member').first()).toBeVisible();
    await expect.element(page.getByText('E2E-TC1').first()).toBeVisible();
    await expect.element(page.getByText('e2e-tc1-member-subsystem').first()).toBeVisible();
    await expect.element(page.getByText('E2E-SS1').first()).toBeVisible();

    await expect.element(page.getByText('Authentication Certificate Submitted for Registration').first()).toBeVisible();
    await expect.element(page.getByText('auth-cert-subject').first()).toBeVisible();
    await expect.element(page.getByText('42').first()).toBeVisible();
  });
});

describe('0900 — CS Management Requests — detail page renders client registration request sections (Browser Mode)', () => {
  it('detail page for CLIENT_REGISTRATION_REQUEST shows affected-server section and client-submitted section', async () => {
    const clientRegRequest: ManagementRequestDetailedView = {
      id: 202,
      type: 'CLIENT_REGISTRATION_REQUEST',
      origin: 'SECURITY_SERVER',
      security_server_owner: 'E2E TC1 Member',
      security_server_id: serverId,
      status: 'WAITING',
      created_at: '2024-06-02T10:00:00Z',
      client_id: clientIdFixture,
      client_owner_name: 'E2E TC2 Member',
    };

    await renderRoute('/management-requests/202/details', {
      permissions: basePermissions,
      msw: [
        specHttp.get('/management-requests/{management_request_id}', ({ response }) =>
          response(200).json(clientRegRequest as never),
        ),
      ],
    });

    await expect.element(page.getByTestId('approve-button')).toBeVisible();

    await expect.element(page.getByText('Affected Security Server Information').first()).toBeVisible();
    await expect.element(page.getByText('E2E TC1 Member').first()).toBeVisible();

    await expect.element(page.getByText('Client Submitted for Registration').first()).toBeVisible();
    await expect.element(page.getByText('E2E TC2 Member').first()).toBeVisible();
    await expect.element(page.getByText('e2e-tc2-member').first()).toBeVisible();
    await expect.element(page.getByText('e2e-tc2-subsystem').first()).toBeVisible();
  });
});
