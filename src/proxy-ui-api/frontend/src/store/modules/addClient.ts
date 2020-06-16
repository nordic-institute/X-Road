/**
 * Vuex store for add client/subsystem/member wizards
 */
import { ActionTree, GetterTree, Module, MutationTree } from 'vuex';
import { RootState } from '../types';
import { AddMemberWizardModes, UsageTypes } from '@/global';
import { createClientId } from '@/util/helpers';
import {
  Token,
  Client,
  TokenCertificateSigningRequest,
  TokenCertificate,
  Key,
} from '@/openapi-types';
import * as api from '@/util/api';

interface ReservedMemberData {
  instanceId: string;
  memberClass: string;
  memberCode: string;
}

export interface AddClientState {
  expandedTokens: string[];
  tokens: Token[];
  tokenId: string | undefined;
  selectableClients: Client[];
  selectableMembers: Client[];
  reservedClients: Client[];
  selectedMemberName: string | undefined;
  memberClass: string;
  memberCode: string;
  subsystemCode: string | undefined;
  memberWizardMode: string;
  reservedMemberData: ReservedMemberData | undefined;
}

const getDefaultState = () => {
  return {
    expandedTokens: [],
    tokens: [],
    selectableClients: [],
    selectableMembers: [],
    reservedClients: [],
    selectedMemberName: '',
    memberClass: '',
    memberCode: '',
    subsystemCode: undefined,
    tokenId: undefined,
    memberWizardMode: AddMemberWizardModes.FULL,
    reservedMemberData: undefined,
  };
};

// Initial state. The state can be reseted with this.
const tokensState: AddClientState = getDefaultState();

