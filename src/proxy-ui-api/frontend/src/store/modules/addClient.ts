/**
 * Vuex store for add client/subsystem wizards
 */
import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import { RootState } from '../types';
import { Token, Client } from '@/types';
import * as api from '@/util/api';

export interface AddClientState {
  expandedTokens: string[];
  tokens: Token[];
  tokenId: string | undefined;
  selectableClients: Client[];
  reservedClients: Client[];
  selectedMember: Client;
  memberClass: string;
  memberCode: string;
  subsystemCode: string | undefined;
}

const getDefaultState = () => {
  return {
    expandedTokens: [],
    tokens: [],
    selectableClients: [],
    reservedClients: [],
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

  selectableClients(state: AddClientState): Client[] {
    return state.selectableClients;
  },
  memberClass(state: AddClientState): string {
    return state.memberClass;
  },
  memberCode(state: AddClientState): string {
    return state.memberCode;
  },
  subsystemCode(state: AddClientState): string | undefined {
    return state.subsystemCode;
  },
  selectedMember(state: AddClientState): Client | undefined {
    return state.selectedMember;
  },
  reservedClients(state: AddClientState): Client[] {
    return state.reservedClients;
  },

  addMemberMode(state: AddClientState): string {
    if (state.memberCode === 'all') {
      return 'ALL';
    }

    if (state.memberCode === 'cert') {
      return 'CERT';
    }

    return 'NONE';
  },

  selectedMemberId(state: AddClientState): string | undefined {
    if (!state.selectedMember) {
      return undefined;
    }

    return state.selectedMember.instance_id +
      ':' +
      state.selectedMember.member_class +
      ':' +
      state.selectedMember.member_code;
  },
};

export const mutations: MutationTree<AddClientState> = {
  resetAddClientState(state: AddClientState) {
    Object.assign(state, getDefaultState());
  },
  setMember(state: AddClientState, member: Client) {
    state.selectedMember = member;
    state.memberClass = member.member_class;
    state.memberCode = member.member_code;
    state.subsystemCode = member.subsystem_code;
  },
  setMemberClass(state: AddClientState, val: string) {
    state.memberClass = val;
  },
  setMemberCode(state: AddClientState, val: string) {
    state.memberCode = val;
  },
  setSubsystemCode(state: AddClientState, val: string) {
    state.subsystemCode = val;
  },
  storeMembers(state: AddClientState, clients: Client[]) {
    state.selectableClients = clients;
  },
  storeReservedClients(state: AddClientState, clients: Client[]) {
    state.reservedClients = clients;
  },
};

export const actions: ActionTree<AddClientState, RootState> = {
  resetAddClientState({ commit }) {
    commit('resetAddClientState');
  },

  fetchSelectableClients({ commit, rootGetters }, id: string) {
    // Fetch clients from backend that can be selected
    return api.get('/clients?exclude_local=true&member_missing_sign_cert=true&internal_search=false&show_members=false')
      .then((res) => {
        console.log(res.data.length);
        commit('storeMembers', res.data);
      })
      .catch((error) => {
        throw error;
      });
  },

  fetchReservedClients({ commit, rootGetters }, client: Client) {
    // Fetch clients from backend that match the selected client without subsystem code
    return api.get(`/clients?instance=${client.instance_id}&member_class=${client.member_class}&member_code=${client.member_code}&internal_search=true`)
      .then((res) => {
        commit('storeReservedClients', res.data);
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
