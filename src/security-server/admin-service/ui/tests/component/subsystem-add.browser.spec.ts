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

const selectableSubsystemsFixture: Client[] = [
  {
    id: 'DEV:COM:1234:TestService',
    instance_id: 'DEV',
    member_name: 'Test member',
    member_class: 'COM',
    member_code: '1234',
    subsystem_code: 'TestService',
    owner: false,
    has_valid_local_sign_cert: false,
    connection_type: 'HTTPS',
    status: 'REGISTERED',
  },
];

const existingSubsystemsFixture: Client[] = [];

validateBody({ type: 'array', items: clientSchema }, selectableSubsystemsFixture);
validateBody({ type: 'array', items: clientSchema }, existingSubsystemsFixture);

// ── Route params matching the legacy feature's "Test member" / COM / 1234 ─────

const INSTANCE_ID = 'DEV';
const MEMBER_CLASS = 'COM';
const MEMBER_CODE = '1234';
const MEMBER_NAME = 'Test member';
const ADD_SUBSYSTEM_PATH = `/add-subsystem/${INSTANCE_ID}/${MEMBER_CLASS}/${MEMBER_CODE}/${encodeURIComponent(MEMBER_NAME)}`;

// ── Permissions ───────────────────────────────────────────────────────────────

const addSubsystemPermissions = [
  Permissions.VIEW_CLIENTS,
  Permissions.ADD_CLIENT,
  Permissions.VIEW_CLIENT_DETAILS,
];

// ── Base MSW handlers for AddSubsystem mount ──────────────────────────────────

function baseHandlers() {
  return [
    // fetchData: selectable subsystems (global, exclude_local=true, show_members=false)
    specHttp.get('/clients', ({ request, response }) => {
      const url = new URL(request.url);
      if (url.searchParams.get('exclude_local') === 'true') {
        return response(200).json(selectableSubsystemsFixture);
      }
      // fetchData: existing subsystems (internal_search=true)
      return response(200).json(existingSubsystemsFixture);
    }),
  ];
}

// ── Specs ─────────────────────────────────────────────────────────────────────

// MIGRATED-FROM: 0500-ss-client-subsystems.feature :: "Add subsystem was cancelled"
describe('Add subsystem — cancelled (Browser Mode)', () => {
  it('renders prefilled member fields, then cancel navigates back to clients list', async () => {
    const { router } = await renderRoute(ADD_SUBSYSTEM_PATH, {
      permissions: addSubsystemPermissions,
      msw: baseHandlers(),
    });

    // The form is visible: select-subsystem-button confirms AddSubsystem rendered
    await expect.element(page.getByTestId('select-subsystem-button')).toBeVisible();

    // Member name field is pre-populated from route params
    const memberNameField = page.getByTestId('selected-member-name').getByRole('textbox');
    await expect.element(memberNameField).toBeVisible();
    const memberNameValue = (await memberNameField.element() as HTMLInputElement).value;
    expect(memberNameValue).toBe('Test member');

    // Member class field is pre-populated
    const memberClassField = page.getByTestId('selected-member-class').getByRole('textbox');
    await expect.element(memberClassField).toBeVisible();
    const memberClassValue = (await memberClassField.element() as HTMLInputElement).value;
    expect(memberClassValue).toBe('COM');

    // Member code field is pre-populated
    const memberCodeField = page.getByTestId('selected-member-code').getByRole('textbox');
    await expect.element(memberCodeField).toBeVisible();
    const memberCodeValue = (await memberCodeField.element() as HTMLInputElement).value;
    expect(memberCodeValue).toBe('1234');

    // Cancel button is visible
    await expect.element(page.getByTestId('cancel-button')).toBeVisible();

    // Click Cancel — router navigates back to /clients
    await page.getByTestId('cancel-button').click();

    // The form is gone (navigated away)
    await expect.element(page.getByTestId('select-subsystem-button')).not.toBeInTheDocument();

    // No POST was fired — client list remains the same, route is /clients
    expect(router.currentRoute.value.path).toBe('/clients');
  });
});
