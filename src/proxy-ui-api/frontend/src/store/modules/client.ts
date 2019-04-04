import axios from 'axios';
import _ from 'lodash';
import FileSaver from 'file-saver';
import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import { RootState } from '../types';

export interface Client {
  id: string;
  name: string;
  type?: string;
  status?: string;
  subsystem?: Client[];
  connectiontype?: string;
}

export interface ClientState {
  client: Client | null;
  certificates: any[];
  loading: boolean;
  connectionType: string | null;
  tlsCertificates: any[];
  ssCertificate: any;
}

export const clientState: ClientState = {
  client: null,
  loading: false,
  certificates: [],
  connectionType: null,
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
      return state.client.connectiontype;
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

    axios.get(`/download`, { responseType: 'blob' }).then((response) => {

      // Log somewhat to show that the browser actually exposes the custom HTTP header
      // const fileNameHeader = "x-suggested-filename";
      const fileNameHeader = 'content-disposition';
      const suggestedFileName = response.headers[fileNameHeader].filename;
      console.log(response.headers[fileNameHeader]);
      const effectiveFileName = (suggestedFileName === undefined ? 'random_name.cert' : suggestedFileName);

      console.log('Received header [' + fileNameHeader + ']: ' + suggestedFileName
        + ', effective fileName: ' + effectiveFileName);

      // Let the user save the file.
      FileSaver.saveAs(response.data, effectiveFileName);

    }).catch((response) => {
      console.error('Could not Download the Excel report from the backend.', response);
    });

  },

  uploadTlsCertificate({ commit, state }, file) {

    return axios.post(`/submit-form`, file)
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
    clone.connectiontype = connType;

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
