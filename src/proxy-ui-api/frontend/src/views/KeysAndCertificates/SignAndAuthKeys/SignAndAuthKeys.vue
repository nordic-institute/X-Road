<template>
  <div class="wrapper">
    <div class="search-row">
      <v-text-field
        v-model="search"
        :label="$t('services.service')"
        single-line
        hide-details
        class="search-input"
      >
        <v-icon slot="append">mdi-magnify</v-icon>
      </v-text-field>
    </div>

    <div v-if="filtered && filtered.length < 1">{{$t('services.noMatches')}}</div>

    <template v-if="filtered">
      <expandable
        v-for="(token, index) in filtered"
        v-bind:key="token.id"
        class="expandable"
        @open="descOpen(token.id)"
        @close="descClose(token.id)"
        :isOpen="isExpanded(token.id)"
      >
        <template v-slot:action>
          <large-button
            @click="login(token, index)"
            v-if="!token.logged_in"
            :disabled="!token.available"
          >{{$t('keys.logIn')}}</large-button>
          <large-button
            @click="logout(token, index)"
            v-if="token.logged_in"
            outlined
          >{{$t('keys.logOut')}}</large-button>
        </template>

        <template v-slot:link>
          <div
            class="clickable-link"
            v-if="canEditServiceDesc"
            @click="tokenClick(token)"
          >{{$t('keys.token')}} {{token.name}}</div>
          <div v-else>{{token.type}} ({{token.url}})</div>
        </template>

        <template v-slot:content>
          <div>
            <div class="button-wrap">
              <large-button
                outlined
                @click="addKey(token, index)"
                :disabled="!token.logged_in"
              >{{$t('keys.addKey')}}</large-button>
              <div v-if="token.type === 'SOFTWARE'">
                <large-button
                    outlined
                    class="button-spacing"
                    :disabled="!token.logged_in"
                    @click="$refs.certUpload[0].click()"
                >{{$t('keys.importCert')}}</large-button>
                <input
                  v-show="false"
                  ref="certUpload"
                  type="file"
                  accept=".pem, .cer, .der"
                  @change="importCert"
              />
              </div>
            </div>

            <!-- AUTH table -->
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

            <!-- SIGN table -->
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
              @certificateClick="certificateClick"
              @importCertByHash="importCertByHash"
            />
          </div>
        </template>
      </expandable>
    </template>

    <!-- Confirm dialog for logging out of token -->
    <confirmDialog
      :dialog="logoutDialog"
      title="keys.logOutTitle"
      text="keys.logOutText"
      @cancel="logoutDialog = false"
      @accept="acceptLogout()"
    />

    <KeyLabelDialog :dialog="keyLabelDialog" @save="doAddKey" @cancel="keyLabelDialog = false" />

    <token-login-dialog
      v-if="selected && selected.token"
      :dialog="loginDialog"
      :tokenId="selected.token.id"
      @cancel="loginDialog = false"
      @save="doLogin"
    />
  </div>
</template>

<script lang="ts">
// View for services tab
import Vue from 'vue';
import { Permissions, RouteName } from '@/global';
import Expandable from '@/components/ui/Expandable.vue';
import LargeButton from '@/components/ui/LargeButton.vue';
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue';
import ServiceIcon from '@/components/ui/ServiceIcon.vue';
import CertificateStatus from './CertificateStatus.vue';
import TokenLoginDialog from './TokenLoginDialog.vue';
import KeysTable from './KeysTable.vue';
import UnknownKeysTable from './UnknownKeysTable.vue';
import KeyLabelDialog from './KeyLabelDialog.vue';
import { mapGetters } from 'vuex';
import { Key, Token, Certificate } from '@/types';
import * as api from '@/util/api';

import _ from 'lodash';

interface ISelectedObject {
  token: Token;
  index: number;
}

type SelectedObject = undefined | ISelectedObject;

