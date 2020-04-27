import axios from 'axios';
import _ from 'lodash';
import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import { RootState } from '../types';
import i18n from './../../i18n';

export interface Client {
  id: string;
  name?: string | null;
  sortNameAsc?: string | null;
  sortNameDesc?: string | null;
  member_name: string | null;
  member_class: string;
  member_code: string;
  subsystem_code: string | null;
  created?: string | null;
  type?: string;
  status?: string;
}

export interface ClientsState {
  clients: Client[];
  loading: boolean;
  localMembers: Client[];
}

export const clientsState: ClientsState = {
  clients: [],
  loading: false,
  localMembers: [],
};

function createSortName(client: Client, sortName: string): any {
  // Create a sort id for client in form  "ACMEGOV:1234 MANAGEMENT"
  return sortName + client.member_class + client.member_code + ' ' + client.subsystem_code;
}

function createMemberAscSortName(client: Client, sortName: string | null): any {
  // Create a sort id for member in form  "ACMEGOV:1234"
  return sortName + client.member_class + client.member_code;
}

function createMemberDescSortName(client: Client, sortName: any): any {
  // Create a sort id for member in form  "ACMEGOV:1234!"
  return sortName + client.member_class + client.member_code + '!';
}

export const getters: GetterTree<ClientsState, RootState> = {
  clients(state): Client[] {
    // New arrays to separate members and subsystems
    const members: Client[] = [];
    const subsystems: Client[] = [];
    const UNKNOWN_NAME: string = i18n.t('client.unknownMember') as string;

    // Find the owner member (there is only one) it has member_name, but no subsystem_code
    state.clients.forEach((element, index) => {
      if (!element.subsystem_code) {
        const clone = _.cloneDeep(element);
        clone.type = 'owner';
        clone.subsystem_code = null;
        clone.name = clone.member_name;
        clone.sortNameAsc = createMemberAscSortName(clone, clone.member_name); // clone.member_name + clone.member_class + clone.member_code;
        clone.sortNameDesc = createMemberDescSortName(clone, clone.member_name); // clone.member_name + clone.member_class + clone.member_code + '!';
        members.push(clone);
        return;
      }
    });

    // Pick out the members
    state.clients.forEach((element) => {
      // Check if the member is already in the members array
      const memberAlreadyExists = members.find((value, index) => {
        const cli = value as Client;

        // Compare member class and member code
        if (cli.member_class === element.member_class && cli.member_code === element.member_code) {
          return true;
        }

        return false;
      });

      if (!memberAlreadyExists) {
        // If member is not in members array, create and add it
        const clone = _.cloneDeep(element);
        clone.type = 'client';

        // Create member id by removing the last part of subsystem's id
        const idArray = clone.id.split(':');
        idArray.pop();
        clone.id = idArray.join(':');
        clone.subsystem_code = null;

        // Create a name from member_name
        if (clone.member_name) {
          clone.name = clone.member_name;
          clone.sortNameAsc = createMemberAscSortName(clone, clone.member_name);
          clone.sortNameDesc = createMemberDescSortName(clone, clone.member_name);
        } else {
          clone.name = UNKNOWN_NAME;
          clone.sortNameAsc = createMemberAscSortName(clone, UNKNOWN_NAME);
          clone.sortNameDesc = createMemberDescSortName(clone, UNKNOWN_NAME);
        }

        clone.status = undefined;
        members.push(clone);
      }

      // Push subsystems to an array
      if (element.subsystem_code) {
        const clone = _.cloneDeep(element);
        clone.name = clone.subsystem_code;

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

    // Combine the arrays
    return [...new Set([...subsystems, ...members])];
  },

  localMembers(state): Client[] {
    return state.localMembers;
  },

  localMembersIds(state): Client[] {
    return state.localMembers;
  },

  loading(state): boolean {
    return state.loading;
  },
};

export const mutations: MutationTree<ClientsState> = {
  storeClients(state, clients: []) {
    state.clients = clients;
  },
  storeLocalMembers(state, clients: []) {
    state.localMembers = clients;
  },
  setLoading(state, loading: boolean) {
    state.loading = loading;
  },
};

export const actions: ActionTree<ClientsState, RootState> = {
  fetchClients({ commit, rootGetters }) {

    commit('setLoading', true);

    return axios.get('/clients')
      .then((res) => {
        console.log(res);
        commit('storeClients', res.data);
      })
      .catch((error) => {
        console.log(error);
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
