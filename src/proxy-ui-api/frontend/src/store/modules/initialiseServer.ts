
import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import { RootState } from '../types';
import { saveResponseAsFile } from '@/util/helpers';
import { Key, CertificateAuthority, CsrSubjectFieldDescription } from '@/types';
import * as api from '@/util/api';
import { UsageTypes, CsrFormatTypes } from '@/global';


export interface InitServerState {
  memberClass: string | undefined;
  memberCode: string | undefined;
  memberName: string | undefined;
  securityServerCode: string | undefined;
}

const getDefaultState = () => {
  return {
    memberClass: undefined,
    memberCode: undefined,
    memberName: 'placeholder name',
    securityServerCode: undefined,
  };
};

// Initial state. The state can be reseted with this.
const moduleState = getDefaultState();


export const getters: GetterTree<InitServerState, RootState> = {

  initServerMemberClass(state): string | undefined {
    return state.memberClass;
  },

  initServerMemberCode(state): string | undefined {
    return state.memberCode;
  },

  initServerMemberName(state): string | undefined {
    return state.memberName;
  },

  initServerSSCode(state): string | undefined {
    return state.securityServerCode;
  },


};

export const mutations: MutationTree<InitServerState> = {
  resetInitServerState(state) {
    Object.assign(state, getDefaultState());
  },
  storeInitServerMemberCode(state, memberCode: string | undefined) {
    state.memberCode = memberCode;
  },
  storeInitServerMemberName(state, memberName: string | undefined) {
    state.memberName = memberName;
  },

  storeInitServerMemberClass(state, memberClass: string | undefined) {
    state.memberClass = memberClass;
  },

  storeInitServerSSCode(state, code: string | undefined) {
    state.securityServerCode = code;
  },
};

export const actions: ActionTree<InitServerState, RootState> = {
  resetInitServerState({ commit }) {
    commit('resetInitServerState');
  },
  setCsrTokenId({ commit, dispatch, rootGetters }, tokenId: string) {
    commit('storeCsrTokenId', tokenId);
  },
  fetchInitServerMemberName({ commit, rootGetters }) {
    api
      .get(`/member-classes`)
      .then((res: any) => {
        commit('storeInitServerMemberName', res.data[0]);
      })
      .catch((error: any) => {
        throw error;
      });
  },
};

export const module: Module<InitServerState, RootState> = {
  namespaced: false,
  state: moduleState,
  getters,
  actions,
  mutations,
};
