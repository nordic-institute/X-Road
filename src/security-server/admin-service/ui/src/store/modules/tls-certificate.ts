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
import { helper } from '@niis/shared-ui';

export const useTlsCertificate = defineStore('tls-certificate', {
  state: () => ({}),
  getters: {},

  actions: {
    async fetchTlsCertificate() {
      return api
        .get<CertificateDetails>('/system/certificate')
        .then((res) => res.data);
    },
    async generateCsr(distinguishedName: string) {
      return api
        .post(
          '/system/certificate/csr',
          { name: distinguishedName },
          { responseType: 'json' },
        )
        .then((res) => {
          helper.saveResponseAsFile(res, 'request.csr');
        });
    },
    async generateKey() {
      return api.post('/system/certificate', {});
    },
    async uploadCertificate(file: File) {
      return file.arrayBuffer().then((buffer) =>
        api.post('/system/certificate/import', buffer, {
          headers: {
            'Content-Type': 'application/octet-stream',
          },
        }),
      );
    },
    async downloadCertificate() {
      return api
        .get('/system/certificate/export', { responseType: 'blob' })
        .then((res) => helper.saveResponseAsFile(res, 'certs.tar.gz'));
    },
    async exportSSCertificate() {
      return api
        .get('/system/certificate/export', { responseType: 'arraybuffer' })
        .then((response) => {
          helper.saveResponseAsFile(response);
        });
    },
  },
});
