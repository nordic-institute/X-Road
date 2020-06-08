import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import { RootState } from '../types';
import { Key, Token, TokenCertificate } from '@/openapi-types';
import axios from 'axios';
import * as api from '@/util/api';

export interface TokensState {
  expandedTokens: string[];
  tokens: Token[];
  selectedToken: Token | undefined;
}

export const tokensState: TokensState = {
  expandedTokens: [],
  tokens: [],
  selectedToken: undefined,
};

export const tokensGetters: GetterTree<TokensState, RootState> = {
  tokenExpanded: (state) => (id: string) => {
    return state.expandedTokens.includes(id);
  },
  tokens(state): Token[] {
    return state.tokens;
  },
  sortedTokens(state): Token[] {
    if (!state.tokens || state.tokens.length === 0) {
      return [];
    }

    // Sort array by id:s so it doesn't jump around. Order of items in the backend reply changes between requests.
    const arr = JSON.parse(JSON.stringify(state.tokens)).sort((a: Token, b: Token) => {
      if (a.id < b.id) {
        return -1;
      }
      if (a.id > b.id) {
        return 1;
      }

      // equal id:s. (should not happen)
      return 0;
    });

    return arr;
  },
  selectedToken(state): Token | undefined {
    return state.selectedToken;
  },
  filteredTokens: (state, getters) => (search: string) => {
    // Filter term is applied to token namem key name and certificate owner id
    let arr = JSON.parse(JSON.stringify(getters.sortedTokens));

    if (!search) {
      return arr;
    }

    const mysearch = search.toLowerCase();

    if (mysearch.length < 1) {
      return state.tokens;
    }

    arr.forEach((token: Token) => {
      token.keys.forEach((key: Key) => {
        const certs = key.certificates.filter((cert: TokenCertificate) => {
          if (cert.owner_id) {
            return cert.owner_id.toLowerCase().includes(mysearch);
          }
          return false;
        });
        key.certificates = certs;
      });
    });

    arr.forEach((token: Token) => {
      const keys = token.keys.filter((key: Key) => {
        if (key?.certificates?.length > 0) {
          return true;
        }

        if (key.name) {
          return key.name.toLowerCase().includes(mysearch);
        }
        return false;
      });
      token.keys = keys;
    });

    arr = arr.filter((token: Token) => {
      if (token?.keys?.length > 0) {
        return true;
      }

      return token.name.toLowerCase().includes(mysearch);
    });

    return arr;
  },

  tokensFilteredByName: (state, getters) => (search: string) => {
    // Filter term is applied to token name
    const arr = getters.sortedTokens;

    if (!search || search.length < 1) {
      return arr;
    }

    const mysearch = search.toLowerCase();

    return arr.filter((token: Token) => {
      return token.name.toLowerCase().includes(mysearch);
    });
  },
};

export const mutations: MutationTree<TokensState> = {
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

export const actions: ActionTree<TokensState, RootState> = {
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
        this.dispatch('checkAlertStatus');
      })
      .catch((error) => {
        throw error;
      });
  },

  setSelectedToken({ commit, dispatch, rootGetters }, token: Token) {
    commit('setSelectedToken', token);
  },
};

export const tokensModule: Module<TokensState, RootState> = {
  namespaced: false,
  state: tokensState,
  getters: tokensGetters,
  actions,
  mutations,
};
