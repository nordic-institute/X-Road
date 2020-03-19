import axios from 'axios';
import _ from 'lodash';
import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import { RootState } from '../types';
import { saveResponseAsFile } from '@/util/helpers';
import { Client } from '@/types';

export interface ClientState {
  client: Client | null;
  signCertificates: any[];
  loading: boolean;
  connection_type: string | null;
  tlsCertificates: any[];
  ssCertificate: any;
}

export const clientState: ClientState = {
  client: null,
  loading: false,
  signCertificates: [],
  connection_type: null,
  tlsCertificates: [],
  ssCertificate: null,
};

export const getters: GetterTree<ClientState, RootState> = {
  client(state): Client | null {
    return state.client;
  },
  signCertificates(state): any[] {
    return state.signCertificates;
  },
  connectionType(state): string | null | undefined {
    if (state.client) {
      return state.client.connection_type;
    }
    return null;
  },
  tlsCertificates(state): any[] {
    return state.tlsCertificates;
  },
  ssCertificate(state): any {
    return state.ssCertificate;
  },
};

export const mutations: MutationTree<ClientState> = {
  storeClient(state, client: Client | null) {
    state.client = client;
  },
  storeSsCertificate(state, certificate: any) {
    state.ssCertificate = certificate;
  },
  storeTlsCertificates(state, certificates: any[]) {
    state.tlsCertificates = certificates;
  },
  storeSignCertificates(state, certificates: any[]) {
    state.signCertificates = certificates;
  },
  setLoading(state, loading: boolean) {
    state.loading = loading;
  },
  clearAll(state) {
    state.client = null;
    state.ssCertificate = null;
    state.tlsCertificates = [];
    state.signCertificates = [];
  },
};

export const actions: ActionTree<ClientState, RootState> = {
  fetchClient({ commit, rootGetters }, id: string) {

    if (!id) {
      throw new Error('Missing client id');
    }

    commit('setLoading', true);

    return axios.get(`/clients/${id}`)
      .then((res) => {
        commit('storeClient', res.data);
      })
      .catch((error) => {
        throw error;
      })
      .finally(() => {
        commit('setLoading', false);
      });
  },
  fetchSignCertificates({ commit, rootGetters }, id: string) {

    commit('setLoading', true);

    if (!id) {
      throw new Error('Missing id');
    }

    return axios.get(`/clients/${id}/sign-certificates`)
      .then((res) => {
        commit('storeSignCertificates', res.data);
      })
      .catch((error) => {
        throw error;
      })
      .finally(() => {
        commit('setLoading', false);
      });
  },

  fetchTlsCertificates({ commit, rootGetters }, id: string) {

    commit('setLoading', true);

    if (!id) {
      throw new Error('Missing id');
    }

    return axios.get(`/clients/${id}/tls-certificates`)
      .then((res) => {
        commit('storeTlsCertificates', res.data);
      })
      .catch((error) => {
        throw error;
      })
      .finally(() => {
        commit('setLoading', false);
      });
  },

  fetchSSCertificate({ commit, rootGetters }, id: string) {

    if (!id) {
      throw new Error('Missing id');
    }

    return axios.get(`/system/certificate`)
      .then((res) => {
        commit('storeSsCertificate', res.data);
      })
      .catch((error) => {
        throw error;
      })
      .finally(() => {
        commit('setLoading', false);
      });
  },

  fetchTlsCertificate({ commit, rootGetters }, { clientId, hash }) {

    if (!clientId) {
      throw new Error('Missing id');
    }

    if (!hash) {
      throw new Error('Missing certificate hash');
    }

    return axios.get(`/clients/${clientId}/tls-certificates/${hash}`);
  },

  deleteTlsCertificate({ commit, state }, { clientId, hash }) {

    return axios.delete(`/clients/${clientId}/tls-certificates/${hash}`);
  },

  downloadSSCertificate({ commit, state }, { hash }) {

    axios.get(`/system/certificate/export`, { responseType: 'arraybuffer' }).then((response) => {
      saveResponseAsFile(response);
    });
  },

  uploadTlsCertificate({ commit, state }, data) {
    return axios.post(`/clients/${data.clientId}/tls-certificates/`, data.fileData, {
      headers: {
        'Content-Type': 'application/octet-stream',
      },
    });
  },

  saveConnectionType({ commit, state }, { clientId, connType }) {

    return axios.patch(`/clients/${clientId}`, {
      connection_type: connType,
    })
      .then((res) => {

        if (res.data) {
          commit('storeClient', res.data);
        } else {
          console.error('no data');
        }
      })
      .catch((error) => {
        console.error(error);
        throw error;
      })
      .finally(() => {
        commit('setLoading', false);
      });

  },

  registerClient({ commit, state }, { memberName, memberClass, memberCode, subsystemCode }) {
    const clientId = memberName + ':' + memberClass + ':' + memberCode + ':' + subsystemCode;
    return axios.put(`/clients/${clientId}/register`, {});
  },

  unregisterClient({ commit, state }, { memberName, memberClass, memberCode, subsystemCode }) {
    const clientId = memberName + ':' + memberClass + ':' + memberCode + ':' + subsystemCode;
    return axios.put(`/clients/${clientId}/unregister`, {});
  },

  clearData({ commit, rootGetters }) {
    commit('storeClient', null);
  },
};

export const clientModule: Module<ClientState, RootState> = {
  namespaced: false,
  state: clientState,
  getters,
  actions,
  mutations,
};
