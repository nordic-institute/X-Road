import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import { RootState } from '../types';


export interface ServicesState {
  expandedServiceDescriptions: string[];
}

export const servicesState: ServicesState = {
  expandedServiceDescriptions: [],
};

export const getters: GetterTree<ServicesState, RootState> = {
  descExpanded: (state) => (id: string) => {
    return state.expandedServiceDescriptions.includes(id);
  },
};

export const mutations: MutationTree<ServicesState> = {

  setHidden(state, id: string) {
    const index = state.expandedServiceDescriptions.findIndex((element: any) => {
      return element === id;
    });

    if (index >= 0) {
      state.expandedServiceDescriptions.splice(index, 1);
    }
  },

  setExpanded(state, id: string) {
    const index = state.expandedServiceDescriptions.findIndex((element: any) => {
      return element === id;
    });

    if (index === -1) {
      state.expandedServiceDescriptions.push(id);
    }
  },
};

export const actions: ActionTree<ServicesState, RootState> = {

  expandDesc({ commit, rootGetters }, id: string) {
    commit('setExpanded', id);
  },

  hideDesc({ commit, rootGetters }, id: string) {
    commit('setHidden', id);
  },
};

export const servicesModule: Module<ServicesState, RootState> = {
  namespaced: false,
  state: servicesState,
  getters,
  actions,
  mutations,
};
