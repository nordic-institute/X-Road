import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import { RootState } from '../types';


export interface KeysState {
  expandedTokens: string[];
}

export const tokensState: KeysState = {
  expandedTokens: []
};

export const getters: GetterTree<KeysState, RootState> = {
  tokenExpanded: (state) => (id: string) => {
    return state.expandedTokens.includes(id);
  },
};

export const mutations: MutationTree<KeysState> = {

  setTokenHidden(state, id: string) {
    const index = state.expandedTokens.findIndex((element: any) => {
      return element === id;
    });

    if (index >= 0) {
      state.expandedTokens.splice(index, 1);
    }
  },

  setTokenExpanded(state, id: string) {
    const index = state.expandedTokens.findIndex((element: any) => {
      return element === id;
    });

    if (index === -1) {
      state.expandedTokens.push(id);
    }
  }
};

export const actions: ActionTree<KeysState, RootState> = {

  expandToken({ commit, rootGetters }, id: string) {
    commit('setTokenExpanded', id);
  },

  hideToken({ commit, rootGetters }, id: string) {
    commit('setTokenHidden', id);
  },
};

export const keysModule: Module<KeysState, RootState> = {
  namespaced: false,
  state: tokensState,
  getters,
  actions,
  mutations,
};
