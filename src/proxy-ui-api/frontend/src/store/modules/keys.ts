import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import { RootState } from '../types';
import { Key, Token, TokenType, TokenCertificate } from '@/types';
import axios from 'axios';
import * as api from '@/util/api';


export interface KeysState {
  expandedTokens: string[];
  tokens: Token[];
  selectedToken: Token | undefined;
}

export const tokensState: KeysState = {
  expandedTokens: [],
  tokens: [],
  selectedToken: undefined,
};

export const getters: GetterTree<KeysState, RootState> = {
  tokenExpanded: (state) => (id: string) => {
    return state.expandedTokens.includes(id);
  },
  tokens(state): Token[] {
    return state.tokens;
  },
  selectedToken(state): Token | undefined {
    return state.selectedToken;
  }
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
  },

  setTokens(state, tokens: Token[]) {
    state.tokens = tokens;
  },

  setSelectedToken(state, token: Token) {
    state.selectedToken = token;
  },

};

export const actions: ActionTree<KeysState, RootState> = {
  expandToken({ commit, rootGetters }, id: string) {
    commit('setTokenExpanded', id);
  },
  hideToken({ commit, rootGetters }, id: string) {
    commit('setTokenHidden', id);
  },
  uploadCertificate({ commit, state }, data) {
    return axios.post(`/token-certificates`, data.fileData, {
      headers: {
        'Content-Type': 'application/octet-stream',
      },
    });
  },
  fetchTokens({ commit, rootGetters }, id: string) {
    // Fetch tokens from backend
    return api
      .get(`/tokens`)
      .then((res) => {
        commit('setTokens', res.data);
      })
      .catch((error) => {
        throw error;
      });
  },

  tokenLogout({ commit, dispatch, rootGetters }, id: string) {
    return api
      .put(`/tokens/${id}/logout`, {})
      .then((res) => {
        // Update tokens
        this.dispatch('fetchTokens');
      })
      .catch((error) => {
        throw error;
      });
  },

  setSelectedToken({ commit, dispatch, rootGetters }, token: Token) {
    commit('setSelectedToken', token);
  },
};

export const keysModule: Module<KeysState, RootState> = {
  namespaced: false,
  state: tokensState,
  getters,
  actions,
  mutations,
};
