/*
 * The MIT License
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
import { CertificateDetails, Client, TokenCertificate } from '@/openapi-types';
import { encodePathParameter } from '@/util/api';

export interface ClientState {
  client: Client | null;
  signCertificates: TokenCertificate[];
  connection_type: string | null;
  tlsCertificates: CertificateDetails[];
  ssCertificate: CertificateDetails | null;
  clientLoading: boolean;
}
export const useClientStore = defineStore('clientStore', {
  state: (): ClientState => {
    return {
      client: null,
      signCertificates: [],
      connection_type: null,
      tlsCertificates: [],
      ssCertificate: null,
      clientLoading: false,
    };
  },
  getters: {
    connectionType(state): string | null | undefined {
      if (state.client) {
        return state.client.connection_type;
      }
      return null;
    },
  },

  actions: {
    async fetchClient(id: string) {
      if (!id) {
        throw new Error('Missing client id');
      }

      this.clientLoading = true;
      return axios
        .get(`/clients/${encodePathParameter(id)}`)
        .then((res) => {
          this.client = res.data;
        })
        .catch((error) => {
          throw error;
        })
        .finally(() => (this.clientLoading = false));
    },
    async fetchSignCertificates(id: string) {
      if (!id) {
        throw new Error('Missing id');
      }

      return axios
        .get<TokenCertificate[]>(
          `/clients/${encodePathParameter(id)}/sign-certificates`,
        )
        .then((res) => {
          this.signCertificates = res.data;
        })
        .catch((error) => {
          throw error;
        });
    },

    async fetchSSCertificate(id: string) {
      if (!id) {
        throw new Error('Missing id');
      }

      return axios
        .get<CertificateDetails>('/system/certificate')
        .then((res) => {
          this.ssCertificate = res.data;
        })
        .catch((error) => {
          throw error;
        });
    },

    async fetchTlsCertificates(id: string) {
      if (!id) {
        throw new Error('Missing id');
      }

      return axios
        .get<CertificateDetails[]>(
          `/clients/${encodePathParameter(id)}/tls-certificates`,
        )
        .then((res) => {
          this.tlsCertificates = res.data;
        })
        .catch((error) => {
          throw error;
        });
    },

    async saveConnectionType(params: { clientId: string; connType: string }) {
      return axios
        .patch(`/clients/${encodePathParameter(params.clientId)}`, {
          connection_type: params.connType,
        })
        .then((res) => {
          if (res.data) {
            this.client = res.data;
          }
        })
        .catch((error) => {
          throw error;
        });
    },
  },
});
