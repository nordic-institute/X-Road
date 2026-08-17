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
import axios from 'axios';
import * as api from '@/util/api';
import { CertificateDetails } from '@/openapi-types';
import { saveResponseAsFile, multipartFormDataConfig } from '@niis/shared-ui';

// A DS TLS certificate that has never been uploaded or enrolled - a normal, expected state
// rather than an error - since manual upload (this slice) and ACME enrollment (a later one)
// are both opt-in.
const NOT_CONFIGURED: CertificateDetails = { hash: '' } as CertificateDetails;

export const useDsTlsCertificate = defineStore('ds-tls-certificate', {
  state: () => ({}),
  getters: {},

  actions: {
    async fetchDsTlsCertificate() {
      return api
        .get<CertificateDetails>('/ds-tls-certificate')
        .then((res) => res.data)
        .catch((error) => {
          if (axios.isAxiosError(error) && error.response?.status === 404) {
            return NOT_CONFIGURED;
          }
          throw error;
        });
    },
    async uploadCertificate(certificate: File, key?: File) {
      const formData = new FormData();
      formData.set('key', key as File, (key as File).name);
      formData.set('certificate', certificate, certificate.name);
      return api.post('/ds-tls-certificate/import', formData, multipartFormDataConfig());
    },
    async downloadCertificate() {
      return api.get('/ds-tls-certificate/export', { responseType: 'blob' }).then((res) => saveResponseAsFile(res, 'ds-tls-certs.tar.gz'));
    },
  },
});
