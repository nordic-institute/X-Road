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
import { useSystem } from '@/store/modules/system';
import { NodeType, NodeTypeResponse, VersionInfo } from '@/openapi-types';
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AxiosHeaders, type AxiosResponse } from 'axios';
import * as api from '@/util/api';

vi.mock('@/util/api');

function mockAxiosResponse<T>(data: T): AxiosResponse<T> {
  return {
    data,
    status: 200,
    statusText: 'OK',
    headers: {},
    config: { headers: new AxiosHeaders() },
  };
}

describe('System store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.resetAllMocks();
  });

  it('fetchSecurityServerNodeType calls /system/node-type and stores the node type', async () => {
    const nodeTypeResponse: NodeTypeResponse = { node_type: NodeType.SECONDARY };
    vi.mocked(api.get).mockResolvedValueOnce(mockAxiosResponse(nodeTypeResponse));

    const store = useSystem();
    await store.fetchSecurityServerNodeType();

    expect(api.get).toHaveBeenCalledWith('/system/node-type');
    expect(store.securityServerNodeType).toEqual(NodeType.SECONDARY);
    expect(store.securityServerVersion).toEqual({});
  });

  it('fetchSecurityServerVersion calls /system/version and stores the version info', async () => {
    const versionInfo: VersionInfo = {
      info: 'Security Server',
      java_version: 21,
      min_java_version: 21,
      max_java_version: 25,
      using_supported_java_version: true,
      java_vendor: 'Eclipse Adoptium',
      java_runtime_version: '21.0.1+12',
    };
    vi.mocked(api.get).mockResolvedValueOnce(mockAxiosResponse(versionInfo));

    const store = useSystem();
    await store.fetchSecurityServerVersion();

    expect(api.get).toHaveBeenCalledWith('/system/version');
    expect(store.securityServerVersion).toEqual(versionInfo);
    expect(store.securityServerNodeType).toBeUndefined();
  });
});