export default Vue.extend({
  components: {
    Expandable,
    ServiceIcon,
    LargeButton,
    CertificateStatus,
    TokenLoginDialog,
    ConfirmDialog,
    KeysTable,
    UnknownKeysTable,
    KeyLabelDialog,
  },
  data() {
    return {
      search: '',
      logoutDialog: false,
      loginDialog: false,
      tokens: [],
      selected: undefined as SelectedObject,
      keyLabelDialog: false,
    };
  },
  computed: {
    canEditServiceDesc(): boolean {
      return this.$store.getters.hasPermission(Permissions.EDIT_WSDL);
    },
    filtered(): Token[] {
      if (!this.tokens || this.tokens.length === 0) {
        return [];
      }

      // Sort array by id:s so it doesn't jump around. Order of items in the backend reply changes between requests.
      let arr = _.cloneDeep(this.tokens).sort((a: Token, b: Token) => {
        if (a.id < b.id) {
          return -1;
        }
        if (a.id > b.id) {
          return 1;
        }

        // equal id:s. (should not happen)
        return 0;
      });

      if (!this.search) {
        return arr;
      }

      const mysearch = this.search.toLowerCase();

      if (mysearch.length < 1) {
        return this.tokens;
      }

      arr.forEach((token: Token) => {
        token.keys.forEach((key: Key) => {
          const certs = key.certificates.filter((cert: Certificate) => {
            if (cert.owner_id) {
              return cert.owner_id.toLowerCase().includes(mysearch);
            }
            return false;
          });
          key.certificates = certs;
        });
      });

      arr.forEach((token: Token) => {
        const keys = token.keys.filter((key: Key) => {
          if (key.certificates && key.certificates.length > 0) {
            return true;
          }

          if (key.name) {
            return key.name.toLowerCase().includes(mysearch);
          }
          return false;
        });
        token.keys = keys;
      });

      arr = arr.filter((token: Token) => {
        if (token.keys && token.keys.length > 0) {
          return true;
        }

        return token.name.toLowerCase().includes(mysearch);
      });

      return arr;
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

    certificateClick(cert: Certificate): void {
      this.$router.push({
        name: RouteName.Certificate,
        params: { hash: cert.certificate_details.hash },
      });
    },

    login(token: Token, index: number): void {
      this.selected = { token, index };
      this.loginDialog = true;
    },

    doLogin(password: string): void {
      if (!this.selected) {
        return;
      }

      this.fetchData();
      this.loginDialog = false;
    },

    logout(token: Token, index: number): void {
      this.selected = { token, index };
      this.logoutDialog = true;
    },

    acceptLogout(): void {
      if (!this.selected) {
        return;
      }

      api
        .put(`/tokens/${this.selected.token.id}/logout`, {})
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
      // Filter out service descriptions that don't include search term
      const filtered = keys.filter((key: Key) => {
        return key.usage === 'AUTHENTICATION';
      });

      return filtered;
    },

    getSignKeys(keys: Key[]): Key[] {
      // Filter out service descriptions that don't include search term
      const filtered = keys.filter((key: Key) => {
        return key.usage === 'SIGNING';
      });

      return filtered;
    },

    getOtherKeys(keys: Key[]): Key[] {
      // Filter out service descriptions that don't include search term
      const filtered = keys.filter((key: Key) => {
        return key.usage !== 'SIGNING' && key.usage !== 'AUTHENTICATION';
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

    addKey(token: Token, index: number) {
      // Open dialog for new key
      this.selected = { token, index };
      this.keyLabelDialog = true;
    },

    doAddKey(label: string) {
      // Send add new key request to backend
      this.keyLabelDialog = false;

      if (!this.selected) {
        return;
      }
      const request = label.length > 0 ? { label } : {};

      api
        .post(`/tokens/${this.selected.token.id}/keys`, request)
        .then((res) => {
          this.fetchData();
          this.$bus.$emit('show-success', 'keys.keyAdded');
        })
        .catch((error) => {
          this.$bus.$emit('show-error', error.message);
        });
    },
    importCert(event: any) {

      const fileList = (event && event.target && event.target.files) || (event && event.dataTransfer && event.dataTransfer.files);
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
          .then(() => {
            this.$bus.$emit('show-success', 'keys.importCertSuccess');
          }, (error) => {
            this.$bus.$emit('show-error', error.message);
          },
          );
      };
      reader.readAsArrayBuffer(fileList[0]);
    },
    importCertByHash(hash: string) {
      api
        .post(`/token-certificates/${hash}/import`, {})
        .then(() => {
          this.$bus.$emit('show-success', 'keys.importCertSuccess');
        }, (error) => {
          this.$bus.$emit('show-error', error.message);
        });
    },
    generateCsr(key: Key) {
      // TODO will be implemented later
    },
    fetchData(): void {
      // Fetch tokens from backend
      api
        .get(`/tokens`)
        .then((res) => {
          this.tokens = res.data;
        })
        .catch((error) => {
          this.$bus.$emit('show-error', error.message);
        });
    },
  },
  created() {
    this.fetchData();
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/tables';

.wrapper {
  margin-top: 20px;
  width: 100%;
}

.search-row {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: flex-end;
  width: 100%;
  margin-top: 40px;
  margin-bottom: 24px;
}

.search-input {
  max-width: 300px;
}

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

