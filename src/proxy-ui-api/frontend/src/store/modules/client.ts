import axios from 'axios';
import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import { RootState } from '../types';
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
};

export const clientModule: Module<ClientState, RootState> = {
  namespaced: false,
  state: clientState,
  getters,
  actions,
  mutations,
};
