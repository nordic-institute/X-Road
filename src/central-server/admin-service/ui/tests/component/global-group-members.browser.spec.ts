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
import { HttpResponse } from 'msw';
import type { RequestHandler } from 'msw';
import { renderRoute } from '../setup/render-route';
import { specHttp } from '../setup/spec-http';
import { submitDialogForm } from '../setup/dialog-helpers';
import { Permissions } from '@/global';
import type { GlobalGroupResource, GroupMemberListView, Members, PagedClients } from '@/openapi-types';

const GROUP_CODE = 'test-group';
const OWNER_GROUP_CODE = 'security-server-owners';

const groupFixture: GlobalGroupResource = {
  code: GROUP_CODE,
  description: 'Test group description',
  member_count: 1,
  updated_at: '2024-01-01T00:00:00Z',
};

const ownerGroupFixture: GlobalGroupResource = {
  code: OWNER_GROUP_CODE,
  description: 'Security server owners',
  member_count: 0,
  updated_at: '2024-01-01T00:00:00Z',
};

const memberFixture: GroupMemberListView = {
  id: 1,
  name: 'Test Member 1',
  created_at: '2024-01-01T00:00:00Z',
  client_id: {
    instance_id: 'CS',
    member_class: 'E2E-TC1',
    member_code: 'test-member-1',
    type: 'MEMBER',
    encoded_id: 'CS:E2E-TC1:test-member-1',
  },
};

const secondMemberFixture: GroupMemberListView = {
  id: 2,
  name: 'Test Member 2',
  created_at: '2024-01-02T00:00:00Z',
  client_id: {
    instance_id: 'CS',
    member_class: 'E2E-TC1',
    member_code: 'test-member-2',
    type: 'MEMBER',
    encoded_id: 'CS:E2E-TC1:test-member-2',
  },
};

const noMembers: GroupMemberListView[] = [];
const oneMembers: GroupMemberListView[] = [memberFixture];
const twoMembers: GroupMemberListView[] = [memberFixture, secondMemberFixture];

const candidateClient: PagedClients = {
  clients: [
    {
      client_id: {
        instance_id: 'CS',
        member_class: 'E2E-TC2',
        member_code: 'candidate-1',
        type: 'MEMBER',
        encoded_id: 'CS:E2E-TC2:candidate-1',
      },
      member_name: 'Candidate Member',
    },
  ],
  paging_metadata: { total_items: 1, items: 1, limit: 25, offset: 0 },
};

const noCandidates: PagedClients = {
  clients: [],
  paging_metadata: { total_items: 0, items: 0, limit: 25, offset: 0 },
};

const groupPermissions = [
  Permissions.VIEW_GLOBAL_GROUPS,
  Permissions.VIEW_GROUP_DETAILS,
  Permissions.EDIT_GROUP_DESCRIPTION,
  Permissions.ADD_AND_REMOVE_GROUP_MEMBERS,
  Permissions.DELETE_GROUP,
];

function groupGroupHandler(fixture: GlobalGroupResource) {
  return specHttp.get('/global-groups/{group_code}', ({ response }) => response(200).json(fixture));
}

function membersHandler(fixture: GroupMemberListView[]) {
  return specHttp.post('/global-groups/{group_code}/members', ({ response }) =>
    response(200).json({ items: fixture, paging_metadata: { total_items: fixture.length, items: fixture.length, limit: 25, offset: 0 } } as never),
  );
}

function clientsHandler(fixture: PagedClients) {
  return specHttp.get('/clients', ({ response }) => response(200).json(fixture as never));
}

async function renderGroupRoute(
  code: string,
  fixture: GlobalGroupResource,
  members: GroupMemberListView[],
  extraHandlers: RequestHandler[] = [],
) {
  return renderRoute(`/settings/global-resources/global-groups/${code}`, {
    permissions: groupPermissions,
    msw: [groupGroupHandler(fixture), membersHandler(members), ...extraHandlers],
  });
}

describe('0480 — CS Global Group Members — edit description with invalid chars shows inline error (Browser Mode)', () => {
  it('typing invalid description characters in edit dialog shows validation error', async () => {
    await renderGroupRoute(GROUP_CODE, groupFixture, noMembers);

    await expect.element(page.getByTestId('global-group-edit-button')).toBeVisible();
    await page.getByTestId('global-group-edit-button').click();

    await page.getByTestId('global-group-description-edit').getByRole('textbox').fill('invalid$€chars');

    submitDialogForm();

    await expect.element(page.getByText('Use valid description characters only')).toBeVisible();
  });
});

