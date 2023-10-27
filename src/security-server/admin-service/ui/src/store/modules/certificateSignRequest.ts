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
  CsrGenerate,
} from '@/openapi-types';
import { defineStore } from 'pinia';
import * as api from '@/util/api';
import { encodePathParameter } from '@/util/api';

export interface CsrState {
  csrKey: Key | undefined;
  csrClient: string | undefined;
  usage: KeyUsageType | undefined;
  certificationService: string;
  csrFormat: CsrFormat;
  certificationServiceList: CertificateAuthority[];
  keyId: string;
  form: CsrSubjectFieldDescription[];
  keyLabel: string | undefined;
  tokenId: string | undefined;
  tokenType: string | undefined;
  memberIds: string[];
  isNewMember: boolean;
}

export const useCsr = defineStore('csr', {
  state: (): CsrState => {
    return {
      csrKey: undefined,
      csrClient: undefined,
      usage: undefined,
      certificationService: '',
      csrFormat: CsrFormat.DER,
      certificationServiceList: [],
      keyId: '',
      form: [],
      keyLabel: undefined,
      tokenId: undefined,
      tokenType: undefined,
      memberIds: [],
      isNewMember: false,
    };
  },
  getters: {
    csrForm: (state) => state.form,

    csrTokenId: (state) => state.tokenId,

    csrRequestBody(state): CsrGenerate {
      // Creates an object that can be used as body for generate CSR request
      const subjectFieldValues: Record<string, string> = {};

      state.form.forEach((item) => {
        subjectFieldValues[item.id] = item.default_value as string;
      });

      return {
        key_usage_type: state.usage as KeyUsageType,
        ca_name: state.certificationService,
        csr_format: state.csrFormat as CsrFormat,
        subject_field_values: subjectFieldValues,
        member_id: state.csrClient,
      };
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

    isUsageReadOnly(state): boolean {
      // if creating CSR for a hardware token, only sign CSRs can be created
      if (state.tokenType === TokenType.HARDWARE) {
        return true;
      }
      // Usage type can be selected only when the Key doesn't already have it set
      if (state.csrKey && state.csrKey.usage) {
        return true;
      }
      return false;
    },
  },

  actions: {
    fetchAllMemberIds() {
      return api
        .get<Client[]>('/clients?show_members=true')
        .then((res) => {
          const idSet = new Set<string>();
          res.data.forEach((client) => {
            idSet.add(
              `${client.instance_id}:${client.member_class}:${client.member_code}`,
            );
          });

          this.memberIds = Array.from(idSet);
        })
        .catch((error) => {
          throw error;
        });
    },

    fetchCertificateAuthorities() {
      return api
        .get<CertificateAuthority[]>('/certificate-authorities')
        .then((res) => {
          this.certificationServiceList = res.data;
        })
        .catch((error) => {
          throw error;
        });
    },
    fetchCsrForm() {
      let params = {
        key_usage_type: this.usage,
      } as { [key: string]: unknown };

      if (this.usage === KeyUsageType.SIGNING) {
        params = {
          ...params,
          member_id: this.csrClient,
          is_new_member: this.isNewMember,
        };
      }

      return api
        .get<CsrSubjectFieldDescription[]>(
          `/certificate-authorities/${encodePathParameter(
            this.certificationService,
          )}/csr-subject-fields`,
          {
            params,
          },
        )
        .then((res) => {
          this.form = res.data;
        })
        .catch((error) => {
          throw error;
        });
    },

    fetchKeyData() {
      return api
        .get<Key>(`/keys/${encodePathParameter(this.keyId)}`)
        .then((res) => {
          this.csrKey = res.data;

          if (res.data.usage) {
            this.usage = res.data.usage;
          }
        })
        .catch((error) => {
          throw error;
        });
    },

    generateKeyAndCsr(tokenId: string) {
      const crtObject = this.csrRequestBody;

      const body: KeyLabelWithCsrGenerate = {
        key_label: '',
        csr_generate_request: crtObject,
      };

      // Add key label only if it has characters
      if (this.keyLabel && this.keyLabel.length > 0) {
        body.key_label = this.keyLabel;
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

    requestGenerateCsr() {
      const requestBody = this.csrRequestBody;
      return api
        .post(`/keys/${encodePathParameter(this.keyId)}/csrs`, requestBody, {
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

    resetCsrState() {
      // Clear the store state
      this.$reset();
    },
    setCsrTokenId(tokenId: string) {
      this.tokenId = tokenId;
    },
    setCsrTokenType(tokenType: string) {
      if (tokenType === TokenType.HARDWARE) {
        // can only create SIGNING CSRs for HARDWARE token
        this.usage = KeyUsageType.SIGNING;
      }
      this.tokenType = tokenType;
    },
    setKeyId(keyId: string) {
      this.keyId = keyId;
    },
    setCsrForm(form: CsrSubjectFieldDescription[]) {
      this.form = form;
    },
    setupSignKey() {
      // Initialize the state with sign type Key. Needed for "add client" wizard.
      const templateKey: Key = {
        id: '',
        name: '',
        label: '',
        certificates: [],
        certificate_signing_requests: [],
        usage: KeyUsageType.SIGNING,
      };
      this.csrKey = templateKey;
      this.usage = KeyUsageType.SIGNING;
    },

    storeCsrClient(client: string | undefined) {
      this.csrClient = client;
    },

    storeCsrIsNewMember(isNewMember = false) {
      this.isNewMember = isNewMember;
    },

    storeKeyId(id: string) {
      this.keyId = id;
    },
  },
});
