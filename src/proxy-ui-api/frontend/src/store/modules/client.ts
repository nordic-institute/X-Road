import axios from 'axios';
import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import { RootState } from '../types';
import { saveResponseAsFile } from '@/util/helpers';
import { Client } from '@/openapi-types';

export interface ClientState {
  client: Client | null;
  signCertificates: any[];
  connection_type: string | null;
  tlsCertificates: any[];
  ssCertificate: any;
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

    return axios
      .get(`/clients/${id}`)
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
      .get(`/clients/${id}/sign-certificates`)
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
      .get(`/clients/${id}/tls-certificates`)
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
      .get(`/system/certificate`)
      .then((res) => {
        commit('storeSsCertificate', res.data);
      })
      .catch((error) => {
        throw error;
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
    axios
      .get(`/system/certificate/export`, { responseType: 'arraybuffer' })
      .then((response) => {
        saveResponseAsFile(response);
      });
  },

  uploadTlsCertificate({ commit, state }, data) {
    return axios.post(
      `/clients/${data.clientId}/tls-certificates/`,
      data.fileData,
      {
        headers: {
          'Content-Type': 'application/octet-stream',
        },
      },
    );
  },

  saveConnectionType({ commit }, { clientId, connType }) {
    return axios
      .patch(`/clients/${clientId}`, {
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
        throw error;
      });
  },

  registerClient({ commit, state }, clientId: string) {
    return axios.put(`/clients/${clientId}/register`, {});
  },

  unregisterClient({ commit, state }, clientId) {
    return axios.put(`/clients/${clientId}/unregister`, {});
  },

  addSubsystem(
    { commit, state },
    { memberName, memberClass, memberCode, subsystemCode },
  ) {
    const body = {
      client: {
        member_name: memberName,
        member_class: memberClass,
        member_code: memberCode,
        subsystem_code: subsystemCode,
      },
      ignore_warnings: false,
    };

    return axios.post('/clients', body);
  },

  deleteClient({ commit, state }, clientId: string) {
    return axios.delete(`/clients/${clientId}`);
  },

  getOrphans({ commit, state }, clientId: string) {
    return axios.get(`/clients/${clientId}/orphans`);
  },

  deleteOrphans({ commit, state }, clientId: string) {
    return axios.delete(`/clients/${clientId}/orphans`);
  },
};

export const clientModule: Module<ClientState, RootState> = {
  namespaced: false,
  state: clientState,
  getters,
  actions,
  mutations,
};
