/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
import axiosAuth from '../../axios-auth';
import axios from 'axios';
import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import { RootState, StoreTypes } from '@/global';
import { Version } from '@/openapi-types/cs-types';

export interface State {
  authenticated: boolean;
  isSessionAlive: boolean | undefined;
  username: string;
  serverVersion: Version | undefined;
}

export const getDefaultState = (): State => {
  return {
    authenticated: false,
    isSessionAlive: undefined,
    username: '',
    serverVersion: undefined,
  };
};

// Initial state. The state can be reseted with this.
const moduleState = getDefaultState();

export const userGetters: GetterTree<State, RootState> = {
  [StoreTypes.getters.IS_AUTHENTICATED](state) {
    return state.authenticated;
  },
  [StoreTypes.getters.IS_SESSION_ALIVE](state) {
    return state.isSessionAlive;
  },
  [StoreTypes.getters.USERNAME](state) {
    return state.username;
  },
  [StoreTypes.getters.HAS_PERMISSION](state, value: string) {
    return true; // Mock. Until there is a real permission system.
  },
};

export const mutations: MutationTree<State> = {
  [StoreTypes.mutations.AUTH_USER](state) {
    state.authenticated = true;
  },
  [StoreTypes.mutations.SET_SESSION_ALIVE]: (state, value: boolean) => {
    state.isSessionAlive = value;
  },
  [StoreTypes.mutations.CLEAR_AUTH_DATA](state) {
    Object.assign(state, getDefaultState());
  },
  [StoreTypes.mutations.SET_USERNAME]: (state, username: string) => {
    state.username = username;
  },
  [StoreTypes.mutations.SET_SERVER_VERSION]: (state, version: Version) => {
    state.serverVersion = version;
  },
};

export const actions: ActionTree<State, RootState> = {
  [StoreTypes.actions.LOGIN]({ commit }, authData) {
    const data = `username=${encodeURIComponent(
      authData.username,
    )}&password=${encodeURIComponent(authData.password)}`;

    return axiosAuth({
      url: '/login',
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      data,
    })
      .then(() => {
        commit(StoreTypes.mutations.AUTH_USER);
        commit(StoreTypes.mutations.SET_SESSION_ALIVE, true);
      })
      .catch((error) => {
        throw error;
      });
  },

  async [StoreTypes.actions.FETCH_SESSION_STATUS]({ commit }) {
    return axios
      .get('/notifications/session-status')
      .then((res) => {
        commit(
          StoreTypes.mutations.SET_SESSION_ALIVE,
          res?.data?.valid ?? false,
        );
      })
      .catch(() => {
        commit(StoreTypes.mutations.SET_SESSION_ALIVE, false);
      });
  },

  async [StoreTypes.actions.FETCH_USER_DATA]() {
    return axios
      .get('/user')
      .then(() => {
        // do something
      })
      .catch((error) => {
        throw error;
      });
  },

  [StoreTypes.actions.LOGOUT]({ commit }, reload = true) {
    // Clear auth data
    commit(StoreTypes.mutations.CLEAR_AUTH_DATA);

    // Call backend for logout
    axiosAuth
      .post('/logout')
      .catch(() => {
        // Nothing to do
      })
      .finally(() => {
        if (reload) {
          // Reload the browser page to clean up the memory
          location.reload();
        }
      });
  },

  async [StoreTypes.actions.FETCH_SERVER_VERSION]({ commit }) {
    return axios
      .get<Version>('/system/version')
      .then((resp) =>
        commit(StoreTypes.mutations.SET_SERVER_VERSION, resp.data),
      )
      .catch((error) => {
        throw error;
      });
  },

  [StoreTypes.actions.CLEAR_AUTH]: ({ commit }) => {
    commit(StoreTypes.mutations.CLEAR_AUTH_DATA);
  },
};

export const module: Module<State, RootState> = {
  namespaced: false,
  state: moduleState,
  getters: userGetters,
  actions,
  mutations,
};
