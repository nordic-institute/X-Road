<!--
   The MIT License
   Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
   Copyright (c) 2018 Estonian Information System Authority (RIA),
   Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
   Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)

   Permission is hereby granted, free of charge, to any person obtaining a copy
   of this software and associated documentation files (the "Software"), to deal
   in the Software without restriction, including without limitation the rights
   to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
   copies of the Software, and to permit persons to whom the Software is
   furnished to do so, subject to the following conditions:

   The above copyright notice and this permission notice shall be included in
   all copies or substantial portions of the Software.

   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
   FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
   AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
   LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
   OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
   THE SOFTWARE.
 -->
<template>
  <div>
    <table class="xrd-table keys-table">
      <thead>
        <tr>
          <th class="title-col">{{ $t(title) }}</th>
          <th class="id-col">{{ $t('keys.id') }}</th>
          <th class="ocsp-col">{{ $t('keys.ocsp') }}</th>
          <th class="expiration-col">{{ $t('keys.expires') }}</th>
          <th class="status-col">{{ $t('keys.status') }}</th>
          <th class="action-col"></th>
        </tr>
      </thead>

      <tbody v-for="key in keys" v-bind:key="key.id">
        <!-- SOFTWARE token table body -->
        <template v-if="tokenType === 'SOFTWARE'">
          <KeyRow
            :tokenLoggedIn="tokenLoggedIn"
            :tokenKey="key"
            @generate-csr="generateCsr(key)"
            @key-click="keyClick(key)"
          />

          <CertificateRow
            v-for="cert in key.certificates"
            v-bind:key="cert.id"
            :cert="cert"
            @certificate-click="certificateClick(cert, key)"
          >
            <div slot="certificateAction">
              <LargeButton
                class="table-button-fix test-register"
                :outlined="false"
                text
                v-if="
                  showRegisterCertButton &&
                  cert.possible_actions.includes('REGISTER')
                "
                @click="showRegisterCertDialog(cert)"
                >{{ $t('action.register') }}</LargeButton
              >
            </div>
          </CertificateRow>
        </template>

        <!-- HARDWARE token table body -->
        <template v-if="tokenType === 'HARDWARE'">
          <KeyRow
            :tokenLoggedIn="tokenLoggedIn"
            :tokenKey="key"
            @generate-csr="generateCsr(key)"
            @key-click="keyClick(key)"
          />

          <CertificateRow
            v-for="cert in key.certificates"
            v-bind:key="cert.id"
            :cert="cert"
            @certificate-click="certificateClick(cert, key)"
          >
            <div slot="certificateAction">
              <template v-if="canImportFromToken">
                <LargeButton
                  v-if="cert.possible_actions.includes('IMPORT_FROM_TOKEN')"
                  class="table-button-fix"
                  :outlined="false"
                  text
                  @click="importCert(cert.certificate_details.hash)"
                  >{{ $t('keys.importCert') }}</LargeButton
                >

                <!-- Special case where HW cert has auth usage -->
                <div v-else-if="key.usage === 'AUTHENTICATION'">
                  {{ $t('keys.authNotSupported') }}
                </div>
              </template>
            </div>
          </CertificateRow>
        </template>

        <!-- CSRs -->
        <template
          v-if="
            key.certificate_signing_requests &&
            key.certificate_signing_requests.length > 0
          "
        >
          <tr
            v-for="req in key.certificate_signing_requests"
            v-bind:key="req.id"
          >
            <td class="td-name">
              <div class="name-wrap">
                <i class="icon-Certificate icon" />
                <div>{{ $t('keys.request') }}</div>
              </div>
            </td>
            <td colspan="4">{{ req.id }}</td>
            <td class="td-align-right">
              <LargeButton
                class="table-button-fix"
                :outlined="false"
                text
                v-if="
                  req.possible_actions.includes('DELETE') && canDeleteCsr(key)
                "
                @click="showDeleteCsrDialog(req, key)"
                >{{ $t('keys.deleteCsr') }}</LargeButton
              >
            </td>
          </tr>
        </template>
      </tbody>
    </table>

    <RegisterCertificateDialog
      :dialog="registerDialog"
      @save="registerCert"
      @cancel="registerDialog = false"
    />

    <ConfirmDialog
      :dialog="confirmDeleteCsr"
      title="keys.deleteCsrTitle"
      text="keys.deleteCsrText"
      @cancel="confirmDeleteCsr = false"
      @accept="deleteCsr()"
    />
  </div>
