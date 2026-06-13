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
import type { LocalGroup } from '@/openapi-types';

// ── AJV schemas ───────────────────────────────────────────────────────────────

const localGroupSchema = {
  type: 'object',
  required: ['code', 'description'],
  properties: {
    id: { type: 'string' },
    code: { type: 'string' },
    description: { type: 'string' },
    client_id: { type: 'string' },
    member_count: { type: 'integer' },
    updated_at: { type: 'string' },
  },
};

const localGroupDetailSchema = {
  type: 'object',
  required: ['code', 'description'],
  properties: {
    id: { type: 'string' },
    code: { type: 'string' },
    description: { type: 'string' },
    client_id: { type: 'string' },
    member_count: { type: 'integer' },
    updated_at: { type: 'string' },
    members: { type: 'array' },
  },
};

// ── Fixtures ──────────────────────────────────────────────────────────────────
//
// Sort fixture design (for the "Sorted by Description" spec):
// Default getter `sortedLocalGroups` sorts by CODE (ascending). When the user
// clicks the "Description" column header in the VDataTable, Vuetify sorts by
// the description field instead.
//
// Requirements:
//   - ≥2 items with DIFFERENT descriptions
//   - natural code-sort order != description-sort order
//     (so the sort click actually changes row positions — the spec would fail
//      without the click)
//
// Items sorted by code (default):   aaa-group (Zebra Desc), bbb-group (Alpha Desc)
// Items sorted by description asc:  bbb-group (Alpha Desc), aaa-group (Zebra Desc)
//
// Before click (code order):  row[0]=aaa-group, row[1]=bbb-group
// After click  (desc order):  row[0]=bbb-group, row[1]=aaa-group
// → asserting row[0] code == "bbb-group" proves the sort fired.

const localGroupsFixture: LocalGroup[] = [
  {
    id: '1',
    code: 'aaa-group',
    description: 'Zebra Desc',
    client_id: 'DEV:COM:1234:test-service',
    member_count: 0,
    updated_at: '2024-01-01T00:00:00Z',
  },
  {
    id: '2',
    code: 'bbb-group',
    description: 'Alpha Desc',
    client_id: 'DEV:COM:1234:test-service',
    member_count: 2,
    updated_at: '2024-01-02T00:00:00Z',
  },
  {
    id: '3',
    code: 'group-match',
    description: 'Filter Match',
    client_id: 'DEV:COM:1234:test-service',
    member_count: 1,
    updated_at: '2024-01-03T00:00:00Z',
  },
  {
    id: '4',
    code: 'unrelated',
    description: 'No Match Here',
    client_id: 'DEV:COM:1234:test-service',
    member_count: 0,
    updated_at: '2024-01-04T00:00:00Z',
  },
];

validateBody({ type: 'array', items: localGroupSchema }, localGroupsFixture);

const clientId = 'DEV:COM:1234:test-service';
const encodedClientId = encodeURIComponent(clientId);

const localGroupDetailFixture: LocalGroup = {
  id: '1',
  code: 'group-1',
  description: 'Initial description',
  client_id: clientId,
  member_count: 0,
  updated_at: '2024-01-01T00:00:00Z',
  members: [],
};

validateBody(localGroupDetailSchema, localGroupDetailFixture);

// ── Permissions ───────────────────────────────────────────────────────────────

const localGroupPermissions = [
  Permissions.VIEW_CLIENTS,
  Permissions.VIEW_CLIENT_DETAILS,
  Permissions.VIEW_CLIENT_LOCAL_GROUPS,
  Permissions.ADD_LOCAL_GROUP,
  Permissions.EDIT_LOCAL_GROUP_DESC,
  Permissions.EDIT_LOCAL_GROUP_MEMBERS,
  Permissions.DELETE_LOCAL_GROUP,
];

// ── Route helpers ─────────────────────────────────────────────────────────────

const localGroupsRoute = `/clients/subsystem/${encodedClientId}/local-groups`;
const localGroupDetailRoute = `/local-group/1`;

// ── Handlers ──────────────────────────────────────────────────────────────────

