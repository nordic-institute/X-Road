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
import axios from 'axios';
import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import { RootState } from '../types';
import {
  CertificateDetails,
  Client,
  TokenCertificate,
} from '@/openapi-types/ss-types';
import { encodePathParameter } from '@/util/api';

export interface ClientState {
  client: Client | null;
  signCertificates: TokenCertificate[];
  connection_type: string | null;
  tlsCertificates: CertificateDetails[];
  ssCertificate: CertificateDetails | null;
}

export const clientState: ClientState = {
  client: null,
  signCertificates: [],
  connection_type: null,
  tlsCertificates: [],
  ssCertificate: null,
};

export const getters: GetterTree<ClientState, RootState> = {
  client(state): Client | null {
    return state.client;
  },
  signCertificates(state): TokenCertificate[] {
    return state.signCertificates;
  },
  connectionType(state): string | null | undefined {
    if (state.client) {
      return state.client.connection_type;
    }
    return null;
  },
  tlsCertificates(state): CertificateDetails[] {
    return state.tlsCertificates;
  },
  ssCertificate(state): CertificateDetails | null {
    return state.ssCertificate;
  },
};

export const mutations: MutationTree<ClientState> = {
  storeClient(state, client: Client | null) {
    state.client = client;
  },
  storeSsCertificate(state, certificate: CertificateDetails) {
    state.ssCertificate = certificate;
  },
  storeTlsCertificates(state, certificates: CertificateDetails[]) {
    state.tlsCertificates = certificates;
  },
  storeSignCertificates(state, certificates: TokenCertificate[]) {
    state.signCertificates = certificates;
  },
  clearAll(state) {
    state.client = null;
    state.ssCertificate = null;
    state.tlsCertificates = [];
    state.signCertificates = [];
  },
};

export const actions: ActionTree<ClientState, RootState> = {
  fetchClient({ commit }, id: string) {
    if (!id) {
      throw new Error('Missing client id');
    }

    return axios
      .get(`/clients/${encodePathParameter(id)}`)
      .then((res) => {
        commit('storeClient', res.data);
      })
      .catch((error) => {
        throw error;
      });
  },
  fetchSignCertificates({ commit }, id: string) {
    if (!id) {
      throw new Error('Missing id');
    }

    return axios
      .get<TokenCertificate[]>(
        `/clients/${encodePathParameter(id)}/sign-certificates`,
      )
      .then((res) => {
        commit('storeSignCertificates', res.data);
      })
      .catch((error) => {
        throw error;
      });
  },

  fetchTlsCertificates({ commit }, id: string) {
    if (!id) {
      throw new Error('Missing id');
    }

    return axios
      .get<CertificateDetails[]>(
        `/clients/${encodePathParameter(id)}/tls-certificates`,
      )
      .then((res) => {
        commit('storeTlsCertificates', res.data);
      })
      .catch((error) => {
        throw error;
      });
  },

  fetchSSCertificate({ commit }, id: string) {
    if (!id) {
      throw new Error('Missing id');
    }

    return axios
      .get<CertificateDetails>('/system/certificate')
      .then((res) => {
        commit('storeSsCertificate', res.data);
      })
      .catch((error) => {
        throw error;
      });
  },

  saveConnectionType({ commit }, { clientId, connType }) {
    return axios
      .patch(`/clients/${encodePathParameter(clientId)}`, {
        connection_type: connType,
      })
      .then((res) => {
        if (res.data) {
          commit('storeClient', res.data);
        }
      })
      .catch((error) => {
        throw error;
      });
  },
};

export const clientModule: Module<ClientState, RootState> = {
  namespaced: false,
  state: clientState,
  getters,
  actions,
  mutations,
};
