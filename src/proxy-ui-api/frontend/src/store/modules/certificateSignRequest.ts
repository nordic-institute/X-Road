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
import { saveResponseAsFile } from '@/util/helpers';
import {
  Key,
  Client,
  CertificateAuthority,
  CsrSubjectFieldDescription,
  KeyWithCertificateSigningRequestId,
  KeyLabelWithCsrGenerate,
  KeyUsageType,
  CsrFormat,
  TokenType,
} from '@/openapi-types';
import * as api from '@/util/api';
import { encodePathParameter } from '@/util/api';

export interface CsrState {
  csrKey: Key | null;
  csrClient: string | null;
  usage: string | null;
  certificationService: string;
  csrFormat: string;
  certificationServiceList: CertificateAuthority[];
  keyId: string;
  form: CsrSubjectFieldDescription[];
  keyLabel: string | undefined;
  tokenId: string | undefined;
  tokenType: string | null;
  memberIds: string[];
  isNewMember: boolean;
}

const getDefaultState = () => {
  return {
    csrKey: null,
    csrClient: null,
    usage: null,
    certificationService: '',
    csrFormat: CsrFormat.DER,
    certificationServiceList: [],
    keyId: '',
    form: [],
    keyLabel: undefined,
    tokenId: undefined,
    tokenType: null,
    memberIds: [],
    isNewMember: false,
  };
};

// Initial state. The state can be reseted with this.
const csrState = getDefaultState();

export const csrGetters: GetterTree<CsrState, RootState> = {
  csrClient(state): string | null {
    return state.csrClient;
  },
  csrKey(state): Key | null {
    return state.csrKey;
  },
  usage(state): string | null {
    return state.usage;
  },
  certificationService(state): string {
    return state.certificationService;
  },
  csrFormat(state): string {
    return state.csrFormat;
  },
  csrForm(state): CsrSubjectFieldDescription[] {
    return state.form;
  },
  certificationServiceList(state): CertificateAuthority[] {
    return state.certificationServiceList;
  },
  keyId(state): string {
    return state.keyId;
  },
  keyLabel(state): string | undefined {
    return state.keyLabel;
  },
  isUsageReadOnly(state): boolean {
    // if creating CSR for a hardware token, only sign CSRs can be created
    if (state.tokenType == TokenType.HARDWARE) {
      return true;
    }
    // Usage type can be selected only when the Key doesn't have already have it set
    if (state.csrKey && state.csrKey.usage) {
      return true;
    }
    return false;
  },
  filteredServiceList(state): CertificateAuthority[] {
    // Return the list of available auth services based on the current usage type
    if (state.usage === KeyUsageType.SIGNING) {
      const filtered = state.certificationServiceList.filter(
        (service: CertificateAuthority) => {
          return !service.authentication_only;
        },
      );
      return filtered;
    }
    return state.certificationServiceList;
  },
  csrRequestBody(state) {
    // Creates an object that can be used as body for generate CSR request
    const subjectFieldValues: { [key: string]: string | undefined } = {};

    state.form.forEach((item) => {
      subjectFieldValues[item.id] = item.default_value;
    });

    return {
      key_usage_type: state.usage,
      ca_name: state.certificationService,
      csr_format: state.csrFormat,
      subject_field_values: subjectFieldValues,
      member_id: state.csrClient,
    };
  },
  csrTokenId(state): string | undefined {
    return state.tokenId;
  },
  memberIds(state): string[] {
    return state.memberIds;
  },
};

export const mutations: MutationTree<CsrState> = {
  resetCsrState(state) {
    Object.assign(state, getDefaultState());
  },
  storeCsrClient(state, client: string | null) {
    state.csrClient = client;
  },
  storeCsrKey(state, key: Key) {
    state.csrKey = key;
  },
  storeUsage(state, usage: string) {
    state.usage = usage;
  },
  storeCertificationService(state, cs: string) {
    state.certificationService = cs;
  },
  storeCsrFormat(state, csrFormat: string) {
    state.csrFormat = csrFormat;
  },
  storeCertificationServiceList(state, csl: CertificateAuthority[]) {
    state.certificationServiceList = csl;
  },
  storeForm(state, form: CsrSubjectFieldDescription[]) {
    state.form = form;
  },
  storeKeyId(state, id: string) {
    state.keyId = id;
  },
  storeKeyLabel(state, label: string) {
    state.keyLabel = label;
  },
  storeCsrTokenId(state, tokenId: string) {
    state.tokenId = tokenId;
  },
  storeCsrTokenType(state, tokenType: string) {
    state.tokenType = tokenType;
  },
  storeMemberIds(state, ids: string[]) {
    state.memberIds = ids;
  },
  storeCsrIsNewMember(state, isNewMember = false) {
    state.isNewMember = isNewMember;
  },
};

