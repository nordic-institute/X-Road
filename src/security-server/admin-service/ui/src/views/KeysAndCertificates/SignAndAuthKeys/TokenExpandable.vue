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
  <XrdExpandable
    class="expandable"
    :is-open="tokenExpanded(token.id)"
    @open="toggleToken"
  >
    <template #link="{ toggle, opened }">
      <div
        data-test="token-name"
        class="d-flex flex-row align-center cursor-pointer"
        @click="toggle"
      >
        <div
          class="token-name font-weight-medium"
          :class="
            tokenStatusClass
              ? tokenStatusClass
              : opened
                ? 'on-surface'
                : 'on-surface-variant'
          "
        >
          {{ $t('keys.token.label') }} {{ token.name }}
        </div>

        <v-btn
          data-test="token-icon-button"
          class="ml-2"
          icon="edit"
          density="compact"
          variant="text"
          color="primary"
          size="small"
          @click="openTokenDetails()"
        />
        <TokenDetailsDialog
          v-if="tokenDetailsDialog"
          :id="token.id"
          @cancel="tokenDetailsDialog = false"
          @delete="fetchData"
          @update="fetchData"
        />
      </div>
    </template>

    <template #action="{ opened }">
      <div class="d-flex flex-row align-center">
        <TokenStatusChip :token-status="token.status" />

        <v-slide-x-reverse-transition>
          <div v-if="opened">
            <XrdBtn
              v-if="canAddKey"
              data-test="token-add-key-button"
              variant="text"
              text="keys.addKey"
              prepend-icon="add_circle"
              :disabled="!token.logged_in"
              @click="addKey()"
            />
            <XrdFileUpload
              v-if="canImportCertificate"
              v-slot="{ upload }"
              accepts=".pem, .cer, .der"
              @file-changed="importCert"
            >
              <XrdBtn
                data-test="token-import-cert-button"
                variant="text"
                text="keys.importCert"
                prepend-icon="upload"
                :disabled="!token.logged_in"
                @click="upload"
              />
            </XrdFileUpload>
          </div>
        </v-slide-x-reverse-transition>

        <TokenLoggingButton
          v-if="canActivateToken"
          class="token-logging-button"
          :token="token"
          @token-logout="logout()"
          @token-login="login()"
        />
      </div>
    </template>

    <template #content>
      <div class="mt-2 mr-4 mb-4 ml-4">
        <!-- AUTH keys table -->
        <div class="border xrd-rounded-12 pa-0">
          <div
            v-if="hasAuthKeys"
            :class="{ 'border-b': hasSignKeys || hasOtherKeys }"
          >
            <KeysTableTitle
              :title="$t('keys.authKeyCert')"
              :keys="authKeys"
              :arrow-state="authKeysOpen"
              @click="authKeysOpen = !authKeysOpen"
            />
            <v-slide-y-transition>
              <KeysTable
                v-if="authKeysOpen"
                :keys="authKeys"
                :token-logged-in="token.logged_in"
                :token-type="token.type"
                data-test="auth-keys-table"
                @key-click="keyClick"
                @generate-csr="generateCsr"
                @certificate-click="certificateClick"
                @import-cert-by-hash="importCertByHash"
                @refresh-list="fetchData"
              />
            </v-slide-y-transition>
          </div>
          <!-- SIGN keys table -->

          <div v-if="hasSignKeys" :class="{ 'border-b': hasOtherKeys }">
            <KeysTableTitle
              :title="$t('keys.signKeyCert')"
              :keys="signKeys"
              :arrow-state="signKeysOpen"
              @click="signKeysOpen = !signKeysOpen"
            />

            <v-slide-y-transition>
              <KeysTable
                v-if="signKeysOpen"
                class="keys-table"
                :keys="signKeys"
                :token-logged-in="token.logged_in"
                :token-type="token.type"
                data-test="sign-keys-table"
                @key-click="keyClick"
                @generate-csr="generateCsr"
                @certificate-click="certificateClick"
                @import-cert-by-hash="importCertByHash"
                @refresh-list="fetchData"
              />
            </v-slide-y-transition>
          </div>

          <!-- Keys with unknown type -->
          <div v-if="hasOtherKeys">
            <KeysTableTitle
              :title="$t('keys.unknown')"
              :keys="otherKeys"
              :arrow-state="unknownKeysOpen"
              @click="unknownKeysOpen = !unknownKeysOpen"
            />
            <v-slide-y-transition>
              <UnknownKeysTable
                v-if="unknownKeysOpen"
                :keys="otherKeys"
                :token-logged-in="token.logged_in"
                :token-type="token.type"
                @key-click="keyClick"
                @generate-csr="generateCsr"
                @certificate-click="certificateClick"
                @import-cert-by-hash="importCertByHash"
              />
            </v-slide-y-transition>
          </div>
        </div>
      </div>
    </template>
  </XrdExpandable>
</template>

<script lang="ts">
// View for a token
import { defineComponent, PropType } from 'vue';
import { Permissions, RouteName } from '@/global';
import KeysTable from './KeysTable.vue';
import KeysTableTitle from './KeysTableTitle.vue';
import UnknownKeysTable from './UnknownKeysTable.vue';
import { Key, KeyUsageType, Token, TokenCertificate } from '@/openapi-types';
import TokenLoggingButton from '@/views/KeysAndCertificates/SignAndAuthKeys/TokenLoggingButton.vue';
import {
  getTokenUIStatus,
  TokenUIStatus,
} from '@/views/KeysAndCertificates/SignAndAuthKeys/TokenStatusHelper';
import { mapActions, mapState } from 'pinia';
import { useUser } from '@/store/modules/user';
import { useTokens } from '@/store/modules/tokens';
import { FileUploadResult } from '@/ui-types';
import { XrdExpandable, XrdBtn, useNotifications } from '@niis/shared-ui';
import TokenStatusChip from '@/views/KeysAndCertificates/SignAndAuthKeys/TokenStatusChip.vue';
import { useTokenCertificates } from '@/store/modules/token-certificates';
import TokenDetailsDialog from '@/views/TokenDetails/TokenDetailsDialog.vue';

