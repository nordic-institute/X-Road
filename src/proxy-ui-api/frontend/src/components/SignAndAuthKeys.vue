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
          <large-button @click="login(token, index)" v-if="!token.open">Log in</large-button>
          <large-button @click="logout(token, index)" v-if="token.open" outlined>Log out</large-button>
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
              <large-button outlined class="button-spacing" :disabled="!token.open">Add key</large-button>
              <large-button outlined :disabled="!token.open">Import Cert.</large-button>
            </div>

            <!-- AUTH table -->
            <table class="xrd-table" v-if="getAuthKeys(token.keys).length > 0">
              <thead>
                <tr>
                  <th>{{$t('keys.authKeyCert')}}</th>
                  <th>{{$t('keys.id')}}</th>
                  <th>{{$t('keys.ocsp')}}</th>
                  <th>{{$t('keys.expires')}}</th>
                  <th>{{$t('keys.status')}}</th>
                </tr>
              </thead>

              <tbody v-for="key in getAuthKeys(token.keys)" v-bind:key="key.id">
                <div class="name-wrap-top">
                  <v-icon class="icon">mdi-key-outline</v-icon>
                  <div class="clickable-link" @click="keyClick(key)">{{key.name}}</div>
                </div>
                <tr v-for="cert in key.certificates" v-bind:key="cert.id">
                  <td>
                    <div class="name-wrap">
                      <v-icon class="icon">mdi-file-document-outline</v-icon>
                      <div
                        class="clickable-link"
                        @click="certificateClick(cert)"
                      >{{cert.issuer_common_name}}</div>
                    </div>
                  </td>
                  <td>{{cert.client_id}}</td>
                  <td>{{cert.ocsp_status}}</td>
                  <td>{{cert.not_after | formatDate}}</td>
                  <td class="status-cell">
                    <certificate-status :certificate="cert" />
                  </td>
                </tr>
              </tbody>
            </table>

            <!-- SIGN table -->
            <template v-if="getSignKeys(token.keys).length > 0">
              <table class="xrd-table" v-for="key in getSignKeys(token.keys)" v-bind:key="key.id">
                <thead>
                  <tr>
                    <th>{{$t('keys.signKeyCert')}}</th>
                    <th>{{$t('keys.id')}}</th>
                    <th>{{$t('keys.ocsp')}}</th>
                    <th>{{$t('keys.expires')}}</th>
                    <th>{{$t('keys.status')}}</th>
                  </tr>
                </thead>

                <div class="name-wrap-top">
                  <v-icon class="icon">mdi-key-outline</v-icon>
                  <div class="clickable-link" @click="keyClick(key)">{{key.name}}</div>
                </div>

                <tbody>
                  <tr v-for="cert in key.certificates" v-bind:key="cert.id">
                    <td>
                      <div class="name-wrap">
                        <v-icon class="icon">mdi-file-document-outline</v-icon>
                        <div
                          class="clickable-link"
                          @click="certificateClick(cert)"
                        >{{cert.issuer_common_name}}</div>
                      </div>
                    </td>
                    <td>{{cert.client_id}}</td>
                    <td>{{cert.ocsp_status}}</td>
                    <td>{{cert.not_after | formatDate}}</td>
                    <td class="status-cell">
                      <certificate-status :certificate="cert" />
                    </td>
                  </tr>
                </tbody>
              </table>
            </template>
          </div>
        </template>
      </expandable>
    </template>

    <!-- Confirm dialog for logging out of token -->
    <confirmDialog
      :dialog="logoutDialog"
      title="services.deleteTitle"
      text="services.deleteWsdlText"
      @cancel="logoutDialog = false"
      @accept="acceptLogout()"
    />

    <token-login-dialog :dialog="loginDialog" @cancel="loginDialog = false" @save="doLogin()" />
  </div>
</template>

<script lang="ts">
// View for services tab
import Vue from 'vue';
import axios from 'axios';
import { Permissions, RouteName } from '@/global';
import Expandable from '@/components/Expandable.vue';
import WarningDialog from '@/components/WarningDialog.vue';
import ServiceIcon from '@/components/ServiceIcon.vue';
import LargeButton from '@/components/LargeButton.vue';
import CertificateStatus from '@/components/CertificateStatus.vue';
import TokenLoginDialog from '@/components/TokenLoginDialog.vue';
import ConfirmDialog from '@/components/ConfirmDialog.vue';

import _ from 'lodash';

export default Vue.extend({
  components: {
    Expandable,
    WarningDialog,
    ServiceIcon,
    LargeButton,
    CertificateStatus,
    TokenLoginDialog,
    ConfirmDialog,
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
      selected: undefined as any,
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
            return cert.issuer_common_name.toLowerCase().includes(mysearch);
          });
          key.certificates = certs;
        });
      });

      arr.forEach((token: any) => {
        const keys = token.keys.filter((key: any) => {
          if (key.certificates && key.certificates.length > 0) {
            return true;
          }

          return key.name.toLowerCase().includes(mysearch);
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
        params: { hash: cert.hash },
      });
    },

    login(token: any, index: number): void {
      this.selected = { token, index };
      this.loginDialog = true;
    },

    doLogin(): void {
      const token = this.selected.token;
      const index = this.selected.index;
      token.open = true;
      Vue.set(this.tokens, index, token);
      this.loginDialog = false;
    },

    logout(token: any, index: number): void {
      this.selected = { token, index };
      this.logoutDialog = true;
    },

    acceptLogout(): void {
      const token = this.selected.token;
      const index = this.selected.index;
      token.open = false;
      Vue.set(this.tokens, index, token);
      this.logoutDialog = false;
    },

    getAuthKeys(keys: any): any {
      // Filter out service deascriptions that don't include search term
      const filtered = keys.filter((key: any) => {
        return key.type === 'AUTH';
      });

      return filtered;
    },

    getSignKeys(keys: any): any {
      // Filter out service deascriptions that don't include search term
      const filtered = keys.filter((key: any) => {
        return key.type === 'SIGN';
      });

      return filtered;
    },

    descClose(descId: string) {
      const index = this.expanded.findIndex((element: any) => {
        return element === descId;
      });

      if (index >= 0) {
        this.expanded.splice(index, 1);
      }
    },
    descOpen(descId: string) {
      const index = this.expanded.findIndex((element: any) => {
        return element === descId;
      });

      if (index === -1) {
        this.expanded.push(descId);
      }
    },
    isExpanded(descId: string) {
      return this.expanded.includes(descId);
    },

    fetchData(): void {
      axios
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
}

.icon {
  margin-left: 18px;
  margin-right: 20px;
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

.name-wrap {
  display: flex;
  flex-direction: row;
  align-items: baseline;
}

.name-wrap-top {
  @extend .name-wrap;
  margin-top: 18px;
  margin-bottom: 5px;
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

.status-cell {
  width: 110px;
}
</style>