export const getters: GetterTree<AddClientState, any> = {
  selectableClients(state: AddClientState): Client[] {
    return state.selectableClients;
  },

  selectableMembers(state: AddClientState): Client[] {
    return state.selectableMembers;
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
  selectedMemberName(state: AddClientState): string | undefined {
    return state.selectedMemberName;
  },
  reservedClients(state: AddClientState): Client[] {
    return state.reservedClients;
  },
  addMemberWizardMode(state: AddClientState): string {
    return state.memberWizardMode;
  },
  selectedMemberId(state: AddClientState, rootGetters): string | undefined {
    // Instance id is always the same with current server and members
    return createClientId(
      rootGetters.currentSecurityServer.instance_id,
      state.memberClass,
      state.memberCode,
    );
  },
  reservedMember(state: AddClientState): any {
    return state.reservedMemberData;
  },
};

export const mutations: MutationTree<AddClientState> = {
  resetAddClientState(state: AddClientState) {
    Object.assign(state, getDefaultState());
  },
  setMember(state: AddClientState, member: Client) {
    state.selectedMemberName = member.member_name;
    state.memberClass = member.member_class;
    state.memberCode = member.member_code;
    state.subsystemCode = member.subsystem_code;
  },
  setSelectedMemberName(state: AddClientState, val: string | undefined) {
    state.selectedMemberName = val;
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
  storeSelectableClients(state: AddClientState, clients: Client[]) {
    state.selectableClients = clients;
  },
  storeSelectableMembers(state: AddClientState, clients: Client[]) {
    state.selectableMembers = clients;
  },
  storeReservedClients(state: AddClientState, clients: Client[]) {
    state.reservedClients = clients;
  },
  storeReservedMember(state: AddClientState, memberData: any) {
    state.reservedMemberData = memberData;
  },
  setAddMemberWizardMode(state: AddClientState, mode: string) {
    state.memberWizardMode = mode;
  },
};

export const actions: ActionTree<AddClientState, RootState> = {
  resetAddClientState({ commit }) {
    commit('resetAddClientState');
  },

  fetchSelectableClients({ commit }, id: string) {
    // Fetch clients from backend that can be selected
    return api
      .get(
        '/clients?exclude_local=true&internal_search=false&show_members=false',
      )
      .then((res) => {
        commit('storeSelectableClients', res.data);
      })
      .catch((error) => {
        throw error;
      });
  },

  fetchSelectableMembers({ commit }, id: string) {
    // Fetch clients from backend that can be selected
    return api
      .get('/clients?internal_search=false&show_members=true')
      .then((res) => {
        // Filter out subsystems
        const filtered = res.data.filter((client: Client) => {
          return !client.subsystem_code;
        });

        commit('storeSelectableMembers', filtered);
      })
      .catch((error) => {
        throw error;
      });
  },

  fetchReservedClients({ commit }, client: Client) {
    // Fetch clients from backend that match the selected client without subsystem code
    return api
      .get(
        `/clients?instance=${client.instance_id}&member_class=${client.member_class}&member_code=${client.member_code}&internal_search=true`,
      )
      .then((res) => {
        commit('storeReservedClients', res.data);
      })
      .catch((error) => {
        throw error;
      });
  },

  fetchReservedMembers({ commit }, client: Client) {
    // Fetch clients from backend that match the selected client without subsystem code
    return api
      .get(
        `/clients?instance=${client.instance_id}&member_class=${client.member_class}&member_code=${client.member_code}&internal_search=true`,
      )
      .then((res) => {
        commit('storeReservedClients', res.data);
      })
      .catch((error) => {
        throw error;
      });
  },

  setSelectedMember({ commit }, member: Client) {
    commit('setMember', member);
  },

  createClient({ state }, ignoreWarnings: boolean) {
    const body = {
      client: {
        member_class: state.memberClass,
        member_code: state.memberCode,
        subsystem_code: state.subsystemCode,
      },
      ignore_warnings: ignoreWarnings,
    };

    return api.post('/clients', body).catch((error) => {
      throw error;
    });
  },

  createMember({ state }, ignoreWarnings: boolean) {
    const body = {
      client: {
        member_class: state.memberClass,
        member_code: state.memberCode,
      },
      ignore_warnings: ignoreWarnings,
    };

    return api.post('/clients', body).catch((error) => {
      throw error;
    });
  },

  async searchTokens(
    { commit, dispatch },
    { instanceId, memberClass, memberCode },
  ) {
    const clientsResponse = await api.get(`/clients?instance=${instanceId}
    &member_class=${memberClass}&member_code=${memberCode}&local_valid_sign_cert=true`);

    const matchingClient: boolean = clientsResponse.data.some(
      (client: Client) => {
        if (
          client.member_code === memberCode &&
          client.member_class === memberClass
        ) {
          return true;
        }
      },
    );

    if (matchingClient) {
      // There is a client with valid sign certificate
      commit('setAddMemberWizardMode', AddMemberWizardModes.CERTIFICATE_EXISTS);
      return;
    }

    // Fetch tokens from backend
    const tokenResponse = await api.get(`/tokens`);
    // Create a client id
    const ownerId = createClientId(instanceId, memberClass, memberCode);

    // Find if a token has a sign key with a certificate that has matching client data
    tokenResponse.data.some((token: Token) => {
      return token.keys.some((key: Key) => {
        if (key.usage === UsageTypes.SIGNING) {
          // Go through the keys certificates
          const foundCert: boolean = key.certificates.some(
            (certificate: TokenCertificate) => {
              if (ownerId === certificate.owner_id) {
                commit(
                  'setAddMemberWizardMode',
                  AddMemberWizardModes.CERTIFICATE_EXISTS,
                );
                return true;
              }
            },
          );

          if (foundCert) {
            return true;
          }

          // Go through the keys CSR:s
          key.certificate_signing_requests.some(
            (csr: TokenCertificateSigningRequest) => {
              if (ownerId === csr.owner_id) {
                dispatch('setCsrTokenId', token.id);
                commit(
                  'setAddMemberWizardMode',
                  AddMemberWizardModes.CSR_EXISTS,
                );
                return true;
              }
            },
          );
        }
      });
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