export const actions: ActionTree<CsrState, RootState> = {
  resetCsrState({ commit }) {
    commit('resetCsrState');
  },
  setCsrTokenId({ commit }, tokenId: string) {
    commit('storeCsrTokenId', tokenId);
  },
  setCsrTokenType({ commit }, tokenType: string) {
    if (tokenType == TokenType.HARDWARE) {
      // can only create SIGNING CSRs for HARDWARE token
      commit('storeUsage', KeyUsageType.SIGNING);
    }
    commit('storeCsrTokenType', tokenType);
  },
  setKeyId({ commit }, keyId: string) {
    commit('storeKeyId', keyId);
  },
  fetchCertificateAuthorities({ commit }) {
    api
      .get<CertificateAuthority[]>('/certificate-authorities')
      .then((res) => {
        commit('storeCertificationServiceList', res.data);
      })
      .catch((error) => {
        throw error;
      });
  },
  fetchCsrForm({ commit, state }) {
    let params = {
      key_usage_type: state.usage,
    } as { [key: string]: unknown };

    if (state.usage === KeyUsageType.SIGNING) {
      params = {
        ...params,
        member_id: state.csrClient,
        is_new_member: state.isNewMember,
      };
    }

    return api
      .get<CsrSubjectFieldDescription>(
        `/certificate-authorities/${encodePathParameter(
          state.certificationService,
        )}/csr-subject-fields`,
        {
          params,
        },
      )
      .then((res) => {
        commit('storeForm', res.data);
      })
      .catch((error) => {
        throw error;
      });
  },

  fetchKeyData({ commit, state }) {
    return api
      .get<Key>(`/keys/${encodePathParameter(state.keyId)}`)
      .then((res) => {
        commit('storeCsrKey', res.data);

        if (res.data.usage) {
          commit('storeUsage', res.data.usage);
        }
      })
      .catch((error) => {
        throw error;
      });
  },

  setCsrFormat({ commit }, csrFormat: string) {
    commit('storeCsrFormat', csrFormat);
  },

  generateCsr({ getters, state }) {
    const requestBody = getters.csrRequestBody;
    return api
      .post(`/keys/${encodePathParameter(state.keyId)}/csrs`, requestBody, {
        responseType: 'arraybuffer',
      })
      .then((response) => {
        saveResponseAsFile(
          response,
          `csr_${requestBody.key_usage_type}.${requestBody.csr_format}`,
        );
      })
      .catch((error) => {
        throw error;
      });
  },

  generateKeyAndCsr({ getters }, tokenId: string) {
    const crtObject = getters.csrRequestBody;

    const body: KeyLabelWithCsrGenerate = {
      key_label: '',
      csr_generate_request: crtObject,
    };

    // Add key label only if it has characters
    if (getters.keyLabel && getters.keyLabel.length > 0) {
      body.key_label = getters.keyLabel;
    }

    return api
      .post<KeyWithCertificateSigningRequestId>(
        `/tokens/${tokenId}/keys-with-csrs`,
        body,
      )
      .then((response) => {
        // Fetch and save the CSR file data
        api
          .get(
            `/keys/${response.data.key.id}/csrs/${response.data.csr_id}?csr_format=${crtObject.csr_format}`,
            {
              responseType: 'arraybuffer',
            },
          )
          .then((res) => {
            saveResponseAsFile(res);
          });
      })
      .catch((error) => {
        throw error;
      });
  },

  setupSignKey({ commit }) {
    // Initialize the state with sign type Key. Needed for "add client" wizard.
    const templateKey: Key = {
      id: '',
      name: '',
      label: '',
      certificates: [],
      certificate_signing_requests: [],
      usage: KeyUsageType.SIGNING,
    };

    commit('storeCsrKey', templateKey);
    commit('storeUsage', KeyUsageType.SIGNING);
  },

  fetchAllMemberIds({ commit }) {
    return api
      .get<Client[]>('/clients?show_members=true')
      .then((res) => {
        const idSet = new Set();
        res.data.forEach((client) => {
          idSet.add(
            `${client.instance_id}:${client.member_class}:${client.member_code}`,
          );
        });

        commit('storeMemberIds', Array.from(idSet));
      })
      .catch((error) => {
        throw error;
      });
  },
};

export const csrModule: Module<CsrState, RootState> = {
  namespaced: false,
  state: csrState,
  getters: csrGetters,
  actions,
  mutations,
};
