import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import { RootState } from '../types';

export interface State {
  memberClass: string | undefined;
  memberCode: string | undefined;
  securityServerCode: string | undefined;
}

export const getDefaultState = (): State => {
  return {
    memberClass: undefined,
    memberCode: undefined,
    securityServerCode: undefined,
  };
};

// Initial state. The state can be reseted with this.
const moduleState = getDefaultState();

export const getters: GetterTree<State, RootState> = {
  initServerMemberClass(state: State): string | undefined {
    return state.memberClass;
  },
  initServerMemberCode(state: State): string | undefined {
    return state.memberCode;
  },
  initServerSSCode(state: State): string | undefined {
    return state.securityServerCode;
  },
};

export const mutations: MutationTree<State> = {
  resetInitServerState(state: State) {
    Object.assign(state, getDefaultState());
  },
  storeInitServerMemberCode(state: State, memberCode: string | undefined) {
    state.memberCode = memberCode;
  },
  storeInitServerMemberClass(state: State, memberClass: string | undefined) {
    state.memberClass = memberClass;
  },
  storeInitServerSSCode(state: State, code: string | undefined) {
    state.securityServerCode = code;
  },
};

export const actions: ActionTree<State, RootState> = {
  resetInitServerState({ commit }) {
    commit('resetInitServerState');
  },
};

export const module: Module<State, RootState> = {
  namespaced: false,
  state: moduleState,
  getters,
  actions,
  mutations,
};
