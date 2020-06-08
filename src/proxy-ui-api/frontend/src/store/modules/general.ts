import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import * as api from '@/util/api';
import { RootState } from '../types';

export interface State {
  xroadInstances: string[];
  memberClasses: string[];
}

export const generalState: State = {
  xroadInstances: [],
  memberClasses: [],
};

export const getters: GetterTree<State, RootState> = {
  xroadInstances: (state) => {
    return state.xroadInstances;
  },
  memberClasses: (state) => {
    return state.memberClasses;
  },
};

export const mutations: MutationTree<State> = {
  storeInstances(state, instances: string[]) {
    state.xroadInstances = instances;
  },
  storeMemberClasses(state, memberClasses: string[]) {
    state.memberClasses = memberClasses;
  },
};

export const actions: ActionTree<State, RootState> = {
  fetchXroadInstances({ commit, rootGetters }) {
    return api
      .get(`/xroad-instances`)
      .then((res) => {
        commit('storeInstances', res.data);
      })
      .catch((error) => {
        throw error;
      });
  },

  fetchMemberClasses({ commit, rootGetters }) {
    return api
      .get(`/member-classes`)
      .then((res) => {
        commit('storeMemberClasses', res.data);
      })
      .catch((error) => {
        throw error;
      });
  },
};

export const generalModule: Module<State, RootState> = {
  namespaced: false,
  state: generalState,
  getters,
  actions,
  mutations,
};
