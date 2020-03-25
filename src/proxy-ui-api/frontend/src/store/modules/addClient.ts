import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import { RootState } from '../types';
import { Key, Token, TokenType, Client } from '@/types';
import axios from 'axios';
import * as api from '@/util/api';


export interface AddClientState {
  expandedTokens: string[];
  tokens: Token[];
  tokenId: string | undefined;
  members: Client[];
  selectedMember: Client;
  memberClass: string;
  memberCode: string;
  subsystemCode: string | undefined;
}

const getDefaultState = () => {
  return {
    expandedTokens: [],
    tokens: [],
    members: [],
    selectedMember: { member_class: '', member_code: '', subsystem_code: '' },
    memberClass: '',
    memberCode: '',
    subsystemCode: undefined,
    tokenId: undefined,
  };
};

// Initial state. The state can be reseted with this.
const tokensState: AddClientState = getDefaultState();


export const getters: GetterTree<AddClientState, RootState> = {

  members(state): Client[] {
    return state.members;
  },
  memberClass(state): string {
    return state.memberClass;
  },
  memberCode(state): string {
    return state.memberCode;
  },
  subsystemCode(state): string | undefined {
    return state.subsystemCode;
  },
  selectedMember(state): Client | undefined {
    return state.selectedMember;
  },
};

export const mutations: MutationTree<AddClientState> = {
  resetAddClientState(state) {
    Object.assign(state, getDefaultState());
  },
  setMember(state, member: Client) {
    state.selectedMember = member;
    state.memberClass = member.member_class;
    state.memberCode = member.member_code;
    state.subsystemCode = member.subsystem_code;
  },
  setMemberClass(state, val: string) {
    state.memberClass = val;
  },
  setMemberCode(state, val: string) {
    state.memberCode = val;
  },
  setSubsystemCode(state, val: string) {
    state.subsystemCode = val;
  },
  storeMembers(state, members: Client[]) {
    state.members = members;
  },
};

export const actions: ActionTree<AddClientState, RootState> = {
  resetAddClientState({ commit }) {
    commit('resetAddClientState');
  },

  fetchMembers({ commit, rootGetters }, id: string) {
    // Fetch members from backend
    return api.get('/clients?show_members=true&internal_search=false')
      .then((res) => {
        commit('storeMembers', res.data);
      })
      .catch((error) => {
        throw error;
      });
  },

  setSelectedMember({ commit, rootGetters }, member: Client) {
    commit('setMember', member);
  },

  createClient({ commit, state }) {

    const body = {
      client: {
        member_class: state.memberClass,
        member_code: state.memberCode,
        subsystem_code: state.subsystemCode,
      },
      ignore_warnings: false,
    };

    return api.post('/clients', body)
      .catch((error) => {
        throw error;
      });
  },

};

export const addClientModule: Module<AddClientState, RootState> = {
  namespaced: false,
  state: tokensState,
  getters,
  actions,
  mutations,
};