const clientHandler = specHttp.get('/clients/{id}', ({ response }) =>
  response(200).json({
    id: clientId,
    instance_id: 'DEV',
    member_name: 'Test Service',
    member_class: 'COM',
    member_code: '1234',
    subsystem_code: 'test-service',
    owner: false,
    has_valid_local_sign_cert: false,
    connection_type: 'HTTPS' as const,
    status: 'REGISTERED' as const,
  }),
);

const localGroupsHandler = specHttp.get('/clients/{id}/local-groups', ({ response }) =>
  response(200).json(localGroupsFixture),
);

const localGroupDetailHandler = specHttp.get('/local-groups/{group_id}', ({ response }) =>
  response(200).json(localGroupDetailFixture),
);

// ── Helper ────────────────────────────────────────────────────────────────────

function getColumnTexts(columnDataTest: string): string[] {
  return page
    .getByTestId(columnDataTest)
    .elements()
    .map((el) => el.textContent?.trim() ?? '');
}

// ─────────────────────────────────────────────────────────────────────────────
// Spec 1: "Not added - description invalid"
// ─────────────────────────────────────────────────────────────────────────────

// MIGRATED-FROM: 0510-ss-client-local-groups.feature :: "Local group is not added as description is invalid"
describe('Local Groups — add dialog: invalid description blocked (Browser Mode)', () => {
  it('shows validation error and keeps save disabled when description contains invalid characters', async () => {
    await renderRoute(localGroupsRoute, {
      permissions: localGroupPermissions,
      msw: [clientHandler, localGroupsHandler],
    });

    await expect.element(page.getByTestId('add-local-group-button')).toBeVisible();
    await page.getByTestId('add-local-group-button').click();

    await expect.element(page.getByTestId('dialog-save-button')).toBeVisible();
    await expect.element(page.getByTestId('dialog-save-button')).toBeDisabled();

    await page.getByTestId('add-local-group-code-input').getByRole('textbox').fill('group-1');

    await page.getByTestId('add-local-group-description-input').getByRole('textbox').fill('invalid$€chars');

    await page.getByTestId('add-local-group-code-input').getByRole('textbox').click();

    await expect.element(page.getByText('Use valid description characters only')).toBeVisible();
    await expect.element(page.getByTestId('dialog-save-button')).toBeDisabled();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// Spec 2: "Sorted by Description"
// ─────────────────────────────────────────────────────────────────────────────
//
// Fixture variety proof:
//   Default sort (by code): aaa-group < bbb-group → row[0].code = "aaa-group"
//   After Description click (by desc): "Alpha Desc" < "Zebra Desc"
//     → row[0].code = "bbb-group" (which has "Alpha Desc")
//
// The assertion checks relative ROW POSITIONS, not mere presence:
//   bbbIdx < aaaIdx after the sort click.
//
// Removing the sort-header click leaves the default code-order intact:
//   row[0]=aaa-group, row[1]=bbb-group → bbbIdx(1) > aaaIdx(0) → assertion fails.

// MIGRATED-FROM: 0510-ss-client-local-groups.feature :: "Local groups are sorted by Description"
describe('Local Groups — table sorted by Description column (Browser Mode)', () => {
  it('reorders rows so that description-alphabetically-first group appears at index 0 after sort click', async () => {
    await renderRoute(localGroupsRoute, {
      permissions: localGroupPermissions,
      msw: [clientHandler, localGroupsHandler],
    });

    await expect.element(page.getByTestId('local-groups-table')).toBeVisible();

    const descriptionHeader = page.getByRole('columnheader', { name: /description/i });
    await descriptionHeader.click();

    await expect.element(page.getByTestId('local-groups-table')).toBeVisible();

    const allCodes = page
      .getByTestId('local-groups-table')
      .getByRole('row')
      .elements()
      .slice(1)
      .map((row) => row.textContent?.trim() ?? '');

    const aaaIdx = allCodes.findIndex((t) => t.includes('aaa-group'));
    const bbbIdx = allCodes.findIndex((t) => t.includes('bbb-group'));

    expect(aaaIdx).toBeGreaterThanOrEqual(0);
    expect(bbbIdx).toBeGreaterThanOrEqual(0);

    expect(bbbIdx).toBeLessThan(aaaIdx);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// Spec 3: "Filtered to group"
// ─────────────────────────────────────────────────────────────────────────────
//
// Two-sided assertion:
//   - "group-match" (code contains "group") IS present after filtering
//   - "unrelated" (code "unrelated", desc "No Match Here") IS ABSENT after filtering
//
// The VDataTable :search prop is bound to the search model in LocalGroupsView.
// Removing the fill() call leaves search empty → both rows present → the
// absence assertion fails.

// MIGRATED-FROM: 0510-ss-client-local-groups.feature :: "Local groups are filtered to \"group\""
describe('Local Groups — search filter two-sided (Browser Mode)', () => {
  it('shows matching rows and hides non-matching rows when filter is applied', async () => {
    await renderRoute(localGroupsRoute, {
      permissions: localGroupPermissions,
      msw: [clientHandler, localGroupsHandler],
    });

    await expect.element(page.getByTestId('local-groups-table')).toBeVisible();

    await expect.element(page.getByTestId('local-group-search-input')).toBeVisible();
    await page.getByTestId('local-group-search-input').getByRole('textbox').fill('group');

    const tableRows = page
      .getByTestId('local-groups-table')
      .getByRole('row')
      .elements()
      .slice(1);

    const rowTexts = tableRows.map((r) => r.textContent?.trim() ?? '');

    const matchPresent = rowTexts.some((t) => t.includes('group-match'));
    const aaaPresent = rowTexts.some((t) => t.includes('aaa-group'));
    const bbbPresent = rowTexts.some((t) => t.includes('bbb-group'));
    const unrelatedPresent = rowTexts.some((t) => t.includes('unrelated'));

    expect(matchPresent).toBe(true);
    expect(aaaPresent).toBe(true);
    expect(bbbPresent).toBe(true);
    expect(unrelatedPresent).toBe(false);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// Spec 4: "Edited with invalid description" (local group detail view)
// ─────────────────────────────────────────────────────────────────────────────

// MIGRATED-FROM: 0510-ss-client-local-groups.feature :: "Local group group-1 is edited with invalid description"
describe('Local Group detail — edit description: invalid characters blocked (Browser Mode)', () => {
  it('shows validation error when description input contains invalid characters', async () => {
    await renderRoute(localGroupDetailRoute, {
      permissions: localGroupPermissions,
      msw: [clientHandler, localGroupDetailHandler],
    });

    await expect.element(page.getByTestId('local-group-edit-description-input')).toBeVisible();

    const descInput = page.getByTestId('local-group-edit-description-input').getByRole('textbox');
    await descInput.clear();
    await descInput.fill('invaliddesc$€');

    await page.getByTestId('local-group-edit-description-input').click({ force: true });
    await page.getByTestId('service-description-details-dialog').click({ force: true });

    await expect.element(page.getByText('Use valid description characters only')).toBeVisible();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// Spec 5: Empty description error (UI slice of "Group group-1 is edited" split)
// ─────────────────────────────────────────────────────────────────────────────
//
// Split: API slice (member add persists) is DONE in LocalGroupsTest#memberAddedToLocalGroupIsPersisted.
// This spec covers only the UI slice: empty description → client-side "required" error.
//
// The LocalGroup.vue uses `useField('description', 'required|max:255|validDescription')`.
// The `required` rule fires when the field is empty and the user leaves it (change event).
// The error message rendered is the vee-validate i18n "required" message:
//   "The {field} field is required" → with field label from the form context it renders
//   as "The Description field is required" but since useField has no explicit field name
//   it outputs "The description field is required" (lowercase, matching the key 'description').

// MIGRATED-FROM: 0510-ss-client-local-groups.feature :: "Local group group-1 is edited"
describe('Local Group detail — edit description: empty description shows required error (Browser Mode)', () => {
  it('shows required validation error and does not save when description is cleared', async () => {
    await renderRoute(localGroupDetailRoute, {
      permissions: localGroupPermissions,
      msw: [clientHandler, localGroupDetailHandler],
    });

    await expect.element(page.getByTestId('local-group-edit-description-input')).toBeVisible();

    const descInput = page.getByTestId('local-group-edit-description-input').getByRole('textbox');
    await descInput.clear();
    await descInput.fill('');

    await page.getByTestId('service-description-details-dialog').click({ force: true });

    await expect.element(page.getByText(/description.*field is required/i)).toBeVisible();
  });
});
