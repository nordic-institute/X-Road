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
import { useClient } from '@/store/modules/client';
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AxiosHeaders, type AxiosResponse } from 'axios';
import * as api from '@/util/api';

vi.mock('@/util/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/util/api')>();
  return { ...actual, put: vi.fn(), remove: vi.fn() };
});

function mockAxiosResponse<T>(data: T): AxiosResponse<T> {
  return {
    data,
    status: 200,
    statusText: 'OK',
    headers: {},
    config: { headers: new AxiosHeaders() },
  };
}

const clientId = 'CS:ORG:1111:TestSubsystem';
const encodedClientId = 'CS%3AORG%3A1111%3ATestSubsystem';

describe('Client store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.resetAllMocks();
  });

  it('deleteClient sends DELETE to /clients/{id}', async () => {
    vi.mocked(api.remove).mockResolvedValueOnce(mockAxiosResponse(undefined));

    const store = useClient();
    await store.deleteClient(clientId);

    expect(api.remove).toHaveBeenCalledWith(`/clients/${encodedClientId}`);
  });

  it('registerClient sends PUT to /clients/{id}/register', async () => {
    vi.mocked(api.put).mockResolvedValueOnce(mockAxiosResponse(undefined));

    const store = useClient();
    await store.registerClient(clientId);

    expect(api.put).toHaveBeenCalledWith(`/clients/${encodedClientId}/register`, undefined);
  });

  it('unregisterClient sends PUT to /clients/{id}/unregister', async () => {
    vi.mocked(api.put).mockResolvedValueOnce(mockAxiosResponse(undefined));

    const store = useClient();
    await store.unregisterClient(clientId);

    expect(api.put).toHaveBeenCalledWith(`/clients/${encodedClientId}/unregister`, undefined);
  });
});
