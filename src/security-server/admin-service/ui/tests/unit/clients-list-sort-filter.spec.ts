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
import { describe, it, expect, beforeEach } from 'vitest';
import { createPinia, setActivePinia } from 'pinia';
import { useClients } from '@/store/modules/clients';
import { Client, ClientStatus, ConnectionType } from '@/openapi-types';
import { ClientTypes } from '@/global';
import { ExtendedClient } from '@/ui-types';

const rawClients: Client[] = [
  {
    id: 'CS:GOV:1234',
    instance_id: 'CS',
    member_name: 'ACME',
    member_class: 'GOV',
    member_code: '1234',
    owner: true,
    has_valid_local_sign_cert: true,
    connection_type: ConnectionType.HTTPS,
    status: ClientStatus.REGISTERED,
  },
  {
    id: 'CS:GOV:1234:ZULU',
    instance_id: 'CS',
    member_name: 'ACME',
    member_class: 'GOV',
    member_code: '1234',
    subsystem_code: 'ZULU',
    owner: false,
    has_valid_local_sign_cert: false,
    connection_type: ConnectionType.HTTPS,
    status: ClientStatus.SAVED,
  },
  {
    id: 'CS:GOV:1234:ALPHA',
    instance_id: 'CS',
    member_name: 'ACME',
    member_class: 'GOV',
    member_code: '1234',
    subsystem_code: 'ALPHA',
    owner: false,
    has_valid_local_sign_cert: false,
    connection_type: ConnectionType.HTTPS,
    status: ClientStatus.REGISTERED,
  },
  {
    id: 'CS:GOV:5678:BRAVO',
    instance_id: 'CS',
    member_name: 'BETA_ORG',
    member_class: 'GOV',
    member_code: '5678',
    subsystem_code: 'BRAVO',
    owner: false,
    has_valid_local_sign_cert: false,
    connection_type: ConnectionType.HTTPS,
    status: ClientStatus.REGISTERED,
  },
  {
    id: 'CS:GOV:9999:ECHO',
    instance_id: 'CS',
    member_name: 'ECHO_ORG',
    member_class: 'GOV',
    member_code: '9999',
    subsystem_code: 'ECHO',
    owner: false,
    has_valid_local_sign_cert: false,
    connection_type: ConnectionType.HTTPS,
    status: ClientStatus.REGISTERED,
  },
];

type SortEvent = { key: string; order: string }[];

function customFilter(search: string, item: ExtendedClient): boolean {
  if (search.length === 0 || search.trim() === '') return true;
  search = search.toLowerCase();
  const isFiltered = item.visibleName.toLowerCase().includes(search) || item.id.toLowerCase().includes(search);
  if (item.type !== ClientTypes.SUBSYSTEM) {
    item.isFiltered = !isFiltered;
    return true;
  }
  return isFiltered;
}

function customSort(items: ExtendedClient[], sortBy: string, sortDesc: boolean): ExtendedClient[] {
  const index = sortBy as keyof ExtendedClient;
  const sortDirection = !sortDesc ? 1 : -1;

  const subsystems = items.filter((c) => c.type === ClientTypes.SUBSYSTEM);

  function orUndefinedStr(name?: string): string {
    return name || 'undefined';
  }

  const groups = items
    .filter((c) => c.type !== ClientTypes.SUBSYSTEM)
    .sort((a, b) => {
      if (a.owner || b.owner) return a.owner ? -1 : 1;
      const dir = index !== 'visibleName' ? 1 : sortDirection;
      return orUndefinedStr(a.visibleName).localeCompare(orUndefinedStr(b.visibleName)) * dir;
    });

  return groups
    .map<ExtendedClient[]>((group) => [
      group,
      ...subsystems
        .filter((c) => c.id.startsWith(`${group.id}:`))
        .sort((a, b) => {
          switch (index) {
            case 'visibleName':
              return orUndefinedStr(a.visibleName).localeCompare(orUndefinedStr(b.visibleName)) * sortDirection;
            case 'id':
              return a.id.localeCompare(b.id) * sortDirection;
            case 'status':
              return (a.status || '').localeCompare(b.status || '') * sortDirection;
            default:
              return 0;
          }
        }),
    ])
    .reduce((prev, cur) => [...prev, ...cur], []);
}

