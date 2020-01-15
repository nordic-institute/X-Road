<template>
  <expandable
    class="expandable"
    @open="descOpen(token.id)"
    @close="descClose(token.id)"
    :isOpen="isExpanded(token.id)"
  >
    <template v-slot:action>
      <template v-if="canActivateToken">
        <large-button
          @click="loginDialog = true"
          v-if="!token.logged_in"
          :disabled="!token.available"
        >{{$t('keys.logIn')}}</large-button>
        <large-button
          @click="logoutDialog = true"
          v-if="token.logged_in"
          outlined
        >{{$t('keys.logOut')}}</large-button>
      </template>
    </template>

    <template v-slot:link>
      <div class="clickable-link" @click="tokenClick(token)">{{$t('keys.token')}} {{token.name}}</div>
    </template>

    <template v-slot:content>
      <div>
        <div class="button-wrap" v-if="canActivateToken">
          <large-button
            outlined
            @click="keyLabelDialog = true"
            :disabled="!token.logged_in"
          >{{$t('keys.addKey')}}</large-button>
          <large-button
            outlined
            class="button-spacing"
            :disabled="!token.logged_in"
            @click="$refs.certUpload.click()"
          >{{$t('keys.importCert')}}</large-button>
          <input
            v-show="false"
            ref="certUpload"
            type="file"
            accept=".pem, .cer, .der"
            @change="importCert"
          />
        </div>

        <!-- AUTH keys table -->
        <keys-table
          v-if="getAuthKeys(token.keys).length > 0"
          :keys="getAuthKeys(token.keys)"
          title="keys.authKeyCert"
          :disableGenerateCsr="!token.logged_in"
          :tokenType="token.type"
          @keyClick="keyClick"
          @generateCsr="generateCsr"
          @certificateClick="certificateClick"
          @importCertByHash="importCertByHash"
        />

        <!-- SIGN keys table -->
        <keys-table
          v-if="getSignKeys(token.keys).length > 0"
          :keys="getSignKeys(token.keys)"
          title="keys.signKeyCert"
          :disableGenerateCsr="!token.logged_in"
          :tokenType="token.type"
          @keyClick="keyClick"
          @generateCsr="generateCsr"
          @certificateClick="certificateClick"
          @importCertByHash="importCertByHash"
          @refreshList="fetchData"
        />

        <!-- Keys with unknown type -->
        <unknown-keys-table
          v-if="getOtherKeys(token.keys).length > 0"
          :keys="getOtherKeys(token.keys)"
          title="keys.unknown"
          :disableGenerateCsr="!token.logged_in"
          :tokenType="token.type"
          @keyClick="keyClick"
          @generateCsr="generateCsr"
          @importCertByHash="importCertByHash"
        />
      </div>
    </template>
    <!-- Confirm dialog for logging out of token -->
    <ConfirmDialog
      :dialog="logoutDialog"
      title="keys.logOutTitle"
      text="keys.logOutText"
      @cancel="logoutDialog = false"
      @accept="acceptLogout()"
    />

    <KeyLabelDialog :dialog="keyLabelDialog" @save="addKey" @cancel="keyLabelDialog = false" />

    <TokenLoginDialog
      :dialog="loginDialog"
      :tokenId="token.id"
      @cancel="loginDialog = false"
      @save="login"
    />
  </expandable>
</template>

<script lang="ts">
// View for a token
import Vue from 'vue';
import { Permissions, RouteName, UsageTypes } from '@/global';
import Expandable from '@/components/ui/Expandable.vue';
import LargeButton from '@/components/ui/LargeButton.vue';
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue';
import TokenLoginDialog from './TokenLoginDialog.vue';
import KeysTable from './KeysTable.vue';
import UnknownKeysTable from './UnknownKeysTable.vue';
import KeyLabelDialog from './KeyLabelDialog.vue';
import { mapGetters } from 'vuex';
import { Key, Token, TokenType, TokenCertificate } from '@/types';
import * as api from '@/util/api';

