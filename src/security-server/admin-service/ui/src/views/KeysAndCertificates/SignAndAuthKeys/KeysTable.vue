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
      <KeysTableThead
        :sort-direction="sortDirection"
        :selected-sort="selectedSort"
        @set-sort="setSort"
      />

      <tbody v-for="key in sortedKeys" :key="key.id">
      <!-- SOFTWARE token table body -->
      <template v-if="tokenType === tokenTypes.SOFTWARE">
        <KeyRow
          :token-logged-in="tokenLoggedIn"
          :token-key="key"
          @generate-csr="generateCsr(key)"
          @key-click="keyClick(key)"
        />

        <CertificateRow
          v-for="cert in key.certificates"
          :key="cert.certificate_details.issuer_distinguished_name"
          :cert="cert"
          @certificate-click="certificateClick(cert, key)"
        >
          <template #certificateAction>
            <xrd-button
              v-if="
                  showRegisterCertButton &&
                  cert.possible_actions?.includes(PossibleAction.REGISTER)
                "
              class="table-button-fix test-register"
              :outlined="false"
              text
              @click="showRegisterCertDialog(cert)"
            >{{ $t('action.register') }}
            </xrd-button>
          </template>
        </CertificateRow>
      </template>

      <!-- HARDWARE token table body -->
      <template v-if="tokenType === 'HARDWARE'">
        <KeyRow
          :token-logged-in="tokenLoggedIn"
          :token-key="key"
          @generate-csr="generateCsr(key)"
          @key-click="keyClick(key)"
        />

        <CertificateRow
          v-for="cert in key.certificates"
          :key="cert.certificate_details.issuer_distinguished_name"
          :cert="cert"
          @certificate-click="certificateClick(cert, key)"
        >
          <template #certificateAction>
            <template v-if="canImportFromToken">
              <xrd-button
                v-if="
                    cert.possible_actions?.includes(
                      PossibleAction.IMPORT_FROM_TOKEN,
                    )
                  "
                class="table-button-fix"
                :outlined="false"
                text
                @click="importCert(cert.certificate_details.hash)"
              >{{ $t('keys.importCert') }}
              </xrd-button>

              <!-- Special case where HW cert has auth usage -->
              <div v-else-if="key.usage === 'AUTHENTICATION'">
                {{ $t('keys.authNotSupported') }}
              </div>
            </template>
          </template>
        </CertificateRow>
      </template>

      <!-- CSRs -->
      <template
        v-if="
            key.certificate_signing_requests &&
            key.certificate_signing_requests.length > 0
          "
      >
        <tr v-for="req in key.certificate_signing_requests" :key="req.id">
          <td class="td-name">
            <div class="name-wrap">
              <i class="icon-Certificate cert-icon" />
              <div>{{ $t('keys.request') }}</div>
            </div>
          </td>
          <td colspan="4">{{ req.id }}</td>
          <td class="td-align-right">
            <xrd-button
              v-if="isAcmeCapable(req, key)"
              :disabled="!canImportCertificate(key) || !tokenLoggedIn"
              class="table-button-fix"
              :outlined="false"
              text
              data-test="order-acme-certificate-button"
              :loading="acmeOrderLoading[req.id]"
              @click="openAcmeOrderCertificateDialog(req, key)"
            >
              {{ $t('keys.orderAcmeCertificate') }}
            </xrd-button>
            <xrd-button
              v-if="
                  req.possible_actions.includes(PossibleAction.DELETE) &&
                  canDeleteCsr(key)
                "
              class="table-button-fix"
              :outlined="false"
              text
              data-test="delete-csr-button"
              @click="showDeleteCsrDialog(req, key)"
            >{{ $t('keys.deleteCsr') }}
            </xrd-button>
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

    <AcmeOrderCertificateDialog
      :dialog="showAcmeOrderCertificateDialog"
      :csr="selectedCsr as TokenCertificateSigningRequest"
      :keyUsage="selectedKey?.usage"
      @cancel="showAcmeOrderCertificateDialog = false"
      @save="orderCertificateViaAcme($event)"
    />

    <xrd-confirm-dialog
      v-if="confirmDeleteCsr"
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
import { defineComponent, PropType } from 'vue';
import RegisterCertificateDialog from './RegisterCertificateDialog.vue';
import KeyRow from './KeyRow.vue';
import CertificateRow from './CertificateRow.vue';
import KeysTableThead from './KeysTableThead.vue';
import {
  Key,
  KeyUsageType,
  PossibleAction,
  TokenCertificate,
  TokenCertificateSigningRequest,
  TokenType,
} from "@/openapi-types";
import { Permissions } from '@/global';
import * as Sorting from './keyColumnSorting';
import { KeysSortColumn } from './keyColumnSorting';
import * as api from '@/util/api';
import { encodePathParameter } from '@/util/api';
import { mapActions, mapState } from 'pinia';
import { useUser } from '@/store/modules/user';
import { useNotifications } from '@/store/modules/notifications';
import XrdButton from '@niis/shared-ui/src/components/XrdButton.vue';
import { useCsr } from '@/store/modules/certificateSignRequest';
import AcmeOrderCertificateDialog from "@/views/KeysAndCertificates/SignAndAuthKeys/AcmeOrderCertificateDialog.vue";

