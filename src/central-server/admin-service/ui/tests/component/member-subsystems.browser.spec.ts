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
import type { RequestHandler } from 'msw';
import { renderRoute } from '../setup/render-route';
import { specHttp } from '../setup/spec-http';
import { submitDialogForm } from '../setup/dialog-helpers';
import { Permissions } from '@/global';
import type { Client, Subsystem } from '@/openapi-types';

const memberId = 'CS:GOV:1000';

const memberFixture: Client = {
  client_id: {
    instance_id: 'CS',
    member_class: 'GOV',
    member_code: '1000',
    type: 'ClientId',
    encoded_id: 'CS:GOV:1000',
  },
  member_name: 'Test Member',
};

const subsystemFixture: Subsystem = {
  subsystem_id: {
    instance_id: 'CS',
    member_class: 'GOV',
    member_code: '1000',
    subsystem_code: 'TEST-SUB',
    type: 'ClientId',
    encoded_id: 'CS:GOV:1000:TEST-SUB',
  },
  subsystem_name: 'Test Subsystem Name',
  used_security_servers: [],
};

const memberPermissions = [
  Permissions.VIEW_MEMBERS,
  Permissions.VIEW_MEMBER_DETAILS,
  Permissions.ADD_MEMBER_SUBSYSTEM,
  Permissions.REMOVE_MEMBER_SUBSYSTEM,
  Permissions.EDIT_MEMBER_SUBSYSTEM,
];

const memberHandler = specHttp.get('/members/{member_id}', ({ response }) => response(200).json(memberFixture as never));

const subsystemsHandler = specHttp.get('/members/{member_id}/subsystems', ({ response }) =>
  response(200).json([subsystemFixture] as never),
);

async function renderSubsystems(extraHandlers: RequestHandler[] = []) {
  return renderRoute(`/members/${memberId}/subsystems`, {
    permissions: memberPermissions,
    msw: [memberHandler, subsystemsHandler, ...extraHandlers],
  });
}

describe('0450 — CS Member Subsystems — table render (Browser Mode)', () => {
  it('subsystems table shows rows for each subsystem belonging to the member', async () => {
    await renderSubsystems();

    await expect.element(page.getByTestId('subsystems-table')).toBeVisible();
    await expect.element(page.getByTestId('subsystem-code').first()).toBeVisible();
    await expect.element(page.getByText('TEST-SUB')).toBeVisible();
  });
});

describe('0450 — CS Member Subsystems — add subsystem dialog and immediate appearance (Browser Mode)', () => {
  it('filling code in add dialog and submitting shows new unregistered row in table', async () => {
    const newSubsystem: Subsystem = {
      subsystem_id: {
        instance_id: 'CS',
        member_class: 'GOV',
        member_code: '1000',
        subsystem_code: 'NEW-SUB',
        type: 'ClientId',
        encoded_id: 'CS:GOV:1000:NEW-SUB',
      },
      subsystem_name: undefined,
      used_security_servers: [],
    };

    let subsystemsCallCount = 0;
    await renderRoute(`/members/${memberId}/subsystems`, {
      permissions: memberPermissions,
      msw: [
        memberHandler,
        specHttp.get('/members/{member_id}/subsystems', ({ response }) => {
          subsystemsCallCount += 1;
          return subsystemsCallCount === 1
            ? response(200).json([] as never)
            : response(200).json([newSubsystem] as never);
        }),
        specHttp.post('/subsystems', ({ response }) => response(201).json(newSubsystem as never)),
      ],
    });

    await expect.element(page.getByTestId('subsystems-table')).toBeVisible();

    await page.getByTestId('add-subsystem').click();

    await page.getByTestId('add-subsystem-input').getByRole('textbox').fill('NEW-SUB');

    await expect.element(page.getByTestId('dialog-save-button')).not.toBeDisabled();
    submitDialogForm();

    await expect.element(page.getByText('NEW-SUB')).toBeVisible();
  });
});

describe('0450 — CS Member Subsystems — rename subsystem and immediate appearance (Browser Mode)', () => {
  it('opening rename dialog, entering new name, and submitting updates the row', async () => {
    const renamedSubsystem: Subsystem = {
      ...subsystemFixture,
      subsystem_name: 'Renamed Name',
    };

    let subsystemsCallCount = 0;
    await renderRoute(`/members/${memberId}/subsystems`, {
      permissions: memberPermissions,
      msw: [
        memberHandler,
        specHttp.get('/members/{member_id}/subsystems', ({ response }) => {
          subsystemsCallCount += 1;
          return subsystemsCallCount === 1
            ? response(200).json([subsystemFixture] as never)
            : response(200).json([renamedSubsystem] as never);
        }),
        specHttp.patch('/subsystems/{subsystem_id}', ({ response }) => response(200).json(memberFixture as never)),
      ],
    });

    await expect.element(page.getByTestId('subsystems-table')).toBeVisible();

    await page.getByTestId('rename-subsystem').first().click();

    const nameField = page.getByTestId('subsystem-name-input').getByRole('textbox');
    await nameField.fill('Renamed Name');

    submitDialogForm();

    await expect.element(page.getByText('Renamed Name')).toBeVisible();
  });
});

describe('0450 — CS Member Subsystems — delete unregistered subsystem (Browser Mode)', () => {
  it('clicking delete and confirming removes the row from the table', async () => {
    let subsystemsCallCount = 0;
    await renderRoute(`/members/${memberId}/subsystems`, {
      permissions: memberPermissions,
      msw: [
        memberHandler,
        specHttp.get('/members/{member_id}/subsystems', ({ response }) => {
          subsystemsCallCount += 1;
          return subsystemsCallCount === 1
            ? response(200).json([subsystemFixture] as never)
            : response(200).json([] as never);
        }),
        specHttp.delete('/subsystems/{subsystem_id}', ({ response }) => response(204).empty()),
      ],
    });

    await expect.element(page.getByTestId('subsystems-table')).toBeVisible();
    await expect.element(page.getByText('TEST-SUB')).toBeVisible();

    await page.getByTestId('delete-subsystem').first().click();

    await expect.element(page.getByTestId('dialog-simple')).toBeVisible();

    await page.getByTestId('dialog-save-button').click();

    await expect.element(page.getByText('TEST-SUB')).not.toBeInTheDocument();
  });
});
