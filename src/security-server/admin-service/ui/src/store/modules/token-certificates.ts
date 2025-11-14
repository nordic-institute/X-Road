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
import { encodePathParameter } from '@/util/api';
import { TokenCertificate, PossibleActions as PossibleActionsList } from '@/openapi-types';
import { buildFileFormData, multipartFormDataConfig } from '@niis/shared-ui';

export const useTokenCertificates = defineStore('token-certificates', {
  state: () => {
    return {};
  },
  getters: {},

  actions: {
    async fetchTokenCertificate(hash: string) {
      const encodedHash = encodePathParameter(hash);
      return api.get<TokenCertificate>(`/token-certificates/${encodedHash}`).then((res) => res.data);
    },
    async fetchTokenCertificatePossibleActions(hash: string) {
      const encodedHash = encodePathParameter(hash);
      return api.get<PossibleActionsList>(`/token-certificates/${encodedHash}/possible-actions`).then((res) => res.data);
    },
    async deleteTokenCertificate(hash: string) {
      const encodedHash = encodePathParameter(hash);
      return api.remove(`/token-certificates/${encodedHash}`);
    },
    async activateTokenCertificate(hash: string) {
      const encodedHash = encodePathParameter(hash);
      return api.put(`/token-certificates/${encodedHash}/activate`, {});
    },
    async deactivateTokenCertificate(hash: string) {
      const encodedHash = encodePathParameter(hash);
      return api.put(`token-certificates/${encodedHash}/disable`, {});
    },
    async unregisterTokenCertificate(hash: string) {
      const encodedHash = encodePathParameter(hash);
      return api.put(`/token-certificates/${encodedHash}/unregister`, {});
    },
    async markForDeletionTokenCertificate(hash: string) {
      const encodedHash = encodePathParameter(hash);
      return api.put(`/token-certificates/${encodedHash}/mark-for-deletion`, {});
    },
    async importTokenCertificate(cert: File) {
      return api
        .post<TokenCertificate>('/token-certificates', buildFileFormData('certificate', cert), multipartFormDataConfig())
        .then((resp) => resp.data);
    },
    async importTokenCertificateByHash(hash: string) {
      const encoded = encodePathParameter(hash);
      api.post<TokenCertificate>(`/token-certificates/${encoded}/import`, {}).then((resp) => resp.data);
    },
  },
});
