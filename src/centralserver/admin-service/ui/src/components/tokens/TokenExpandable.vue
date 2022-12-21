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
        <TokenLoggingButton
          class="token-logging-button"
          :token="token"
          @token-logout="$emit('token-logout')"
          @token-login="$emit('token-login')"
        />
      </div>
    </template>

    <template #content>
      <div>
        <div class="button-wrap mb-6">
          <div>
            {{ $t('tokens.keysInfoMessage') }}
          </div>

          <xrd-button
            v-if="canAddKey"
            outlined
            :disabled="!token.logged_in"
            data-test="token-add-key-button"
            @click="addKey()"
          >
            <xrd-icon-base class="xrd-large-button-icon">
              <XrdIconAdd />
            </xrd-icon-base>
            {{ $t('keys.addKey') }}
          </xrd-button>
        </div>

        <!-- SIGN keys table -->
        <div v-if="token.configuration_signing_keys">
          <keys-table
            v-if="signKeysOpen"
            class="keys-table"
            :keys="token.configuration_signing_keys"
          />
        </div>
      </div>
    </template>
  </xrd-expandable>
</template>

<script lang="ts">
import Vue from 'vue';
import { Prop } from 'vue/types/options';
import { Colors } from '@/global';
import { mapActions, mapState } from 'pinia';
import { tokenStore } from '@/store/modules/tokens';
import { Token } from '@/openapi-types';
import KeysTable from '@/components/tokens/KeysTable.vue';
import TokenLoggingButton from '@/components/tokens/TokenLoggingButton.vue';

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
    ...mapState(tokenStore, {
      isExpanded: 'tokenExpanded',
    }),
    canAddKey(): boolean {
      return true;
      // Add permission check from store
    },
    tokenStatusClass(): string {
      return this.token.logged_in ? 'logged-out' : 'logged-in';
    },
    tokenStatusColor(): string {
      return this.token.logged_in ? this.colors.Black100 : this.colors.Purple20;
    },
  },
  methods: {
    ...mapActions(tokenStore, [
      'setSelectedToken',
      'setTokenHidden',
      'setTokenExpanded',
    ]),
    addKey(): void {
      this.setSelectedToken(this.token);
      //this.$store.commit(StoreTypes.mutations.SET_SELECTED_TOKEN, this.token);
      this.$emit('add-key');
    },

    tokenNameClick(): void {
      this.isExpanded(this.token.id)
        ? this.descClose(this.token.id)
        : this.descOpen(this.token.id);
    },

    descClose(tokenId: string) {
      this.setTokenHidden(tokenId);
    },
    descOpen(tokenId: string) {
      this.setTokenExpanded(tokenId);
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