export default defineComponent({
  components: {
    AcmeOrderCertificateDialog,
    XrdButton,
    RegisterCertificateDialog,
    KeyRow,
    CertificateRow,
    KeysTableThead,
  },
  props: {
    keys: {
      type: Array as PropType<Key[]>,
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
  emits: [
    'key-click',
    'certificate-click',
    'generate-csr',
    'import-cert-by-hash',
    'refresh-list',
  ],
  data() {
    return {
      registerDialog: false,
      confirmDeleteCsr: false,
      showAcmeOrderCertificateDialog: false,
      selectedCert: undefined as TokenCertificate | undefined,
      selectedCsr: undefined as TokenCertificateSigningRequest | undefined,
      selectedKey: undefined as Key | undefined,
      sortDirection: false,
      selectedSort: KeysSortColumn.NAME,
      tokenTypes: TokenType,
      acmeOrderLoading: {} as Record<string, boolean>,
    };
  },
  computed: {
    PossibleAction() {
      return PossibleAction;
    },
    ...mapState(useUser, ['hasPermission']),
    ...mapState(useCsr, ['certificationServiceList']),
    sortedKeys(): Key[] {
      return Sorting.keyArraySort(
        this.keys,
        this.selectedSort,
        this.sortDirection,
      );
    },
    canImportFromToken(): boolean {
      // Can the user import certificate from hardware token
      return this.hasPermission(Permissions.IMPORT_SIGN_CERT);
    },

    showRegisterCertButton(): boolean {
      // Decide if the user can register a certificate
      return this.hasPermission(Permissions.SEND_AUTH_CERT_REG_REQ);
    },
  },

  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    ...mapActions(useCsr, ['orderAcmeCertificate']),
    setSort(sort: KeysSortColumn): void {
      // Set sort column and direction
      if (sort === this.selectedSort) {
        this.sortDirection = !this.sortDirection;
      }

      this.selectedSort = sort;
    },
    canDeleteCsr(key: Key): boolean {
      // Decide if the user can delete CSR based on the key usage type and permissions
      if (key.usage === 'AUTHENTICATION') {
        return this.hasPermission(Permissions.DELETE_AUTH_CERT);
      }
      return this.hasPermission(Permissions.DELETE_SIGN_CERT);
    },
    isAcmeCapable(certificateRequest: TokenCertificateSigningRequest, key: Key): boolean {
      return this.certificationServiceList.some(
        (certificationService) =>
          certificationService.certificate_profile_info == certificateRequest.certificate_profile
          && certificationService.acme_capable
          && (key.usage == KeyUsageType.AUTHENTICATION || !certificationService.authentication_only),
      );
    },
    canImportCertificate(key: Key): boolean {
      return (
        key.usage == KeyUsageType.AUTHENTICATION && this.hasPermission(Permissions.IMPORT_AUTH_CERT) ||
        key.usage == KeyUsageType.SIGNING && this.hasPermission(Permissions.IMPORT_SIGN_CERT)
      );
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
          this.showSuccess(this.$t('keys.certificateRegistered'));
          this.$emit('refresh-list');
        })
        .catch((error) => {
          this.showError(error);
        });
    },
    showDeleteCsrDialog(req: TokenCertificateSigningRequest, key: Key): void {
      this.confirmDeleteCsr = true;
      this.selectedCsr = req;
      this.selectedKey = key;
    },
    openAcmeOrderCertificateDialog(req: TokenCertificateSigningRequest, key: Key): void {
      this.selectedCsr = req;
      this.selectedKey = key;
      this.showAcmeOrderCertificateDialog = true;
    },
    orderCertificateViaAcme(caName: string): void {
      this.showAcmeOrderCertificateDialog = false;
      let csrId = this.selectedCsr?.id || '';
      this.acmeOrderLoading[csrId] = true;
      this.orderAcmeCertificate(this.selectedCsr as TokenCertificateSigningRequest, caName, this.selectedKey?.usage)
        .then(() => {
          this.showSuccess(this.$t('keys.acmeCertOrdered'));
          this.$emit('refresh-list');
        })
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => {
          this.acmeOrderLoading[csrId] = false;
        });
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
          this.showSuccess(this.$t('keys.csrDeleted'));
          this.$emit('refresh-list');
        })
        .catch((error) => {
          this.showError(error);
        });
    },
  },
});
</script>

<style lang="scss" scoped>
@import '@/assets/tables';

.cert-icon {
  color: $XRoad-WarmGrey100;
  margin-right: 20px;
}

.keys-table {
  margin-top: 0px;
}

.table-button-fix {
  margin-left: auto;
  margin-right: 0;
}

.td-align-right {
  text-align: right;
}

td.td-name {
  padding-left: 30px;
  text-align: center;
  vertical-align: middle;
}

.name-wrap {
  display: flex;
  flex-direction: row;
  align-items: center;
  margin-left: 2.7rem;
}
</style>
