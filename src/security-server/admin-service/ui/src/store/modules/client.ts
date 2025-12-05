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
import { CertificateDetails, Client, SecurityServer, TokenCertificate } from '@/openapi-types';
import { multipartFormDataConfig, buildFileFormData } from '@niis/shared-ui';

export interface ClientState {
  client: Client | undefined;
  signCertificates: TokenCertificate[];
  connection_type: string | null;
  tlsCertificates: CertificateDetails[];
  securityServers: SecurityServer[];
  ssCertificate: CertificateDetails | undefined;
  clientLoading: boolean;
}

function clientBaseUrl(clientId: string, path = '') {
  const encodedId = encodePathParameter(clientId);
  return `/clients/${encodedId}` + path;
}

export const useClient = defineStore('client', {
  state: (): ClientState => {
    return {
      client: undefined,
      signCertificates: [],
      connection_type: null,
      tlsCertificates: [],
      securityServers: [],
      ssCertificate: undefined,
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
      this.client = undefined;
      return api
        .get<Client>(clientBaseUrl(id))
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

      return api
        .get<TokenCertificate[]>(clientBaseUrl(id, '/sign-certificates'))
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

      return api
        .get<CertificateDetails>('/system/certificate')
        .then((res) => {
          this.ssCertificate = res.data;
        })
        .catch((error) => {
          throw error;
        });
    },

    async fetchTlsCertificates(clientId: string) {
      if (!clientId) {
        throw new Error('Missing id');
      }

      return api
        .get<CertificateDetails[]>(clientBaseUrl(clientId, '/tls-certificates'))
        .then((res) => {
          this.tlsCertificates = res.data;
        })
        .catch((error) => {
          throw error;
        });
    },

    async fetchSecurityServers(clientId: string) {
      if (!id) {
        throw new Error('Missing id');
      }

      return api
        .get<SecurityServer[]>(clientBaseUrl(clientId, `/security-servers`))
        .then((res) => {
          this.securityServers = res.data;
        })
        .catch((error) => {
          throw error;
        });
    },

    async fetchTlsCertificate(clientId: string, hash: string) {
      const encodedHash = encodePathParameter(hash);
      return api.get<CertificateDetails>(clientBaseUrl(clientId, `/tls-certificates/${encodedHash}`)).then((response) => response.data);
    },

    async deleteTlsCertificate(clientId: string, hash: string) {
      const encodedHash = encodePathParameter(hash);

      return api.remove(clientBaseUrl(clientId, `/tls-certificates/${encodedHash}`));
    },

    async uploadTlsCertificate(clientId: string, file: File) {
      return api.post(clientBaseUrl(clientId, '/tls-certificates'), buildFileFormData('certificate', file), multipartFormDataConfig());
    },

    async saveConnectionType(clientId: string, connType: string) {
      return api
        .patch<Client>(clientBaseUrl(clientId), {
          connection_type: connType,
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

    async renameClient(clientId: string, newName: string) {
      return api.put(clientBaseUrl(clientId, '/rename'), {
        client_name: newName,
      });
    },

    async registerClient(clientId: string) {
      return api.put(clientBaseUrl(clientId, '/register'), {});
    },
  },
});
