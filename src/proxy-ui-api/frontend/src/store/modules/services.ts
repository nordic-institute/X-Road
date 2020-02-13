import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import { RootState } from '../types';
import {Endpoint, Service, Subject} from '@/types';


export interface ServicesState {
  expandedServiceDescriptions: string[];
  service: Service | {};
  accessRightsSubjects: Subject[];
}

export const servicesState: ServicesState = {
  expandedServiceDescriptions: [],
  service: {
    id: '',
    service_code: '',
    code: '',
    timeout: 0,
    ssl_auth: true,
    url: '',
  },
  accessRightsSubjects: [],
};

export const getters: GetterTree<ServicesState, RootState> = {
  descExpanded: (state) => (id: string) => {
    return state.expandedServiceDescriptions.includes(id);
  },

  accessRightsSubjects: (state: ServicesState) => {
    return state.accessRightsSubjects;
  },

  service: (state: ServicesState) => {
    return state.service;
  },
};


export const mutations: MutationTree<ServicesState> = {

  setHidden(state, id: string) {
    const index = state.expandedServiceDescriptions.findIndex((element: any) => {
      return element === id;
    });

    if (index >= 0) {
      state.expandedServiceDescriptions.splice(index, 1);
    }
  },

  setExpanded(state, id: string) {
    const index = state.expandedServiceDescriptions.findIndex((element: any) => {
      return element === id;
    });

    if (index === -1) {
      state.expandedServiceDescriptions.push(id);
    }
  },

  setService(state, service: Service) {
    if (service.endpoints) {
      service.endpoints = service.endpoints.sort((a: Endpoint, b: Endpoint) => {
        const sortByGenerated = (a.generated === b.generated) ? 0 : a.generated ? -1 : 1;
        const sortByPathSlashCount = a.path.split('/').length - b.path.split('/').length;
        const sortByPathLength = a.path.length - b.path.length;
        return sortByGenerated || sortByPathSlashCount || sortByPathLength;
      });
    }
    state.service = service;
  },

  setAccessRightsSubjects(state, accessRights: Subject[]) {
    state.accessRightsSubjects = accessRights;
  },
};

export const actions: ActionTree<ServicesState, RootState> = {

  expandDesc({ commit, rootGetters }, id: string) {
    commit('setExpanded', id);
  },

  hideDesc({ commit, rootGetters }, id: string) {
    commit('setHidden', id);
  },

  setService({ commit, rootGetters }, service) {
    commit('setService', service);
  },

  setAccessRightsSubjects({ commit, rootGetters }, accessRights) {
    commit('setAccessRightsSubjects', accessRights);
  },
};

export const servicesModule: Module<ServicesState, RootState> = {
  namespaced: false,
  state: servicesState,
  getters,
  actions,
  mutations,
};
