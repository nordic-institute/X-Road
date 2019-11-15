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
                class="button-spacing"
                :disabled="!token.logged_in"
              >{{$t('keys.addKey')}}</large-button>
              <large-button outlined :disabled="!token.logged_in">{{$t('keys.importCert')}}</large-button>
            </div>

            <!-- AUTH table -->
            <keys-table
              v-if="getAuthKeys(token.keys).length > 0"
              :keys="getAuthKeys(token.keys)"
              title="keys.authKeyCert"
              @keyClick="keyClick"
              @certificateClick="certificateClick"
            />

            <!-- SIGN table -->
            <keys-table
              v-if="getSignKeys(token.keys).length > 0"
              :keys="getSignKeys(token.keys)"
              title="keys.signKeyCert"
              @keyClick="keyClick"
              @certificateClick="certificateClick"
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
import Expandable from '@/components/Expandable.vue';
import WarningDialog from '@/components/WarningDialog.vue';
import ServiceIcon from '@/components/ServiceIcon.vue';
import LargeButton from '@/components/LargeButton.vue';
import CertificateStatus from '@/components/CertificateStatus.vue';
import TokenLoginDialog from '@/components/TokenLoginDialog.vue';
import ConfirmDialog from '@/components/ConfirmDialog.vue';
import KeysTable from '@/components/KeysTable.vue';
import { mapGetters } from 'vuex';
import * as api from '@/util/api';

import _ from 'lodash';

interface Token {
  active: boolean;
  available: boolean;
  id: string;
  keys: any[];
  name: string;
  read_only: boolean;
  saved_to_configuration: boolean;
  status: string;
  token_infos: any[];
  type: string;
  logged_in?: boolean; // keeps track of the Token logged in status
}

interface ISelectedObject {
  token: Token;
  index: number;
}

type SelectedObject = undefined | ISelectedObject;

export default Vue.extend({
  components: {
    Expandable,
    WarningDialog,
    ServiceIcon,
    LargeButton,
    CertificateStatus,
    TokenLoginDialog,
    ConfirmDialog,
    KeysTable,
  },
  data() {
    return {
      search: '',
      logoutDialog: false,
      loginDialog: false,
      selectedServiceDesc: undefined,
      selectedIndex: -1,
      componentKey: 0,
      expanded: [] as string[],
      tokens: [],
      addWsdlBusy: false,
      refreshWsdlBusy: false,
      selected: undefined as SelectedObject,
    };
  },
  computed: {
    canEditServiceDesc(): boolean {
      return this.$store.getters.hasPermission(Permissions.EDIT_WSDL);
    },
    filtered(): any {
      if (!this.tokens || this.tokens.length === 0) {
        return [];
      }

      // Sort array by id:s so it doesn't jump around. Order of items in the backend reply changes between requests.
      let arr = _.cloneDeep(this.tokens).sort((a: any, b: any) => {
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

      arr.forEach((token: any) => {
        token.keys.forEach((key: any) => {
          const certs = key.certificates.filter((cert: any) => {
            if (cert.owner_id) {
              return cert.owner_id.toLowerCase().includes(mysearch);
            }
            return false;
          });
          key.certificates = certs;
        });
      });

      arr.forEach((token: any) => {
        const keys = token.keys.filter((key: any) => {
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

      arr = arr.filter((token: any) => {
        if (token.keys && token.keys.length > 0) {
          return true;
        }

        return token.name.toLowerCase().includes(mysearch);
      });

      return arr;
    },
  },
  methods: {
    tokenClick(token: any): void {
      this.$router.push({
        name: RouteName.Token,
        params: { id: token.id },
      });
    },

    keyClick(key: any): void {
      this.$router.push({
        name: RouteName.Key,
        params: { id: key.id },
      });
    },

    certificateClick(cert: any): void {
      this.$router.push({
        name: RouteName.Certificate,
        params: { hash: cert.certificate_details.hash },
      });
    },

    login(token: any, index: number): void {
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

    logout(token: any, index: number): void {
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

    getAuthKeys(keys: any): any {
      // Filter out service deascriptions that don't include search term
      const filtered = keys.filter((key: any) => {
        return key.usage === 'AUTHENTICATION';
      });

      return filtered;
    },

    getSignKeys(keys: any): any {
      // Filter out service deascriptions that don't include search term
      const filtered = keys.filter((key: any) => {
        return key.usage === 'SIGNING';
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
@import '../assets/tables';

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
  margin-right: 20px;
}
</style>

