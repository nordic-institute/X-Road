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
import type { Client } from '@/openapi-types';

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

const clientListFixture: Client[] = [
  {
    id: 'CS:GOV:1234',
    instance_id: 'CS',
    member_name: 'Test member',
    member_class: 'GOV',
    member_code: '1234',
    owner: true,
    has_valid_local_sign_cert: true,
    connection_type: 'HTTPS',
    status: 'REGISTERED',
  },
  {
    id: 'CS:GOV:1234:SUBS1',
    instance_id: 'CS',
    member_name: 'Test member',
    member_class: 'GOV',
    member_code: '1234',
    subsystem_code: 'SUBS1',
    owner: false,
    has_valid_local_sign_cert: false,
    connection_type: 'HTTPS',
    status: 'REGISTERED',
  },
  {
    id: 'CS:GOV:5678',
    instance_id: 'CS',
    member_name: 'Beta Corp',
    member_class: 'GOV',
    member_code: '5678',
    owner: false,
    has_valid_local_sign_cert: false,
    connection_type: 'HTTPS',
    status: 'SAVED',
  },
  {
    id: 'CS:GOV:5678:BETA',
    instance_id: 'CS',
    member_name: 'Beta Corp',
    member_class: 'GOV',
    member_code: '5678',
    subsystem_code: 'BETA',
    owner: false,
    has_valid_local_sign_cert: false,
    connection_type: 'HTTPS',
    status: 'SAVED',
  },
  {
    id: 'CS:GOV:9000',
    instance_id: 'CS',
    member_name: 'Alpha Corp',
    member_class: 'GOV',
    member_code: '9000',
    owner: false,
    has_valid_local_sign_cert: false,
    connection_type: 'HTTPS',
    status: 'REGISTERED',
  },
  {
    id: 'CS:GOV:9000:ALPHA',
    instance_id: 'CS',
    member_name: 'Alpha Corp',
    member_class: 'GOV',
    member_code: '9000',
    subsystem_code: 'ALPHA',
    owner: false,
    has_valid_local_sign_cert: false,
    connection_type: 'HTTPS',
    status: 'SAVED',
  },
  {
    id: 'CS:GOV:9000:ZEBRA',
    instance_id: 'CS',
    member_name: 'Alpha Corp',
    member_class: 'GOV',
    member_code: '9000',
    subsystem_code: 'ZEBRA',
    owner: false,
    has_valid_local_sign_cert: false,
    connection_type: 'HTTPS',
    status: 'REGISTERED',
  },
];

validateBody({ type: 'array', items: clientSchema }, clientListFixture);

function getColumnTexts(testId: string): string[] {
  return page
    .getByTestId(testId)
    .elements()
    .map((el) => el.textContent?.trim() ?? '');
}

describe('Client list (Browser Mode)', () => {
  // MIGRATED-FROM: 0590-ss-client-list.feature :: "Client List search"
  it('filters the table when search text is entered', async () => {
    await renderRoute('/clients', {
      msw: [specHttp.get('/clients', ({ response }) => response(200).json(clientListFixture))],
    });

    await expect.element(page.getByTestId('client-name').first()).toBeVisible();

    const searchField = page.getByTestId('search-query-field').getByRole('textbox');
    await searchField.fill('Test member');

    await expect.element(page.getByTestId('client-name').first()).toBeVisible();

    const names = getColumnTexts('client-name');
    const joined = names.join('\n');
    expect(joined).toContain('Test member');
    expect(joined).not.toContain('Alpha Corp');
    expect(joined).not.toContain('Beta Corp');
  });

  // MIGRATED-FROM: 0590-ss-client-list.feature :: "Client List default sorting by name"
  it('renders clients sorted by name ascending by default — owner member first', async () => {
    await renderRoute('/clients', {
      msw: [specHttp.get('/clients', ({ response }) => response(200).json(clientListFixture))],
    });

    await expect.element(page.getByTestId('client-name').first()).toBeVisible();

    const ids = getColumnTexts('client-id');
    expect(ids.length).toBeGreaterThan(0);

    expect(ids[0]).toBe('CS:GOV:1234');

    const alphaIdx = ids.indexOf('CS:GOV:9000');
    const betaIdx = ids.indexOf('CS:GOV:5678');
    expect(alphaIdx).toBeGreaterThan(0);
    expect(betaIdx).toBeGreaterThan(0);
    expect(alphaIdx).toBeLessThan(betaIdx);
  });

  // MIGRATED-FROM: 0590-ss-client-list.feature :: "Client List sorting by ID desc"
  it('sorts subsystem IDs descending within their member group when ID column is clicked twice', async () => {
    await renderRoute('/clients', {
      msw: [specHttp.get('/clients', ({ response }) => response(200).json(clientListFixture))],
    });

    await expect.element(page.getByTestId('client-name').first()).toBeVisible();

    const idHeader = page.getByRole('columnheader', { name: 'ID' });
    await idHeader.click();
    await idHeader.click();

    await expect.element(page.getByTestId('client-id').first()).toBeVisible();

    const ids = getColumnTexts('client-id');
    expect(ids.length).toBeGreaterThan(0);

    const ownerIdx = ids.findIndex((id) => id === 'CS:GOV:1234');
    expect(ownerIdx).toBe(0);

    const alphaSubsystemIdx = ids.findIndex((id) => id === 'CS:GOV:9000:ALPHA');
    const zebraSubsystemIdx = ids.findIndex((id) => id === 'CS:GOV:9000:ZEBRA');
    expect(alphaSubsystemIdx).toBeGreaterThan(0);
    expect(zebraSubsystemIdx).toBeGreaterThan(0);
    expect(zebraSubsystemIdx).toBeLessThan(alphaSubsystemIdx);
  });

  // MIGRATED-FROM: 0590-ss-client-list.feature :: "Client List sorting by Status asc"
  it('sorts subsystems by status ascending within their member group when Status column is clicked', async () => {
    await renderRoute('/clients', {
      msw: [specHttp.get('/clients', ({ response }) => response(200).json(clientListFixture))],
    });

    await expect.element(page.getByTestId('client-name').first()).toBeVisible();

    const statusHeader = page.getByRole('columnheader', { name: 'Status' });
    await statusHeader.click();

    await expect.element(page.getByTestId('client-status').first()).toBeVisible();

    const ids = getColumnTexts('client-id');
    const statuses = getColumnTexts('client-status');
    expect(ids.length).toEqual(statuses.length);
    expect(ids.length).toBeGreaterThan(0);

    const alphaIdx = ids.findIndex((id) => id === 'CS:GOV:9000:ALPHA');
    const zebraIdx = ids.findIndex((id) => id === 'CS:GOV:9000:ZEBRA');
    expect(alphaIdx).toBeGreaterThan(0);
    expect(zebraIdx).toBeGreaterThan(0);

    expect(statuses[alphaIdx].toLowerCase()).toContain('saved');
    expect(statuses[zebraIdx].toLowerCase()).toContain('registered');
    expect(zebraIdx).toBeLessThan(alphaIdx);
  });
});
