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
import { Endpoint, Service, ServiceClient } from '@/openapi-types';

export interface ServicesState {
  expandedServiceDescriptions: string[];
  service: Service | {};
  serviceClients: ServiceClient[];
}

export const servicesState: ServicesState = {
  expandedServiceDescriptions: [],
  service: {
    id: '',
    service_code: '',
    code: '',
    timeout: 0,
    ssl_auth: undefined,
    url: '',
  },
  serviceClients: [],
};

export const getters: GetterTree<ServicesState, RootState> = {
  descExpanded: (state) => (id: string) => {
    return state.expandedServiceDescriptions.includes(id);
  },

  serviceClients: (state: ServicesState): ServiceClient[] => {
    return state.serviceClients;
  },

  service: (state: ServicesState): Service | {} => {
    return state.service;
  },
};

export const mutations: MutationTree<ServicesState> = {
  setHidden(state, id: string): void {
    const index = state.expandedServiceDescriptions.findIndex((element) => {
      return element === id;
    });

    if (index >= 0) {
      state.expandedServiceDescriptions.splice(index, 1);
    }
  },

  setExpanded(state, id: string): void {
    const index = state.expandedServiceDescriptions.findIndex((element) => {
      return element === id;
    });

    if (index === -1) {
      state.expandedServiceDescriptions.push(id);
    }
  },

  setService(state, service: Service) {
    service.endpoints = service.endpoints?.sort((a: Endpoint, b: Endpoint) => {
      const sortByGenerated =
        a.generated === b.generated ? 0 : a.generated ? -1 : 1;
      const sortByPathSlashCount =
        a.path.split('/').length - b.path.split('/').length;
      const sortByPathLength = a.path.length - b.path.length;
      return sortByGenerated || sortByPathSlashCount || sortByPathLength;
    });
    state.service = service;
  },

  setServiceClients(state, serviceClients: ServiceClient[]): void {
    state.serviceClients = serviceClients;
  },
};

export const actions: ActionTree<ServicesState, RootState> = {
  expandDesc({ commit }, id: string) {
    commit('setExpanded', id);
  },

  hideDesc({ commit }, id: string) {
    commit('setHidden', id);
  },

  setService({ commit }, service) {
    commit('setService', service);
  },

  setServiceClients({ commit }, serviceClients) {
    commit('setServiceClients', serviceClients);
  },
};

export const servicesModule: Module<ServicesState, RootState> = {
  namespaced: false,
  state: servicesState,
  getters,
  actions,
  mutations,
};
