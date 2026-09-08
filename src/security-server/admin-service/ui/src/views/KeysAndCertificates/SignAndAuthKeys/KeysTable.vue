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
  <div class="pr-4 pb-4 pl-4">
    <v-table class="xrd">
      <KeysTableThead :sort-direction="sortDirection" :selected-sort="selectedSort" @set-sort="setSort" />
      <tbody v-for="key in sortedKeys" :key="key.id">
        <!-- SOFTWARE token table body -->
        <template v-if="tokenType === tokenTypes.SOFTWARE">
          <KeyRow :token-logged-in="tokenLoggedIn" :token-key="key" @generate-csr="generateCsr(key)" @key-click="keyClick(key)" />

          <CertificateRow
            v-for="cert in key.certificates"
            :key="cert.certificate_details.hash"
            :cert="cert"
            :is-acme-certificate="isAcmeCertificate(cert)"
            @certificate-click="certificateClick(cert, key)"
          >
            <template #certificateAction>
              <XrdBtn
                v-if="showRegisterCertButton && cert.possible_actions?.includes(PossibleAction.REGISTER)"
                class="table-button-fix test-register"
                variant="text"
                text="action.register"
                color="tertiary"
                @click="showRegisterCertDialog(cert)"
              />
            </template>
          </CertificateRow>
        </template>

        <!-- HARDWARE token table body -->
        <template v-if="tokenType === 'HARDWARE'">
          <KeyRow :token-logged-in="tokenLoggedIn" :token-key="key" @generate-csr="generateCsr(key)" @key-click="keyClick(key)" />

          <CertificateRow
            v-for="cert in key.certificates"
            :key="cert.certificate_details.hash"
            :cert="cert"
            :is-acme-certificate="isAcmeCertificate(cert)"
            @certificate-click="certificateClick(cert, key)"
          >
            <template #certificateAction>
              <template v-if="canImportFromToken">
                <XrdBtn
                  v-if="cert.possible_actions?.includes(PossibleAction.IMPORT_FROM_TOKEN)"
                  variant="text"
                  text="keys.importCert"
                  color="tertiary"
                  @click="importCert(cert.certificate_details.hash)"
                />

                <!-- Special case where HW cert has auth usage -->
                <div v-else-if="key.usage === 'AUTHENTICATION'">
                  {{ $t('keys.authNotSupported') }}
                </div>
              </template>
            </template>
          </CertificateRow>
        </template>

        <!-- CSRs -->
        <template v-if="key.certificate_signing_requests && key.certificate_signing_requests.length > 0">
          <tr v-for="req in key.certificate_signing_requests" :key="req.id">
            <td class="td-name pl-13">
              <XrdLabelWithIcon icon="editor_choice" color="on-surface" :label="$t('keys.request')" />
            </td>
            <td colspan="5">{{ req.id }}</td>
            <td class="text-end">
              <XrdBtn
                v-if="isAcmeCapable(req, key)"
                data-test="order-acme-certificate-button"
                class="table-button-fix"
                variant="text"
                color="tertiary"
                text="keys.orderAcmeCertificate"
                :disabled="!canImportCertificate(key) || !tokenLoggedIn"
                :loading="acmeOrderLoading[req.id]"
                @click="openAcmeOrderCertificateDialog(req, key)"
              />
              <XrdBtn
                v-if="req.possible_actions.includes(PossibleAction.DELETE) && canDeleteCsr(key)"
                data-test="delete-csr-button"
                class="table-button-fix"
                variant="text"
                color="tertiary"
                text="keys.deleteCsr"
                @click="showDeleteCsrDialog(req, key)"
              />
            </td>
          </tr>
        </template>
      </tbody>
    </v-table>

    <RegisterCertificateDialog :dialog="registerDialog" @save="registerCert" @cancel="registerDialog = false" />

    <AcmeOrderCertificateDialog
      v-if="selectedCsr && showAcmeOrderCertificateDialog"
      :csr="selectedCsr as TokenCertificateSigningRequest"
      :key-usage="selectedKey?.usage"
      @cancel="showAcmeOrderCertificateDialog = false"
      @save="orderCertificateViaAcme($event)"
    />

    <XrdConfirmDialog
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
import { Key, KeyUsageType, PossibleAction, TokenCertificate, TokenCertificateSigningRequest, TokenType } from '@/openapi-types';
import { Permissions } from '@/global';
import * as Sorting from './keyColumnSorting';
import { KeysSortColumn } from './keyColumnSorting';
import { mapActions, mapState } from 'pinia';
import { useUser } from '@/store/modules/user';
import { useCsr } from '@/store/modules/certificateSignRequest';
import AcmeOrderCertificateDialog from '@/views/KeysAndCertificates/SignAndAuthKeys/AcmeOrderCertificateDialog.vue';
import { XrdBtn, XrdLabelWithIcon, useNotifications, XrdConfirmDialog } from '@niis/shared-ui';

export default defineComponent({
  components: {
    AcmeOrderCertificateDialog,
    XrdBtn,
    RegisterCertificateDialog,
    KeyRow,
    CertificateRow,
    KeysTableThead,
    XrdLabelWithIcon,
    XrdConfirmDialog,
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
  emits: ['key-click', 'certificate-click', 'generate-csr', 'import-cert-by-hash', 'refresh-list'],
  setup() {
    const { addError, addSuccessMessage } = useNotifications();
    return { addError, addSuccessMessage };
  },
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
      return Sorting.keyArraySort(this.keys, this.selectedSort, this.sortDirection);
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
    ...mapActions(useCsr, ['orderAcmeCertificate', 'deleteCsrFromKey', 'registerCertificate']),
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
          certificationService.certificate_profile_info == certificateRequest.certificate_profile &&
          certificationService.acme_capable &&
          (key.usage == KeyUsageType.AUTHENTICATION || !certificationService.authentication_only),
      );
    },
    isAcmeCertificate(cert: TokenCertificate): boolean {
      const certIssuer = this.certificationServiceList.find(
        (certificationService) => certificationService.name == cert.certificate_details.issuer_common_name,
      );
      return certIssuer?.acme_capable ?? false;
    },
    canImportCertificate(key: Key): boolean {
      return (
        (key.usage == KeyUsageType.AUTHENTICATION && this.hasPermission(Permissions.IMPORT_AUTH_CERT)) ||
        (key.usage == KeyUsageType.SIGNING && this.hasPermission(Permissions.IMPORT_SIGN_CERT))
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

      this.registerCertificate(this.selectedCert.certificate_details.hash, address)
        .then(() => {
          this.addSuccessMessage('keys.certificateRegistered');
          this.$emit('refresh-list');
        })
        .catch((error) => this.addError(error));
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
      const csrId = this.selectedCsr?.id || '';
      this.acmeOrderLoading[csrId] = true;
      this.orderAcmeCertificate(this.selectedCsr as TokenCertificateSigningRequest, caName, this.selectedKey?.usage)
        .then(() => {
          this.addSuccessMessage('keys.acmeCertOrdered');
          this.$emit('refresh-list');
        })
        .catch((error) => {
          this.addError(error);
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

      this.deleteCsrFromKey(this.selectedKey.id, this.selectedCsr.id)
        .then(() => {
          this.addSuccessMessage('keys.csrDeleted');
          this.$emit('refresh-list');
        })
        .catch((error) => {
          this.addError(error);
        });
    },
  },
});
</script>

<style lang="scss" scoped></style>
