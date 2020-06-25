import axios from 'axios';
import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import { RootState } from '../types';
import { saveResponseAsFile } from '@/util/helpers';
import { CertificateDetails, Client, TokenCertificate } from '@/openapi-types';

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
      .get<TokenCertificate[]>(`/clients/${id}/sign-certificates`)
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
      .get<CertificateDetails[]>(`/clients/${id}/tls-certificates`)
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
      .get<CertificateDetails>(`/system/certificate`)
      .then((res) => {
        commit('storeSsCertificate', res.data);
      })
      .catch((error) => {
        throw error;
      });
  },

  // TODO: Check with Mikko why this is in the store, it doesn't operate on state
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  fetchTlsCertificate({ commit, rootGetters }, { clientId, hash }) {
    if (!clientId) {
      throw new Error('Missing id');
    }

    if (!hash) {
      throw new Error('Missing certificate hash');
    }

    return axios.get(`/clients/${clientId}/tls-certificates/${hash}`);
  },

  // TODO: Check with Mikko why this is in the store, it doesn't operate on state
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  deleteTlsCertificate({ commit, state }, { clientId, hash }) {
    return axios.delete(`/clients/${clientId}/tls-certificates/${hash}`);
  },

  // TODO: Check with Mikko why this is in the store, it doesn't operate on state
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  downloadSSCertificate({ commit, state }) {
    axios
      .get(`/system/certificate/export`, { responseType: 'arraybuffer' })
      .then((response) => {
        saveResponseAsFile(response);
      });
  },

  // TODO: Check with Mikko why this is in the store, it doesn't operate on state
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
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
        }
      })
      .catch((error) => {
        throw error;
      });
  },

  // TODO: Check with Mikko why this is in the store, it doesn't operate on state
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  registerClient({ commit, state }, clientId: string) {
    return axios.put(`/clients/${clientId}/register`, {});
  },

  // TODO: Check with Mikko why this is in the store, it doesn't operate on state
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  unregisterClient({ commit, state }, clientId) {
    return axios.put(`/clients/${clientId}/unregister`, {});
  },

  addSubsystem(
    // TODO: Check with Mikko why this is in the store, it doesn't operate on state
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
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

  // TODO: Check with Mikko why this is in the store, it doesn't operate on state
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  deleteClient({ commit, state }, clientId: string) {
    return axios.delete(`/clients/${clientId}`);
  },

  // TODO: Check with Mikko why this is in the store, it doesn't operate on state
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  getOrphans({ commit, state }, clientId: string) {
    return axios.get(`/clients/${clientId}/orphans`);
  },

  // TODO: Check with Mikko why this is in the store, it doesn't operate on state
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
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