export default Vue.extend({
  components: {
    Expandable,
    LargeButton,
    TokenLoginDialog,
    ConfirmDialog,
    KeysTable,
    UnknownKeysTable,
    KeyLabelDialog,
  },
  props: {
    token: {
      type: Object,
      required: true,
    },
  },
  data() {
    return {
      logoutDialog: false,
      loginDialog: false,
      keyLabelDialog: false,
    };
  },
  computed: {
    canActivateToken(): boolean {
      return this.$store.getters.hasPermission(
        Permissions.ACTIVATE_DEACTIVATE_TOKEN,
      );
    },
  },
  methods: {
    tokenClick(token: Token): void {
      this.$router.push({
        name: RouteName.Token,
        params: { id: token.id },
      });
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
          usage: payload.key.usage,
        },
      });
    },

    login(password: string): void {
      this.fetchData();
      this.loginDialog = false;
    },

    acceptLogout(): void {
      api
        .put(`/tokens/${this.token.id}/logout`, {})
        .then((res) => {
          this.$bus.$emit('show-success', 'keys.loggedOut');
          this.fetchData();
        })
        .catch((error) => {
          this.$bus.$emit('show-error', error.message);
        });

      this.logoutDialog = false;
    },

    getAuthKeys(keys: Key[]): Key[] {
      const filtered = keys.filter((key: Key) => {
        return key.usage === UsageTypes.AUTHENTICATION;
      });

      return filtered;
    },

    getSignKeys(keys: Key[]): Key[] {
      const filtered = keys.filter((key: Key) => {
        return key.usage === UsageTypes.SIGNING;
      });

      return filtered;
    },

    getOtherKeys(keys: Key[]): Key[] {
      // Keys that don't have assigned usage type
      const filtered = keys.filter((key: Key) => {
        return (
          key.usage !== UsageTypes.SIGNING &&
          key.usage !== UsageTypes.AUTHENTICATION
        );
      });

      return filtered;
    },

    descClose(tokenId: string) {
      this.$store.dispatch('hideToken', tokenId);
    },
    descOpen(tokenId: string) {
      this.$store.dispatch('expandToken', tokenId);
    },
    isExpanded(tokenId: string) {
      return this.$store.getters.tokenExpanded(tokenId);
    },

    addKey(label: string) {
      // Send add new key request to backend
      this.keyLabelDialog = false;
      const request = label.length > 0 ? { label } : {};

      api
        .post(`/tokens/${this.token.id}/keys`, request)
        .then((res) => {
          this.fetchData();
          this.$bus.$emit('show-success', 'keys.keyAdded');
        })
        .catch((error) => {
          this.$bus.$emit('show-error', error.message);
        });
    },
    importCert(event: any) {
      const fileList =
        (event && event.target && event.target.files) ||
        (event && event.dataTransfer && event.dataTransfer.files);
      if (!fileList.length) {
        return;
      }

      const reader = new FileReader();

      // Upload file when it's loaded in FileReader
      reader.onload = (e: any) => {
        if (!e || !e.target || !e.target.result) {
          return;
        }

        this.$store
          .dispatch('uploadCertificate', {
            fileData: e.target.result,
          })
          .then(
            () => {
              this.$bus.$emit('show-success', 'keys.importCertSuccess');
              this.fetchData();
            },
            (error) => {
              this.$bus.$emit('show-error', error.message);
            },
          );
      };
      reader.readAsArrayBuffer(fileList[0]);
    },
    importCertByHash(hash: string) {
      api.post(`/token-certificates/${hash}/import`, {}).then(
        () => {
          this.$bus.$emit('show-success', 'keys.importCertSuccess');
          this.fetchData();
        },
        (error) => {
          this.$bus.$emit('show-error', error.message);
        },
      );
    },
    generateCsr(key: Key) {
      this.$router.push({
        name: RouteName.GenerateCertificateSignRequest,
        params: { keyId: key.id },
      });
    },
    fetchData(): void {
      // Fetch tokens from backend
      this.$emit('refreshList');
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/tables';

.clickable-link {
  text-decoration: underline;
  cursor: pointer;
}

.expandable {
  margin-bottom: 10px;
}

.button-wrap {
  margin-top: 10px;
  width: 100%;
  display: flex;
  justify-content: flex-end;
}

.button-spacing {
  margin-left: 20px;
}
</style>