</template>

<script lang="ts">
/**
 * Table component for an array of keys
 */
import Vue from 'vue';
import RegisterCertificateDialog from './RegisterCertificateDialog.vue';
import KeyRow from './KeyRow.vue';
import CertificateRow from './CertificateRow.vue';
import {
  Key,
  KeyUsageType,
  TokenCertificate,
  TokenCertificateSigningRequest,
} from '@/openapi-types';
import { Permissions } from '@/global';
import * as api from '@/util/api';
import { encodePathParameter } from '@/util/api';

export default Vue.extend({
  components: {
    RegisterCertificateDialog,
    KeyRow,
    CertificateRow,
  },

  props: {
    keys: {
      type: Array,
      required: true,
    },
    title: {
      type: String,
      required: true,
    },
    tokenLoggedIn: {
      type: Boolean,
    },
    tokenType: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      registerDialog: false,
      confirmDeleteCsr: false,
      usageTypes: KeyUsageType,
      selectedCert: undefined as TokenCertificate | undefined,
      selectedCsr: undefined as TokenCertificateSigningRequest | undefined,
      selectedKey: undefined as Key | undefined,
    };
  },
  computed: {
    canImportFromToken(): boolean {
      // Can the user import certificate from hardware token
      return this.$store.getters.hasPermission(Permissions.IMPORT_SIGN_CERT);
    },

    showRegisterCertButton(): boolean {
      // Decide if the user can register a certificate
      return this.$store.getters.hasPermission(
        Permissions.SEND_AUTH_CERT_REG_REQ,
      );
    },
  },
  methods: {
    canDeleteCsr(key: Key): boolean {
      // Decide if the user can delete CSR based on the key usage type and permissions
      if (key.usage === 'AUTHENTICATION') {
        return this.$store.getters.hasPermission(Permissions.DELETE_AUTH_CERT);
      }
      return this.$store.getters.hasPermission(Permissions.DELETE_SIGN_CERT);
    },
    keyClick(key: Key): void {
      this.$emit('key-click', key);
    },
    certificateClick(cert: TokenCertificate, key: Key): void {
      this.$emit('certificate-click', { cert, key });
    },
    generateCsr(key: Key): void {
      this.$emit('generate-csr', key);
    },
    importCert(hash: string): void {
      this.$emit('import-cert-by-hash', hash);
    },
    showRegisterCertDialog(cert: TokenCertificate): void {
      this.registerDialog = true;
      this.selectedCert = cert;
    },
    registerCert(address: string): void {
      this.registerDialog = false;
      if (!this.selectedCert) {
        return;
      }

      api
        .put(
          `/token-certificates/${this.selectedCert.certificate_details.hash}/register`,
          { address },
        )
        .then(() => {
          this.$store.dispatch('showSuccess', 'keys.certificateRegistered');
          this.$emit('refresh-list');
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
        });
    },
    showDeleteCsrDialog(req: TokenCertificateSigningRequest, key: Key): void {
      this.confirmDeleteCsr = true;
      this.selectedCsr = req;
      this.selectedKey = key;
    },
    deleteCsr(): void {
      this.confirmDeleteCsr = false;

      if (!this.selectedKey || !this.selectedCsr) {
        return;
      }

      api
        .remove(
          `/keys/${encodePathParameter(
            this.selectedKey.id,
          )}/csrs/${encodePathParameter(this.selectedCsr.id)}`,
        )
        .then(() => {
          this.$store.dispatch('showSuccess', 'keys.csrDeleted');
          this.$emit('refresh-list');
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
        });
    },
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/tables';
.icon {
  color: $XRoad-WarmGrey100;
  margin-right: 20px;
}

.keys-table {
  margin-top: 20px;
}

.table-button-fix {
  margin-left: auto;
  margin-right: 0;
}

.td-align-right {
  text-align: right;
}

.td-name {
  text-align: center;
  vertical-align: middle;
}

.name-wrap {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-left: 2.7rem;

  i.v-icon.mdi-file-document-outline {
    margin-left: 42px;
  }
}
.title-col {
  width: 30%;
}
.ocsp-col,
.expiration-col,
.status-col,
.action-col {
  width: 10%;
}
</style>
