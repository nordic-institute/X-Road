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
import { NodeType, NodeTypeResponse, VersionInfo } from '@/openapi-types';
import { RootState } from '@/store/types';
import { get } from '@/util/api';

export interface SystemState {
  securityServerVersion: VersionInfo | Record<string, unknown>;
  securityServerNodeType?: NodeType;
}

export const systemState = (): SystemState => {
  return {
    securityServerVersion: {},
    securityServerNodeType: undefined,
  };
};

export const getters: GetterTree<SystemState, RootState> = {
  securityServerVersion(state) {
    return state.securityServerVersion;
  },
  securityServerNodeType(state) {
    return state.securityServerNodeType;
  },
  isSecondaryNode(state) {
    return state.securityServerNodeType === NodeType.SECONDARY;
  },
};

export const mutations: MutationTree<SystemState> = {
  storeSecurityServerVersion: (state, version: VersionInfo) => {
    state.securityServerVersion = version;
  },
  storeSecurityServerNodeType(
    state: SystemState,
    securityServerNodeType: NodeType,
  ) {
    state.securityServerNodeType = securityServerNodeType;
  },
};

export const actions: ActionTree<SystemState, RootState> = {
  fetchSecurityServerVersion({ commit }) {
    return get<VersionInfo>('/system/version').then((resp) =>
      commit('storeSecurityServerVersion', resp.data),
    );
  },
  fetchSecurityServerNodeType({ commit }) {
    return get<NodeTypeResponse>('/system/node-type').then((res) => {
      commit('storeSecurityServerNodeType', res.data.node_type);
    });
  },
};

export const system: Module<SystemState, RootState> = {
  namespaced: false,
  state: systemState,
  getters,
  actions,
  mutations,
};