describe('0480 — CS Global Group Members — add to owners group shows cannot-add error (Browser Mode)', () => {
  it('submitting add-members for owner group returns 400 and shows owners group error', async () => {
    await renderGroupRoute(OWNER_GROUP_CODE, ownerGroupFixture, noMembers, [
      clientsHandler(candidateClient),
      specHttp.untyped.post('/api/v1/global-groups/:group_code/members/add', () =>
        HttpResponse.json({ status: 400, error: { code: 'cannot_add_member_to_owners_group' } }, { status: 400 }),
      ),
    ]);

    await expect.element(page.getByTestId('add-member-button')).toBeVisible();
    await page.getByTestId('add-member-button').click();

    await expect.element(page.getByTestId('select-members-list')).toBeVisible();

    await page.getByTestId('select-members-list').getByRole('checkbox').nth(1).click();

    submitDialogForm();

    await expect.element(page.getByText('Cannot add members to server owner group')).toBeVisible();
  });
});

describe('0480 — CS Global Group Members — add members to non-owner group shows them in list (Browser Mode)', () => {
  it('selecting candidates and confirming add renders both members in the members table', async () => {
    const addedMembers: Members = {
      items: [memberFixture.client_id.encoded_id!, secondMemberFixture.client_id.encoded_id!],
    };

    const twoClients: PagedClients = {
      clients: [
        {
          client_id: memberFixture.client_id,
          member_name: memberFixture.name,
        },
        {
          client_id: secondMemberFixture.client_id,
          member_name: secondMemberFixture.name,
        },
      ],
      paging_metadata: { total_items: 2, items: 2, limit: 25, offset: 0 },
    };

    let membersCallCount = 0;
    await renderRoute(`/settings/global-resources/global-groups/${GROUP_CODE}`, {
      permissions: groupPermissions,
      msw: [
        groupGroupHandler(groupFixture),
        specHttp.post('/global-groups/{group_code}/members', ({ response }) => {
          membersCallCount += 1;
          return membersCallCount === 1
            ? response(200).json({ items: noMembers, paging_metadata: { total_items: 0, items: 0, limit: 25, offset: 0 } } as never)
            : response(200).json({ items: twoMembers, paging_metadata: { total_items: 2, items: 2, limit: 25, offset: 0 } } as never);
        }),
        clientsHandler(twoClients),
        specHttp.post('/global-groups/{group_code}/members/add', ({ response }) =>
          response(201).json(addedMembers),
        ),
      ],
    });

    await expect.element(page.getByTestId('add-member-button')).toBeVisible();
    await page.getByTestId('add-member-button').click();

    await expect.element(page.getByTestId('select-members-list')).toBeVisible();

    await page.getByText(memberFixture.name).first().click();
    await page.getByText(secondMemberFixture.name).first().click();

    submitDialogForm();

    await expect.element(page.getByText(memberFixture.name).first()).toBeVisible();
    await expect.element(page.getByText(secondMemberFixture.name).first()).toBeVisible();
  });
});

describe('0480 — CS Global Group Members — existing members excluded from add-members candidates (Browser Mode)', () => {
  it('opening add-members dialog when member already in group shows no matching candidates', async () => {
    await renderGroupRoute(GROUP_CODE, groupFixture, oneMembers, [clientsHandler(noCandidates)]);

    await expect.element(page.getByTestId('global-group-members')).toBeVisible();

    await page.getByTestId('add-member-button').click();

    await expect.element(page.getByTestId('select-members-list')).toBeVisible();

    expect(page.getByTestId('select-members-list').getByText('test-member-1').query()).toBeNull();
  });
});

describe('0480 — CS Global Group Members — search field in add-members dialog filters candidates (Browser Mode)', () => {
  it('typing in the search field updates the visible candidate rows', async () => {
    const filteredCandidates: PagedClients = {
      clients: [
        {
          client_id: {
            instance_id: 'CS',
            member_class: 'E2E-TC2',
            member_code: 'filtered-match',
            type: 'MEMBER',
            encoded_id: 'CS:E2E-TC2:filtered-match',
          },
          member_name: 'Filtered Match',
        },
      ],
      paging_metadata: { total_items: 1, items: 1, limit: 25, offset: 0 },
    };

    let clientsCallCount = 0;
    await renderRoute(`/settings/global-resources/global-groups/${GROUP_CODE}`, {
      permissions: groupPermissions,
      msw: [
        groupGroupHandler(groupFixture),
        membersHandler(noMembers),
        specHttp.get('/clients', ({ response }) => {
          clientsCallCount += 1;
          return clientsCallCount === 1
            ? response(200).json(candidateClient as never)
            : response(200).json(filteredCandidates as never);
        }),
      ],
    });

    await page.getByTestId('add-member-button').click();

    await expect.element(page.getByTestId('select-members-list')).toBeVisible();
    await expect.element(page.getByText('Candidate Member')).toBeVisible();

    await page
      .getByTestId('member-subsystem-search-field')
      .getByRole('textbox')
      .fill('Filtered');

    await expect.element(page.getByText('Filtered Match')).toBeVisible();
    await expect.element(page.getByText('Candidate Member')).not.toBeInTheDocument();
  });
});

