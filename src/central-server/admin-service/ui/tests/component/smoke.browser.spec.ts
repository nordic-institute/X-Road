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
import { pagedClientsSchema } from '../setup/schemas';
import type { PagedClients } from '@/openapi-types';

const membersFixture: PagedClients = {
  clients: [
    {
      member_name: 'Test Organisation',
      client_id: {
        instance_id: 'CS',
        member_class: 'GOV',
        member_code: '1000',
        type: 'ClientId',
        encoded_id: 'CS:GOV:1000',
      },
    },
    {
      member_name: 'Alpha Corp',
      client_id: {
        instance_id: 'CS',
        member_class: 'GOV',
        member_code: '2000',
        type: 'ClientId',
        encoded_id: 'CS:GOV:2000',
      },
    },
  ],
  paging_metadata: {
    total_items: 2,
    items: 2,
    limit: 25,
    offset: 0,
  },
};

validateBody(pagedClientsSchema, membersFixture);

describe('CS UI smoke (Browser Mode)', () => {
  it('renders the members list with member names from the spec-bound MSW handler', async () => {
    await renderRoute('/members', {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      msw: [specHttp.get('/clients', ({ response }) => response(200).json(membersFixture as any))],
    });

    await expect.element(page.getByTestId('member-name').first()).toBeVisible();

    const combined = page
      .getByTestId('member-name')
      .elements()
      .map((el) => el.textContent?.trim() ?? '')
      .join('\n');

    expect(combined).toContain('Test Organisation');
    expect(combined).toContain('Alpha Corp');
  });
});
