import axios from 'axios';
import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import { RootState } from '../types';

// import mockJson from './mock';
// import mockJson from './fi-all';
// import mockJson from './ee-all';


export interface Client {
  id: string;
  name?: string | null;
  sortName?: string | null;
  descName?: string | null;
  member_name: string | null;
  member_class: string;
  member_code: string;
  subsystem_code: string | null;
  created?: string | null;
  type?: string;
  status?: string;
  subsystems?: Client[];
}

export interface ClientsArray extends Array<Client> { }

export interface DataState {
  clients: ClientsArray;
  loading: boolean;
}

export const dataState: DataState = {
  // clients: mockJson,
  clients: [],
  loading: false,
};

export const getters: GetterTree<DataState, RootState> = {
  mockClients(state): ClientsArray {
    return state.clients;
  },
  mockClientsFlat(state): object[] {

    // New arrays to separate members and subsystems
    const members: object[] = [];
    const subsystems: object[] = [];
    const UNKNOWN_NAME: string = 'unknown member';

    // Find the owner member (there is only one) it has member_name, but no subsystem_code
    state.clients.forEach((element, index) => {
      if (!element.subsystem_code) {
        const clone = JSON.parse(JSON.stringify(element));
        clone.type = 'owner';
        clone.subsystem_code = null;
        clone.name = clone.member_name;
        clone.sortName = createMemberAscSortId(clone, clone.member_name); // clone.member_name + clone.member_class + clone.member_code;
        clone.descName = createMemberDescSortId(clone, clone.member_name); // clone.member_name + clone.member_class + clone.member_code + '!';
        members.push(clone);
        return;
      }
    });


    // Pick out the members
    state.clients.forEach((element) => {
      // Check if the member is already in the members array
      const memberAlreadyExists = members.find((value, index) => {
        const cli = value as Client;

        // TODO: check the full id ?
        if (cli.member_class === element.member_class && cli.member_code === element.member_code) {
          return true;
        }

        return false;
      });

      if (!memberAlreadyExists) {
        // If member is not in members array, create and add it
        const clone = JSON.parse(JSON.stringify(element));
        clone.type = 'client';

        // Create member id
        const idArray = clone.id.split(':');
        idArray.pop();
        clone.id = idArray.join(':');
        clone.subsystem_code = null;

        // Create a name from member_name
        if (clone.member_name) {
          clone.name = clone.member_name;
          clone.sortName = createMemberAscSortId(clone, clone.member_name);
          clone.descName = createMemberDescSortId(clone, clone.member_name);
        } else {
          clone.name = UNKNOWN_NAME;
          clone.sortName = createMemberAscSortId(clone, UNKNOWN_NAME);
          clone.descName = createMemberDescSortId(clone, UNKNOWN_NAME);
        }

        clone.status = undefined;
        members.push(clone);
      }

      // Push subsystems to an array
      if (element.subsystem_code) {
        const clone = JSON.parse(JSON.stringify(element));
        clone.name = clone.subsystem_code;

        if (element.member_name) {
          clone.sortName = createSortId(clone, element.member_name);
          clone.descName = createSortId(clone, element.member_name);
        } else {
          clone.sortName = createSortId(clone, UNKNOWN_NAME);
          clone.descName = createSortId(clone, UNKNOWN_NAME);
        }

        subsystems.push(clone);
      }
    });

    // Combine the arrays
    return [...new Set([...subsystems, ...members])];
  },

  loadings(state): boolean {
    return state.loading;
  },
};

function createSortId(client: Client, sortName: string): any {
  // Create a sort id for client in form  "ACMEGOV:1234 MANAGEMENT"
  return sortName + client.member_class + client.member_code + ' ' + client.subsystem_code;
}

function createMemberAscSortId(client: Client, sortName: string | null): any {
  // Create a sort id for member in form  "ACMEGOV:1234"
  return sortName + client.member_class + client.member_code;
}

function createMemberDescSortId(client: Client, sortName: any): any {
  // Create a sort id for member in form  "ACMEGOV:1234!"
  return sortName + client.member_class + client.member_code + '!';
}


export const mutations: MutationTree<DataState> = {
  setLoading(state, loading: boolean) {
    state.loading = loading;
  },
};

export const actions: ActionTree<DataState, RootState> = {
  fetchData({ commit, rootGetters }) {

    commit('setLoading', true);

    return axios.get('/clients')
      .then((res) => {
        console.log(res);
      })
      .catch((error) => {
        throw error;
      })
      .finally(() => {
        commit('setLoading', false);
      });
  },
};

export const mockDataModule: Module<DataState, RootState> = {
  namespaced: false,
  state: dataState,
  getters,
  actions,
  mutations,
};
