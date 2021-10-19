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
import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import { RootState, StoreTypes } from '@/global';
import {
  CentralServerAddress,
  InitializationStatus,
  InstanceIdentifier,
  TokenInitStatus,
} from '@/openapi-types';
import { get, post } from '@/util/api';

export interface State {
  instanceId: InstanceIdentifier;
  serverAddress: CentralServerAddress;
  tokenInit: TokenInitStatus;
}

export const getDefaultState = (): State => {
  return {
    instanceId: '',
    serverAddress: '',
    tokenInit: TokenInitStatus.UNKNOWN,
  };
};

// Initial state. The state can be reset with this.
const moduleState = getDefaultState();

export const getters: GetterTree<State, RootState> = {
  [StoreTypes.getters.INITIALIZATION_STATUS](state): State {
    return {
      instanceId: state.instanceId,
      serverAddress: state.serverAddress,
      tokenInit: state.tokenInit,
    };
  },
  [StoreTypes.getters.IS_SERVER_INITIALIZED](state): boolean {
    return (
      0 < state?.instanceId.length &&
      0 < state?.serverAddress.length &&
      TokenInitStatus.INITIALIZED == state.tokenInit
    );
  },
};

export const mutations: MutationTree<State> = {
  [StoreTypes.mutations.SET_INITIALIZATION_STATUS](
    state,
    value: InitializationStatus,
  ) {
    state.tokenInit = value.software_token_init_status;
    state.serverAddress = value.central_server_address;
    state.instanceId = value.instance_identifier;
  },
};

export const actions: ActionTree<State, RootState> = {
  async [StoreTypes.actions.INITIALIZATION_REQUEST]({ commit }, formData) {
    return post('/initialization', formData).then(() => {
      return commit(StoreTypes.mutations.SET_CONTINUE_INIT, true);
    });
  },

  async [StoreTypes.actions.INITIALIZATION_STATUS_REQUEST]({ commit }) {
    return get('/initialization/status').then((res) => {
      commit(StoreTypes.mutations.SET_INITIALIZATION_STATUS, res?.data);
    });
  },
};

export const module: Module<State, RootState> = {
  namespaced: false,
  state: moduleState,
  getters,
  actions,
  mutations,
};