describe('0480 — CS Global Group Members — selection preserved when search filter changes (Browser Mode)', () => {
  it('checking a candidate then typing in search still shows that candidate checked', async () => {
    const twoClients: PagedClients = {
      clients: [
        {
          client_id: {
            instance_id: 'CS',
            member_class: 'E2E-TC2',
            member_code: 'candidate-keep',
            type: 'MEMBER',
            encoded_id: 'CS:E2E-TC2:candidate-keep',
          },
          member_name: 'Keep Me Selected',
        },
        {
          client_id: {
            instance_id: 'CS',
            member_class: 'E2E-TC2',
            member_code: 'candidate-other',
            type: 'MEMBER',
            encoded_id: 'CS:E2E-TC2:candidate-other',
          },
          member_name: 'Other Candidate',
        },
      ],
      paging_metadata: { total_items: 2, items: 2, limit: 25, offset: 0 },
    };

    const filteredToKeep: PagedClients = {
      clients: [twoClients.clients![0]],
      paging_metadata: { total_items: 1, items: 1, limit: 25, offset: 0 },
    };

    let clientsCallCount = 0;
    await renderRoute(`/settings/global-resources/global-groups/${GROUP_CODE}`, {
      permissions: groupPermissions,
      msw: [
        groupGroupHandler(groupFixture),
        membersHandler(noMembers),
        specHttp.get('/clients', ({ response }) => {
          clientsCallCount += 1;
          return clientsCallCount === 1
            ? response(200).json(twoClients as never)
            : response(200).json(filteredToKeep as never);
        }),
      ],
    });

    await page.getByTestId('add-member-button').click();

    await expect.element(page.getByTestId('select-members-list')).toBeVisible();
    await expect.element(page.getByText('Keep Me Selected').first()).toBeVisible();

    await page.getByTestId('select-members-list').getByRole('checkbox').nth(1).click();

    await expect.element(page.getByTestId('dialog-save-button')).not.toBeDisabled();

    await page
      .getByTestId('member-subsystem-search-field')
      .getByRole('textbox')
      .fill('Keep');

    await expect.element(page.getByTestId('dialog-save-button')).not.toBeDisabled();
  });
});

describe('0480 — CS Global Group Members — delete member confirmation gating and removal (Browser Mode)', () => {
  it('save disabled with wrong code, enabled with correct code, member removed after submit', async () => {
    let membersCallCount = 0;
    await renderRoute(`/settings/global-resources/global-groups/${GROUP_CODE}`, {
      permissions: groupPermissions,
      msw: [
        groupGroupHandler(groupFixture),
        specHttp.post('/global-groups/{group_code}/members', ({ response }) => {
          membersCallCount += 1;
          return membersCallCount === 1
            ? response(200).json({ items: oneMembers, paging_metadata: { total_items: 1, items: 1, limit: 25, offset: 0 } } as never)
            : response(200).json({ items: noMembers, paging_metadata: { total_items: 0, items: 0, limit: 25, offset: 0 } } as never);
        }),
        specHttp.delete('/global-groups/{group_code}/members/{client_id}', ({ response }) => response(204).empty()),
      ],
    });

    await expect.element(page.getByTestId('global-group-members')).toBeVisible();
    await expect.element(page.getByTestId('delete-member-button').first()).toBeVisible();

    await page.getByTestId('delete-member-button').first().click();

    await expect.element(page.getByTestId('verify-member-code')).toBeVisible();
    await expect.element(page.getByTestId('dialog-save-button')).toBeDisabled();

    await page.getByTestId('verify-member-code').getByRole('textbox').fill('wrong-code');

    await expect.element(page.getByTestId('dialog-save-button')).toBeDisabled();

    await page.getByTestId('verify-member-code').getByRole('textbox').fill(memberFixture.client_id.member_code);

    await expect.element(page.getByTestId('dialog-save-button')).not.toBeDisabled();

    submitDialogForm();

    await expect.element(page.getByText(memberFixture.name)).not.toBeInTheDocument();
  });
});
