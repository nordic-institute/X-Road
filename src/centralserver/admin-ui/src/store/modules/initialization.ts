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
import { post } from '@/util/api';
import axios, { AxiosResponse } from 'axios';
import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import { RootState, StoreTypes } from '@/global';
import {
  InitializationStatus,
  InitialServerConf,
  TokenInitStatus,
} from '@/openapi-types';

export interface State {
  serverAddress: string;
  instanceIdentifier: string;
  isServerInitialized: boolean | undefined;
}

export const getDefaultState = (): State => {
  return {
    serverAddress: '',
    instanceIdentifier: '',
    isServerInitialized: false,
  };
};

// Initial state. The state can be reset with this.
const moduleState = getDefaultState();

export const initializationGetters: GetterTree<State, RootState> = {
  [StoreTypes.getters.SERVER_ADDRESS](state) {
    return state.serverAddress;
  },
  [StoreTypes.getters.INSTANCE_IDENTIFIER](state) {
    return state.instanceIdentifier;
  },
  [StoreTypes.getters.IS_SERVER_INITIALIZED](state) {
    return state.isServerInitialized;
  },
};

export const mutations: MutationTree<State> = {
  [StoreTypes.mutations.SET_SERVER_ADDRESS](state, value: string) {
    state.serverAddress = value;
  },
  [StoreTypes.mutations.SET_INSTANCE_IDENTIFIER]: (state, value: string) => {
    state.instanceIdentifier = value;
  },
  [StoreTypes.mutations.SET_SERVER_INITIALIZED]: (state, value: boolean) => {
    state.isServerInitialized = value;
  },
};

export const actions: ActionTree<State, RootState> = {
  [StoreTypes.actions.INITIALIZATION_REQUEST](
    { commit },
    formData: InitialServerConf,
  ) {
    return post<void>('/initialization', formData)
      .then(() => {
        commit(StoreTypes.mutations.SET_SERVER_INITIALIZED, true);
      })
      .catch((error) => {
        commit(StoreTypes.mutations.SET_ERROR_OBJECT, error);
        // how to handle "already initialized" ?
        throw error;
      });
  },

  async [StoreTypes.actions.INITIALIZATION_STATUS_REQUEST]({ commit }) {
    return axios
      .get<InitializationStatus>('/initialization/status')
      .then((res: AxiosResponse<InitializationStatus>) => {
        commit(
          StoreTypes.mutations.SET_SERVER_INITIALIZED,
          isServerInitialized(res.data),
        );
        commit(
          StoreTypes.mutations.SET_SERVER_ADDRESS,
          res?.data?.central_server_address,
        );
        commit(
          StoreTypes.mutations.SET_INSTANCE_IDENTIFIER,
          res?.data?.instance_identifier,
        );
      })
      .catch((error) => {
        throw error;
      });
  },
};

function isServerInitialized(status: InitializationStatus): boolean {
  const isAddressInitialized = !!(
    status?.central_server_address && status.central_server_address.length > 0
  );
  const isInstanceInitialized = !!(
    status?.instance_identifier && status.instance_identifier.length > 0
  );
  const isTokenInitialized: boolean =
    TokenInitStatus.INITIALIZED == status?.software_token_init_status;
  return (
    <boolean>isAddressInitialized && isInstanceInitialized && isTokenInitialized
  );
}

export const module: Module<State, RootState> = {
  namespaced: false,
  state: moduleState,
  getters: initializationGetters,
  actions,
  mutations,
};
