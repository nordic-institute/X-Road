import axios from 'axios';
import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import { RootState } from '../types';

export interface DataState {
  cities: [];
  loading: boolean;
}

export const state: DataState = {
  cities: [],
  loading: false,
};

export const getters: GetterTree<DataState, RootState> = {
  cities(state): [] {
    return state.cities;
  },
  loading(state): boolean {
    return state.loading;
  },
};

export const mutations: MutationTree<DataState> = {
  storeCities(state, cities: []) {
    state.cities = cities;
  },
  setLoading(state, loading: boolean) {
    state.loading = loading;
  },
};

export const actions: ActionTree<DataState, RootState> = {
  fetchData({ commit, rootGetters }) {
    if (!rootGetters.isAuthenticated) {
      //console.log('Not authenticated! Cant call get cities!');
      //return;
    }

    commit('setLoading', true);

    return axios.get('/cities')
      .then((res) => {
        console.log(res);
        const cities = res.data;
        commit('storeCities', cities);
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
    commit('storeCities', []);
  },
};

export const data: Module<DataState, RootState> = {
  namespaced: false,
  state,
  getters,
  actions,
  mutations,
};