export default defineComponent({
  components: {
    TokenDetailsDialog,
    TokenStatusChip,
    XrdExpandable,
    XrdBtn,
    KeysTable,
    KeysTableTitle,
    UnknownKeysTable,
    TokenLoggingButton,
  },
  props: {
    token: {
      type: Object as PropType<Token>,
      required: true,
    },
  },
  emits: ['add-key', 'token-login', 'token-logout', 'refresh-list'],
  setup() {
    const { addError, addSuccessMessage } = useNotifications();
    return { addError, addSuccessMessage };
  },
  data() {
    return {
      tokenDetailsDialog: false,
      authKeysOpen: true,
      signKeysOpen: true,
      unknownKeysOpen: true,
    };
  },
  computed: {
    ...mapState(useUser, ['hasPermission']),

    ...mapState(useTokens, ['tokenExpanded']),
    canActivateToken(): boolean {
      return this.hasPermission(Permissions.ACTIVATE_DEACTIVATE_TOKEN);
    },
    canImportCertificate(): boolean {
      return (
        this.hasPermission(Permissions.IMPORT_AUTH_CERT) ||
        this.hasPermission(Permissions.IMPORT_SIGN_CERT)
      );
    },
    canAddKey(): boolean {
      return this.hasPermission(Permissions.GENERATE_KEY);
    },
    tokenStatusClass(): string {
      switch (getTokenUIStatus(this.token.status)) {
        case TokenUIStatus.Inactive:
          return 'opacity-60 on-surface';
        case TokenUIStatus.Unavailable:
          return 'text-error';
        case TokenUIStatus.Unsaved:
          return 'text-error';
        default:
          return '';
      }
    },
    authKeys() {
      return this.getAuthKeys(this.token.keys);
    },
    signKeys() {
      return this.getSignKeys(this.token.keys);
    },
    otherKeys() {
      return this.getOtherKeys(this.token.keys);
    },
    hasAuthKeys() {
      return this.authKeys.length > 0;
    },
    hasSignKeys() {
      return this.signKeys.length > 0;
    },
    hasOtherKeys() {
      return this.otherKeys.length > 0;
    },
  },
  created() {
    if (this.getAuthKeys(this.token.keys).length > 10) {
      this.authKeysOpen = false;
    }
    if (this.getSignKeys(this.token.keys).length > 10) {
      this.signKeysOpen = false;
    }
  },
  methods: {
    ...mapActions(useTokens, ['setSelectedToken', 'hideToken', 'expandToken']),
    ...mapActions(useTokenCertificates, [
      'importTokenCertificate',
      'importTokenCertificateByHash',
    ]),
    addKey(): void {
      this.setSelectedToken(this.token);
      this.$emit('add-key');
    },
    login(): void {
      this.setSelectedToken(this.token);
      this.$emit('token-login');
    },
    logout(): void {
      this.setSelectedToken(this.token);
      this.$emit('token-logout');
    },
    openTokenDetails(): void {
      this.tokenDetailsDialog = true;
    },
    keyClick(key: Key): void {
      this.$router.push({
        name: RouteName.Key,
        params: { id: key.id },
      });
    },
    certificateClick(payload: { cert: TokenCertificate; key: Key }): void {
      this.$router.push({
        name: RouteName.Certificate,
        params: {
          hash: payload.cert.certificate_details.hash,
          usage: payload.key.usage ?? 'undefined',
        },
      });
    },
    getAuthKeys(keys: Key[]): Key[] {
      return keys.filter((key: Key) => {
        return key.usage === KeyUsageType.AUTHENTICATION;
      });
    },
    getSignKeys(keys: Key[]): Key[] {
      return keys.filter((key: Key) => key.usage === KeyUsageType.SIGNING);
    },
    getOtherKeys(keys: Key[]): Key[] {
      // Keys that don't have assigned usage type
      return keys.filter(
        (key: Key) =>
          key.usage !== KeyUsageType.SIGNING &&
          key.usage !== KeyUsageType.AUTHENTICATION,
      );
    },
    toggleToken(opened: boolean): void {
      if (opened) {
        this.expandToken(this.token.id);
      } else {
        this.hideToken(this.token.id);
      }
    },
    importCert(event: FileUploadResult) {
      this.importTokenCertificate(event.buffer)
        .then((certificate) => {
          if (certificate.ocsp_verify_before_activation_error) {
            this.addError(
              this.$t('keys.importCertOcspVerifyWarning', {
                errorMessage: certificate.ocsp_verify_before_activation_error,
              }),
              { warning: true },
            );
          }
        })
        .then(() => this.addSuccessMessage('keys.importCertSuccess'))
        .then(() => this.fetchData())
        .catch((error) => this.addError(error));
    },
    importCertByHash(hash: string) {
      this.importTokenCertificateByHash(hash)
        .then(() => this.addSuccessMessage('keys.importCertSuccess'))
        .then(() => this.fetchData())
        .catch((error) => this.addError(error));
    },
    generateCsr(key: Key) {
      this.$router.push({
        name: RouteName.GenerateCertificateSignRequest,
        params: {
          keyId: key.id,
          tokenType: this.token.type,
        },
      });
    },
    fetchData(): void {
      // Fetch tokens from backend
      this.$emit('refresh-list');
    },
  },
});
</script>

<style lang="scss" scoped></style>
