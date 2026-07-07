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
    await expect.element(page.getByTestId('security-server-owner-name')).toHaveTextContent('E2E TC1 Member with Subsystems');
    await expect.element(page.getByTestId('security-server-owner-class')).toHaveTextContent('E2E-TC1');
    await expect.element(page.getByTestId('security-server-owner-code')).toHaveTextContent('e2e-tc1-member-subsystem');

    router.back();

    await expect.element(page.getByTestId('member-details')).toBeVisible();
    await expect.element(page.getByTestId('member-name')).toHaveTextContent('E2E TC1 Member with Subsystems');
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
