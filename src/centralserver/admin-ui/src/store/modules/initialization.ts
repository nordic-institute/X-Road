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
  not_used: unknown;
}

export const getDefaultState = (): State => {
  return {
    not_used: undefined,
  };
};

// Initial state. The state can be reset with this.
const moduleState = getDefaultState();

export const getters: GetterTree<State, RootState> = {
  [StoreTypes.getters.IS_SERVER_INITIALIZED](state, getters): boolean {
    const initializationStatus: InitializationStatus =
      getters[StoreTypes.getters.SYSTEM_STATUS]?.initialization_status;
    return (
      0 < initializationStatus?.instance_identifier.length &&
      0 < initializationStatus?.central_server_address.length &&
      TokenInitStatus.INITIALIZED ==
        initializationStatus?.software_token_init_status
    );
  },
};

export const actions: ActionTree<State, RootState> = {
  async [StoreTypes.actions.INITIALIZATION_REQUEST]({ commit }, formData) {
    return post('/initialization', formData).then(() => {
      commit(StoreTypes.mutations.SET_CONTINUE_INIT, true);
    });
  },
};

export const module: Module<State, RootState> = {
  namespaced: false,
  state: moduleState,
  getters,
  actions,
};
