import axios from 'axios';
import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import { RootState } from '../types';


export interface Client {
  id: string;
  name: string;
  type?: string;
  status?: string;
  subsystems?: Array<Client>;
}

export interface ClientsArray extends Array<Client> { }

export interface DataState {
  cities: [];
  clients: ClientsArray;
  loading: boolean;
}

export const state: DataState = {
  cities: [],
  clients: [
    {
      id: 'Member:Dev:Org:111',
      name: 'Turku',
      status: 'registered',
      type: 'owner',
      subsystems: [{
        id: 'Subsystem:Dev:Org:111:Matti',
        name: 'Matti',
        status: 'registration in progress',
      },
      {
        id: 'Subsystem:Dev:Org:111:Teppo',
        name: 'Teppo',
        status: 'saved',
      },]
    }, {
      name: 'Tampere',
      id: 'Member:Dev:Org:222',
      subsystems: [
        {
          id: 'Subsystem:Dev:Org:222:Amuri',
          name: 'Amuri',
          status: 'registered',
        },
        {
          id: 'Subsystem:Dev:Org:222:Nalkala',
          name: 'Nalkala',
          status: 'deletion in progress',
        },
        {
          id: 'Subsystem:Dev:Org:222:Hervanta',
          name: 'Hervanta',
          status: 'global error',
        },
      ]
    }
  ],
  loading: false,
};

export const getters: GetterTree<DataState, RootState> = {
  cities(state): [] {
    return state.cities;
  },
  clients(state): ClientsArray {
    return state.clients;
  },
  clientsFlat(state): Array<object> {

    const flat: Array<object> = [];

    state.clients.forEach(element => {
      flat.push({ id: element.id, name: element.name, status: element.status, type: element.type || 'client' });
      if (element.subsystems && element.subsystems.length > 0) {
        element.subsystems.forEach(subsystem => {
          flat.push({ id: subsystem.id, name: subsystem.name, status: subsystem.status, type: 'subsystem' });
        });
      }
    });

    return flat;
  },

  loading(state): boolean {
    return state.loading;
  },
};

export const mutations: MutationTree<DataState> = {
  storeCities(state, cities: []) {
    state.cities = cities;
  },
  setLoading(state, loading: boolean) {
    state.loading = loading;
  },
};

export const actions: ActionTree<DataState, RootState> = {
  fetchData({ commit, rootGetters }) {
    if (!rootGetters.isAuthenticated) {
      //console.log('Not authenticated! Cant call get cities!');
      //return;
    }

    commit('setLoading', true);

    return axios.get('/clients')
      .then((res) => {
        console.log(res);
        const cities = res.data;
        commit('storeCities', cities);
      })
      .catch((error) => {
        console.log(error);
        throw error;
      })
      .finally(() => {
        commit('setLoading', false);
      });
  },
  clearData({ commit, rootGetters }) {
    commit('storeCities', []);
  },
};

export const data: Module<DataState, RootState> = {
  namespaced: false,
  state,
  getters,
  actions,
  mutations,
};
