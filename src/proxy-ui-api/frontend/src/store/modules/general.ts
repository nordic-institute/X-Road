import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import * as api from '@/util/api';
import { RootState } from '../types';
import { MemberName } from '@/openapi-types';

export interface State {
  xroadInstances: string[];
  memberClasses: string[];
  memberClassesCurrentInstance: string[];
  memberName: string;
}

export const generalState: State = {
  xroadInstances: [],
  memberClasses: [],
  memberClassesCurrentInstance: [],
  memberName: '',
};

export const getters: GetterTree<State, RootState> = {
  xroadInstances: (state: State) => {
    return state.xroadInstances;
  },
  memberClasses: (state: State) => {
    return state.memberClasses;
  },
  memberClassesCurrentInstance: (state: State) => {
    return state.memberClassesCurrentInstance;
  },

  memberName: (state: State) => {
    return state.memberName;
  },
};

export const mutations: MutationTree<State> = {
  storeInstances(state: State, instances: string[]) {
    state.xroadInstances = instances;
  },
  storeMemberClasses(state: State, memberClasses: string[]) {
    state.memberClasses = memberClasses;
  },
  storeCurrentInstanceMemberClasses(state: State, memberClasses: string[]) {
    state.memberClassesCurrentInstance = memberClasses;
  },
  storeMemberName(state: State, name: string) {
    state.memberName = name;
  },
};

export const actions: ActionTree<State, RootState> = {
  fetchXroadInstances({ commit }) {
    return api
      .get('/xroad-instances')
      .then((res) => {
        commit('storeInstances', res.data);
      })
      .catch((error) => {
        throw error;
      });
  },

  fetchMemberClasses({ commit }) {
    return api
      .get<string[]>('/member-classes')
      .then((res) => {
        commit('storeMemberClasses', res.data);
      })
      .catch((error) => {
        throw error;
      });
  },

  fetchMemberClassesForCurrentInstance({ commit }) {
    return api
      .get<string[]>('/member-classes?current_instance=true')
      .then((res) => {
        commit('storeCurrentInstanceMemberClasses', res.data);
      })
      .catch((error) => {
        throw error;
      });
  },

  fetchMemberName({ commit }, { memberClass, memberCode }) {
    // this is currently an inline schema and is not automatically generated to a typescript type
    return api
      .get<MemberName>(
        `/member-names?member_class=${memberClass}&member_code=${memberCode}`,
      )
      .then((res) => {
        commit('storeMemberName', res.data.member_name);
      })
      .catch((error) => {
        throw error;
      });
  },
};

export const generalModule: Module<State, RootState> = {
  namespaced: false,
  state: generalState,
  getters,
  actions,
  mutations,
};
