import axios from 'axios';
import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import { RootState } from '../types';


export interface Client {
  id: string;
  name: string;
  type?: string;
  status?: string;
  subsystems?: Client[];
}

export interface ClientsArray extends Array<Client> { }

export interface DataState {
  clients: ClientsArray;
  loading: boolean;
}

export const clientsState: DataState = {
  clients: [],
  loading: false,
};

export const getters: GetterTree<DataState, RootState> = {
  clients(state): ClientsArray {
    return state.clients;
  },
  loading(state): boolean {
    return state.loading;
  },
};

export const mutations: MutationTree<DataState> = {
  storeClients(state, clients: []) {
    state.clients = clients;
  },
  setLoading(state, loading: boolean) {
    state.loading = loading;
  },
};

export const actions: ActionTree<DataState, RootState> = {
  fetchClients({ commit, rootGetters }) {

    commit('setLoading', true);

    return axios.get('/clients')
      .then((res) => {
        console.log(res);
        commit('storeClients', res.data);
      })
      .catch((error) => {
        console.log(error);
        throw error;
      })
      .finally(() => {
        commit('setLoading', false);
      });
  },
  clearData({ commit, rootGetters }) {
    commit('storeClients', []);
  },
};

export const clientsModule: Module<DataState, RootState> = {
  namespaced: false,
  state: clientsState,
  getters,
  actions,
  mutations,
};
