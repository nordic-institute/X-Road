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

import { defineStore } from 'pinia';
import * as api from '@/util/api';
import { CertificateDetails } from '@/openapi-types';
import { buildFileFormData, multipartFormDataConfig, saveResponseAsFile } from '@niis/shared-ui';

export const useOpMonitorTrust = defineStore('op-monitor-trust', {
  state: () => ({}),
  getters: {},

  actions: {
    async fetchInternalRpcCa() {
      return api.get<CertificateDetails>('/system/internal-rpc-ca').then((res) => res.data);
    },
    async downloadInternalRpcCa() {
      return api.get('/system/internal-rpc-ca/download', { responseType: 'blob' }).then((res) => saveResponseAsFile(res));
    },
    async fetchOpMonitorCa(): Promise<CertificateDetails | undefined> {
      return api.get<CertificateDetails>('/system/op-monitor-ca').then((res) => (res.status === 204 ? undefined : res.data));
    },
    async uploadOpMonitorCa(certificate: File) {
      return api
        .post<CertificateDetails>('/system/op-monitor-ca', buildFileFormData('certificate', certificate), multipartFormDataConfig())
        .then((res) => res.data);
    },
    async deleteOpMonitorCa() {
      return api.remove('/system/op-monitor-ca');
    },
  },
});
