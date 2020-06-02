import axios from 'axios';
import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import { RootState } from '../types';
import { Client } from '@/openapi-types';
import { createClientId } from '@/util/helpers';
import { ExtendedClient } from '@/ui-types';
import { ClientTypes } from '@/global';
import i18n from './../../i18n';

export interface ClientsState {
  clients: Client[];
  formattedClients: ExtendedClient[];
  clientsLoading: boolean;
  localMembers: Client[];
  ownerMember: Client | undefined;
  members: ExtendedClient[]; // all local members, virtual and real
  realMembers: ExtendedClient[]; // local actual real members, owner +1
  virtualMembers: ExtendedClient[]; // local "virtual" members, generated from subsystem data
  subsystems: ExtendedClient[];
}

export const clientsState: ClientsState = {
  clients: [],
  formattedClients: [],
  clientsLoading: false,
  localMembers: [],
  ownerMember: undefined,
  members: [],
  subsystems: [],
  realMembers: [],
  virtualMembers: [],
};

function createSortName(client: Client, sortName: string): any {
  // Create a sort id for client in form  "ACMEGOV:1234 MANAGEMENT"
  return sortName + client.member_class + client.member_code + ' ' + client.subsystem_code;
}

function createMemberAscSortName(client: Client, sortName: string | undefined): any {
  // Create a sort id for member in form  "ACMEGOV:1234"
  return sortName + client.member_class + client.member_code;
}

function createMemberDescSortName(client: Client, sortName: string | undefined): any {
  // Create a sort id for member in form  "ACMEGOV:1234!"
  return sortName + client.member_class + client.member_code + '!';
}

export const getters: GetterTree<ClientsState, RootState> = {
  clients(state): ExtendedClient[] {
    return state.formattedClients;
  },

  realMembers(state): ExtendedClient[] {
    return state.realMembers;
  },

  localMembers(state): Client[] {
    return state.localMembers;
  },

  clientsLoading(state): boolean {
    return state.clientsLoading;
  },

  ownerMember(state): Client | undefined {
    return state.ownerMember;
  },

};

export const mutations: MutationTree<ClientsState> = {
  storeClients(state, clients: Client[]) {
    state.clients = clients;

    // New arrays to separate members and subsystems
    const realMembers: ExtendedClient[] = [];
    const members: ExtendedClient[] = [];
    const virtualMembers: ExtendedClient[] = [];
    const subsystems: ExtendedClient[] = [];
    const UNKNOWN_NAME: string = i18n.t('client.unknownMember') as string;

    // Find members. Owner member (there is only one) and possible other member
    state.clients.forEach((element: Client) => {

      if (!element.subsystem_code) {
        const clone = {...element} as ExtendedClient;
        clone.type = ClientTypes.OWNER_MEMBER;
        clone.subsystem_code = undefined;
        clone.visibleName = clone.member_name;
        clone.sortNameAsc = createMemberAscSortName(clone, clone.member_name); // clone.member_name + clone.member_class + clone.member_code;
        clone.sortNameDesc = createMemberDescSortName(clone, clone.member_name); // clone.member_name + clone.member_class + clone.member_code + '!';

        if (element.owner) {
          clone.type = ClientTypes.OWNER_MEMBER;
          state.ownerMember = element;
        } else {
          clone.type = ClientTypes.MEMBER;
        }

        realMembers.push(clone);
        members.push(clone);
      }
    });

    // Pick out the members
    state.clients.forEach((element) => {
      // Check if the member is already in the members array
      const memberAlreadyExists = members.find((member: ExtendedClient) =>
        member.member_class === element.member_class &&
        member.member_code === element.member_code &&
        member.instance_id === element.instance_id);

      if (!memberAlreadyExists) {
        // If "virtual member" is not in members array, create and add it
        const clone = {...element} as any;
        clone.type = ClientTypes.VIRTUAL_MEMBER;

        // This should not happen, but better to throw error than create an invalid client id
        if (!element.instance_id) {
          throw new Error('Missing instance id');
        }

        // Create "virtual member" id
        clone.id = createClientId(
          element.instance_id,
          element.member_class,
          element.member_code,
        );

        clone.subsystem_code = undefined;

        // Create a name from member_name
        if (clone.member_name) {
          clone.visibleName = clone.member_name;
          clone.sortNameAsc = createMemberAscSortName(clone, clone.member_name);
          clone.sortNameDesc = createMemberDescSortName(clone, clone.member_name);
        } else {
          clone.visibleName = UNKNOWN_NAME;
          clone.sortNameAsc = createMemberAscSortName(clone, UNKNOWN_NAME);
          clone.sortNameDesc = createMemberDescSortName(clone, UNKNOWN_NAME);
        }

        clone.status = undefined;
        members.push(clone);
      }

      // Push subsystems to an array
      if (element.subsystem_code) {
        const clone = {...element} as ExtendedClient;
        clone.visibleName = clone.subsystem_code;
        clone.type = ClientTypes.SUBSYSTEM;

        if (element.member_name) {
          clone.sortNameAsc = createSortName(clone, element.member_name);
          clone.sortNameDesc = createSortName(clone, element.member_name);
        } else {
          clone.sortNameAsc = createSortName(clone, UNKNOWN_NAME);
          clone.sortNameDesc = createSortName(clone, UNKNOWN_NAME);
        }

        subsystems.push(clone);
      }
    });

    state.realMembers = realMembers;
    state.subsystems = subsystems;
    state.members = members;
    // Combine the arrays
    state.formattedClients = [...new Set([...subsystems, ...members])];
  },

  storeLocalMembers(state, clients: []) {
    state.localMembers = clients;
  },
  setLoading(state, loading: boolean) {
    state.clientsLoading = loading;
  },
};

export const actions: ActionTree<ClientsState, RootState> = {
  fetchClients({ commit, rootGetters }) {

    commit('setLoading', true);

    return axios.get('/clients')
      .then((res) => {
        commit('storeClients', res.data);
      })
      .catch((error) => {
        throw error;
      })
      .finally(() => {
        commit('setLoading', false);
      });
  },

  fetchLocalMembers({ commit, rootGetters }) {
    return axios.get('/clients?show_members=true&internal_search=true')
      .then((res) => {
        const filtered = res.data.filter((client: Client) => {
          return !client.subsystem_code;
        });

        commit('storeLocalMembers', filtered);
      })
      .catch((error) => {
        throw error;
      });
  },

};

export const clientsModule: Module<ClientsState, RootState> = {
  namespaced: false,
  state: clientsState,
  getters,
  actions,
  mutations,
};