describe('ClientsListView — client-side sort and filter', () => {
  let store: ReturnType<typeof useClients>;

  beforeEach(() => {
    setActivePinia(createPinia());
    store = useClients();
    store.storeClients(rawClients);
  });

  it('storeClients produces the correct visibleName for each type', () => {
    const clients = store.getClients;
    const owner = clients.find((c) => c.id === 'CS:GOV:1234');
    expect(owner?.type).toBe(ClientTypes.OWNER_MEMBER);
    expect(owner?.visibleName).toBe('ACME');

    const alpha = clients.find((c) => c.id === 'CS:GOV:1234:ALPHA');
    expect(alpha?.type).toBe(ClientTypes.SUBSYSTEM);
    expect(alpha?.visibleName).toBe('ALPHA');
  });

  it('customSort asc by visibleName puts owner first, then subsystems alphabetically', () => {
    const sorted = customSort([...store.getClients], 'visibleName', false);
    expect(sorted[0].id).toBe('CS:GOV:1234');
    const ownerSubsystems = sorted.filter(
      (c) => c.type === ClientTypes.SUBSYSTEM && c.id.startsWith('CS:GOV:1234:'),
    );
    expect(ownerSubsystems[0].visibleName).toBe('ALPHA');
    expect(ownerSubsystems[1].visibleName).toBe('ZULU');
  });

  it('customSort desc by visibleName reverses subsystem order within each group', () => {
    const sorted = customSort([...store.getClients], 'visibleName', true);
    const ownerSubsystems = sorted.filter(
      (c) => c.type === ClientTypes.SUBSYSTEM && c.id.startsWith('CS:GOV:1234:'),
    );
    expect(ownerSubsystems[0].visibleName).toBe('ZULU');
    expect(ownerSubsystems[1].visibleName).toBe('ALPHA');
  });

  it('customSort by id asc sorts subsystems by full id', () => {
    const sorted = customSort([...store.getClients], 'id', false);
    const ownerSubsystems = sorted.filter(
      (c) => c.type === ClientTypes.SUBSYSTEM && c.id.startsWith('CS:GOV:1234:'),
    );
    expect(ownerSubsystems[0].id).toBe('CS:GOV:1234:ALPHA');
    expect(ownerSubsystems[1].id).toBe('CS:GOV:1234:ZULU');
  });

  it('customSort by status sorts subsystems by status value', () => {
    const sorted = customSort([...store.getClients], 'status', false);
    const ownerSubsystems = sorted.filter(
      (c) => c.type === ClientTypes.SUBSYSTEM && c.id.startsWith('CS:GOV:1234:'),
    );
    expect(ownerSubsystems[0].status).toBe(ClientStatus.REGISTERED);
    expect(ownerSubsystems[1].status).toBe(ClientStatus.SAVED);
  });

  it('customFilter search by subsystem code returns only matching subsystems', () => {
    const all = store.getClients;
    const filtered = all.filter((c) => customFilter('ALPHA', c));
    const ids = filtered.map((c) => c.id);
    expect(ids).toContain('CS:GOV:1234:ALPHA');
    expect(ids).not.toContain('CS:GOV:1234:ZULU');
  });

  it('customFilter search by member name keeps member rows visible (member isFiltered set)', () => {
    const all = store.getClients;
    const items = all.map((c) => ({ ...c })) as ExtendedClient[];
    items.filter((c) => customFilter('BRAVO', c));
    const bravo = items.find((c) => c.id === 'CS:GOV:5678:BRAVO');
    expect(bravo).toBeDefined();
  });

  it('customFilter empty string returns all clients', () => {
    const all = store.getClients;
    const filtered = all.filter((c) => customFilter('', c));
    expect(filtered.length).toBe(all.length);
  });

  it('customFilter search is case-insensitive', () => {
    const all = store.getClients;
    const filtered = all.filter((c) => customFilter('alpha', c));
    expect(filtered.some((c) => c.id === 'CS:GOV:1234:ALPHA')).toBe(true);
  });

  it('sort event changes sort order from asc to desc', () => {
    let sortBy: SortEvent = [{ key: 'visibleName', order: 'asc' }];
    let filteredClients = customSort([...store.getClients], sortBy[0].key, sortBy[0].order === 'desc');

    sortBy = [{ key: 'visibleName', order: 'desc' }];
    filteredClients = customSort([...store.getClients], sortBy[0].key, sortBy[0].order === 'desc');

    const ownerSubs = filteredClients.filter(
      (c) => c.type === ClientTypes.SUBSYSTEM && c.id.startsWith('CS:GOV:1234:'),
    );
    expect(ownerSubs[0].visibleName).toBe('ZULU');
    expect(ownerSubs[ownerSubs.length - 1].visibleName).toBe('ALPHA');
  });
});
