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
