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
  <xrd-expandable
    class="expandable"
    :is-open="isExpanded(token.id)"
    :color="tokenStatusColor"
    @open="descOpen(token.id)"
    @close="descClose(token.id)"
  >
    <template #link>
      <div
        class="clickable-link identifier-wrap"
        data-test="token-name"
        @click="tokenNameClick()"
      >
        <span
          class="token-status-indicator token-name"
          :class="tokenStatusClass"
        >
          {{ $t('keys.token') }} {{ token.name }}
        </span>
      </div>
    </template>

    <template #action>
      <div class="action-slot-wrapper">
        <template v-if="canActivateToken">
          <TokenLoggingButton
            class="token-logging-button"
            :token="token"
            @token-logout="logout()"
            @token-login="login()"
          />
        </template>
      </div>
    </template>

    <template #content>
      <div>
        <div class="button-wrap mb-6">
          <div>
            A maximum of two configuration source signing keys is allowed on a
            security token
          </div>

          <xrd-button
            v-if="canAddKey"
            outlined
            :disabled="!token.logged_in"
            data-test="token-add-key-button"
            @click="addKey()"
            ><v-icon class="xrd-large-button-icon">icon-Add</v-icon
            >{{ $t('keys.addKey') }}</xrd-button
          >
        </div>

        <!-- SIGN keys table -->
        <div v-if="getSignKeys(token.keys).length > 0">
          <!--  <KeysTableTitle
            title="keys.signKeyCert"
            :keys="getSignKeys(token.keys)"
            :arrowState="signKeysOpen"
            @click="signKeysOpen = !signKeysOpen"
          />

          -->

          <keys-table
            v-if="signKeysOpen"
            class="keys-table"
            :keys="getSignKeys(token.keys)"
            :token-logged-in="token.logged_in"
            :token-type="token.type"
            @key-click="keyClick"
            @refresh-list="fetchData"
          />
        </div>
      </div>
    </template>
  </xrd-expandable>
</template>

<script lang="ts">
// View for a token
import Vue from 'vue';
import KeysTable from './KeysTable.vue';
import { Key, KeyUsageType, Token } from '@/mock-openapi-types';
import TokenLoggingButton from './TokenLoggingButton.vue';
import { Prop } from 'vue/types/options';
import { Colors } from '@/global';
import { getTokenUIStatus, TokenUIStatus } from './TokenStatusHelper';
import { StoreTypes } from '@/global';
import { mapGetters } from 'vuex';

export default Vue.extend({
  components: {
    KeysTable,
    TokenLoggingButton,
  },
  props: {
    token: {
      type: Object as Prop<Token>,
      required: true,
    },
  },
  data() {
    return {
      colors: Colors,
      authKeysOpen: true,
      signKeysOpen: true,
      unknownKeysOpen: true,
    };
  },
  computed: {
    ...mapGetters({
      isExpanded: 'tokenExpanded',
    }),
    canActivateToken(): boolean {
      return true;
      /*  return this.$store.getters[StoreTypes.getters.HAS_PERMISSION](
        Permissions.MOCK_PERMISSION1,
      ); */
    },
    canImportCertificate(): boolean {
      return true;
      /*
      return (
        this.$store.getters[StoreTypes.getters.HAS_PERMISSION](
          Permissions.MOCK_PERMISSION1,
        ) ||
        this.$store.getters[StoreTypes.getters.HAS_PERMISSION](
          Permissions.MOCK_PERMISSION2,
        )
      ); */
    },
    canAddKey(): boolean {
      return true;
      /* return this.$store.getters[StoreTypes.getters.HAS_PERMISSION](
        Permissions.MOCK_PERMISSION2,
      ); */
    },

    tokenLabelKey(): string {
      const status: TokenUIStatus = getTokenUIStatus(this.token.status);

      if (status === TokenUIStatus.Inactive) {
        return 'keys.tokenStatus.inactive';
      } else if (status === TokenUIStatus.Unavailable) {
        return 'keys.tokenStatus.unavailable';
      } else if (status === TokenUIStatus.Unsaved) {
        return 'keys.tokenStatus.unsaved';
      }

      return ''; // if TokenUIStatus is Active or Available or unknown return empty string
    },

    tokenIcon(): string {
      const status: TokenUIStatus = getTokenUIStatus(this.token.status);

      if (status === TokenUIStatus.Inactive) {
        return 'icon-Cancel';
      } else if (status === TokenUIStatus.Unavailable) {
        return 'icon-Error';
      } else if (status === TokenUIStatus.Unsaved) {
        return 'icon-Error';
      }

      return '';
    },
    tokenStatusClass(): string {
      return this.token.logged_in ? 'logged-out' : 'logged-in';
    },
    tokenStatusColor(): string {
      return this.token.logged_in ? this.colors.Black100 : this.colors.Purple20;
    },
  },

  methods: {
    addKey(): void {
      this.$store.commit(StoreTypes.mutations.SET_SELECTED_TOKEN, this.token);
      this.$emit('add-key');
    },

    login(): void {
      this.$store.commit(StoreTypes.mutations.SET_SELECTED_TOKEN, this.token);
      this.$emit('token-login');
    },

    logout(): void {
      this.$store.commit(StoreTypes.mutations.SET_SELECTED_TOKEN, this.token);
      this.$emit('token-logout');
    },

    tokenNameClick(): void {
      this.isExpanded(this.token.id)
        ? this.descClose(this.token.id)
        : this.descOpen(this.token.id);
    },

    tokenClick(token: Token): void {
      /*this.$router.push({
        name: RouteName.Token,
        params: { id: token.id },
      });
      */
    },

    keyClick(key: Key): void {
      /* this.$router.push({
        name: RouteName.Key,
        params: { id: key.id },
      });
      */
    },

    getSignKeys(keys: Key[]): Key[] {
      return keys.filter((key: Key) => key.usage === KeyUsageType.SIGNING);
    },

    descClose(tokenId: string) {
      this.$store.commit(StoreTypes.mutations.SET_TOKEN_HIDDEN, tokenId);
    },
    descOpen(tokenId: string) {
      this.$store.commit(StoreTypes.mutations.SET_TOKEN_EXPANDED, tokenId);
    },
    /* isExpanded(tokenId: string) {
      return this.$store.getters.TOKEN_EXPANDED(tokenId);
    }, */
    fetchData(): void {
      // Fetch tokens from backend
      this.$emit('refresh-list');
    },
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/tables';
@import '~styles/colors';

.token-logging-button {
  display: inline-flex;
}

.token-status-indicator {
  font-weight: bold;
  text-align: center;

  &.label {
    margin-right: 24px;
    text-decoration: none;
  }

  &.logged-in {
    color: $XRoad-Black100;
  }

  &.logged-out {
    color: $XRoad-Purple100;
  }
}

.clickable-link {
  color: $XRoad-Purple100;
  cursor: pointer;
}

.expandable {
  margin-bottom: 10px;
}

.action-slot-wrapper {
  display: flex;
  flex-direction: row;
  align-items: center;
}

.token-status {
  display: flex;
  flex-direction: row;
  height: 100%;
  align-items: center;
  font-weight: 700;
}

.button-wrap {
  margin-top: 10px;
  padding-left: 16px;
  padding-right: 16px;
  width: 100%;
  display: flex;
  justify-content: space-between;
}

.button-spacing {
  margin-left: 20px;
}

.keys-table {
  transform-origin: top;
  transition: transform 0.4s ease-in-out;
}

.button-icon {
  margin-top: -14px; // fix for icon position
}
</style>
