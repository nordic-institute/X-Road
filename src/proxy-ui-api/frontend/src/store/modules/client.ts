import axios from 'axios';
import _ from 'lodash';
import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import { RootState } from '../types';

export interface Client {
  id: string;
  name: string;
  type?: string;
  status?: string;
  subsystem?: Client[];
  connection_type?: string;
}

export interface ClientState {
  client: Client | null;
  certificates: any[];
  loading: boolean;
  connection_type: string | null;
  tlsCertificates: any[];
  ssCertificate: any;
}

export const clientState: ClientState = {
  client: null,
  loading: false,
  certificates: [],
  connection_type: null,
  tlsCertificates: [],
  ssCertificate: null,
};

export const getters: GetterTree<ClientState, RootState> = {
  client(state): Client | null {
    return state.client;
  },
  certificates(state): any[] {
    return state.certificates;
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
  /*
  loading(state): boolean {
    return state.loading;
  }, */
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
  storeCertificates(state, certificates: any[]) {
    state.certificates = certificates;
  },
  setLoading(state, loading: boolean) {
    state.loading = loading;
  },
  clearAll(state) {
    state.client = null;
    state.ssCertificate = null;
    state.tlsCertificates = [];
    state.certificates = [];
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
        console.log(error);
        throw error;
      })
      .finally(() => {
        commit('setLoading', false);
      });
  },
  fetchCertificates({ commit, rootGetters }, id: string) {

    commit('setLoading', true);

    if (!id) {
      throw new Error('Missing id');
    }

    return axios.get(`/clients/${id}/certificates`)
      .then((res) => {
        commit('storeCertificates', res.data);
      })
      .catch((error) => {
        console.log(error);
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

    return axios.get(`/clients/${id}/tlscertificates`)
      .then((res) => {
        console.log(res);
        commit('storeTlsCertificates', res.data);
      })
      .catch((error) => {
        console.log(error);
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
        console.log(error);
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

    return axios.get(`/clients/${clientId}/tlscertificates/${hash}`);
  },

  deleteTlsCertificate({ commit, state }, { clientId, hash }) {

    return axios.delete(`/clients/${clientId}/tlscertificates/${hash}`)
      .then((res) => {
        console.log(res);
      })
      .catch((error) => {
        console.error(error);
        throw error;
      });
  },

  downloadSSCertificate({ commit, state }, { hash }) {

    axios.get(`/system/certificate/export`, { responseType: 'arraybuffer' }).then((response) => {
      let suggestedFileName = undefined;
      const disposition = response.headers['content-disposition'];

      if (disposition && disposition.indexOf('attachment') !== -1) {
        const filenameRegex = /filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/;
        const matches = filenameRegex.exec(disposition);
        if (matches != null && matches[1]) {
          suggestedFileName = matches[1].replace(/['"]/g, '');
        }
      }

      const effectiveFileName = (suggestedFileName === undefined ? 'ceoorts.tar.gz' : suggestedFileName);
      const blob = new Blob([response.data]);

      // Create a link to DOM and click it. This will trigger the browser to start file download.
      const link = document.createElement('a');
      link.href = window.URL.createObjectURL(blob);
      link.setAttribute('download', effectiveFileName);
      document.body.appendChild(link);
      link.click();

    }).catch((response) => {
      console.error('Could not download the certificate', response);
    });
  },

  uploadTlsCertificate({ commit, state }, { clientId, file }) {

    return axios.post(`/clients/${clientId}/tlscertificates/`, file)
      .then((res) => {
        console.log(res.data);
      })
      .catch((error) => {
        console.error(error);
        throw error;
      });
  },


  saveConnectionType({ commit, state }, connType: string) {

    // Bail if there is no client for some reason
    if (!state.client) {
      throw new Error('Client does not exist');
    }

    const id = state.client.id;
    const clone = _.cloneDeep(state.client);
    clone.connection_type = connType;

    return axios.put(`/clients/${id}`, clone)
      .then((res) => {
        console.log(res);

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
