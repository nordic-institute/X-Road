import axios from 'axios';
import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import { RootState } from '../types';

export interface Client {
  id: string;
  name: string;
  type?: string;
  status?: string;
  subsystem?: Client[];
}

export interface ClientState {
  client: Client | null;
  certificates: any[];
  loading: boolean;
}

export const clientState: ClientState = {
  client: null,
  loading: false,
  certificates: [],
};

export const getters: GetterTree<ClientState, RootState> = {
  client(state): Client | null {
    return state.client;
  },
  certificates(state): any[] {
    return state.certificates;
  },
  loading(state): boolean {
    return state.loading;
  },
};

export const mutations: MutationTree<ClientState> = {
  storeClient(state, client: Client | null) {
    state.client = client;
  },
  storeCertificates(state, certificates: any[]) {
    state.certificates = certificates;
  },
  setLoading(state, loading: boolean) {
    state.loading = loading;
  },
};

export const actions: ActionTree<ClientState, RootState> = {
  fetchClient({ commit, rootGetters }, id: string) {

    commit('setLoading', true);

    return axios.get(`/clients/${id}`)
      .then((res) => {
        console.log(res);
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

    return sleep(500).then(() => {
      const mockCerts = [
        {
          "name": "X-Road Test CA CN",
          "csp": "globalsign",
          "serial": 48,
          "state": "in use",
          "expires": "2099"
        },
        {
          "name": "X-Road Test 2",
          "csp": "globalsign",
          "serial": 50,
          "state": "in use",
          "expires": "2022"
        }
      ];

      // Do something after the sleep!
      commit('storeCertificates', mockCerts);
      commit('setLoading', false);
    });

    /*

    return axios.get(`/clients/${id}/certificates`)
      .then((res) => {
        console.log(res);
        commit('storeCertificates', res.data);
      })
      .catch((error) => {
        console.log(error);
        throw error;
      })
      .finally(() => {
        commit('setLoading', false);
      });

      */
  },

  clearData({ commit, rootGetters }) {
    commit('storeClient', null);
  },
};


function sleep(ms: number = 2000) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export const clientModule: Module<ClientState, RootState> = {
  namespaced: false,
  state: clientState,
  getters,
  actions,
  mutations,
};
