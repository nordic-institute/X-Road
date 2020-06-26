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

    <div v-if="filtered && filtered.length < 1">
      {{ $t('services.noMatches') }}
    </div>

    <template v-if="filtered">
      <token-expandable
        v-for="token in filtered"
        v-bind:key="token.id"
        @refreshList="fetchData"
        @tokenLogout="logoutDialog = true"
        @tokenLogin="loginDialog = true"
        @addKey="addKey"
        :token="token"
      />
    </template>

    <ConfirmDialog
      :dialog="logoutDialog"
      title="keys.logOutTitle"
      text="keys.logOutText"
      @cancel="logoutDialog = false"
      @accept="acceptTokenLogout()"
    />

    <TokenLoginDialog
      :dialog="loginDialog"
      @cancel="loginDialog = false"
      @save="tokenLogin"
    />
  </div>
</template>

<script lang="ts">
// View for keys tab
import Vue from 'vue';
import { RouteName } from '@/global';
import TokenExpandable from './TokenExpandable.vue';
import TokenLoginDialog from '@/components/token/TokenLoginDialog.vue';
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue';
import { mapGetters } from 'vuex';
import { Key, Token, TokenCertificate } from '@/openapi-types';

export default Vue.extend({
  components: {
    TokenExpandable,
    ConfirmDialog,
    TokenLoginDialog,
  },
  data() {
    return {
      search: '',
      loginDialog: false,
      logoutDialog: false,
    };
  },
  computed: {
    ...mapGetters(['tokens']),
    filtered(): Token[] {
      if (!this.tokens || this.tokens.length === 0) {
        return [];
      }

      // Sort array by id:s so it doesn't jump around. Order of items in the backend reply changes between requests.
      let arr = JSON.parse(JSON.stringify(this.tokens)).sort(
        (a: Token, b: Token) => {
          if (a.id < b.id) {
            return -1;
          }
          if (a.id > b.id) {
            return 1;
          }

          // equal id:s. (should not happen)
          return 0;
        },
      );

      if (!this.search) {
        return arr;
      }

      const mysearch = this.search.toLowerCase();

      if (mysearch.length < 1) {
        return this.tokens;
      }

      arr.forEach((token: Token) => {
        token.keys.forEach((key: Key) => {
          const certs = key.certificates.filter((cert: TokenCertificate) => {
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
    fetchData(): void {
      // Fetch tokens from backend
      this.$store.dispatch('fetchTokens').catch((error) => {
        this.$store.dispatch('showError', error);
      });
    },
    acceptTokenLogout(): void {
      const token: Token = this.$store.getters.selectedToken;

      if (!token) {
        return;
      }

      this.$store.dispatch('tokenLogout', token.id).then(
        () => {
          this.$store.dispatch('showSuccess', 'keys.loggedOut');
        },
        (error) => {
          this.$store.dispatch('showError', error);
        },
      );

      this.logoutDialog = false;
    },
    tokenLogin(): void {
      this.fetchData();
      this.loginDialog = false;
    },
    addKey() {
      this.$router.push({
        name: RouteName.AddKey,
        params: { tokenId: this.$store.getters.selectedToken.id },
      });
    },
  },
  created() {
    this.fetchData();
  },
});
</script>

<style lang="scss" scoped>
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
</style>
