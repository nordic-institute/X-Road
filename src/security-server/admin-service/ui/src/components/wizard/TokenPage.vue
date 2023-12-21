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
  <div class="wizard-token-step-form-content">
    {{ $t('wizard.token.info') }}
    <v-text-field
      v-model="search"
      :label="$t('wizard.token.tokenName')"
      single-line
      hide-details
      class="search-input"
      data-test="token-search-input"
      autofocus
      variant="underlined"
      density="compact"
      append-inner-icon="mdi-magnify"
    >
    </v-text-field>

    <v-radio-group v-model="tokenGroup">
      <div v-for="token in filteredTokens" :key="token.id" class="radio-row">
        <v-radio
          :label="`Token ${token.name}`"
          :value="token"
          :disabled="!token.logged_in"
          data-test="token-radio-button"
        ></v-radio>
        <div>
          <xrd-button
            v-if="!token.logged_in"
            :disabled="!token.available"
            :outlined="false"
            text
            data-test="token-login-button"
            @click="confirmLogin(token)"
            >{{ $t('keys.logIn') }}
          </xrd-button>
          <xrd-button
            v-if="token.logged_in"
            text
            :outlined="false"
            disabled
            data-test="token-logout-button"
            >{{ $t('wizard.token.loggedIn') }}
          </xrd-button>
        </div>
      </div>
    </v-radio-group>
  </div>

  <div class="button-footer">
    <xrd-button
      outlined
      :disabled="!disableDone"
      data-test="cancel-button"
      @click="cancel"
      >{{ $t('action.cancel') }}
    </xrd-button>

    <xrd-button
      outlined
      class="previous-button"
      data-test="previous-button"
      @click="previous"
      >{{ $t('action.previous') }}
    </xrd-button>

    <xrd-button :disabled="disableNext" data-test="next-button" @click="done"
      >{{ $t('action.next') }}
    </xrd-button>
  </div>
  <TokenLoginDialog
    :dialog="loginDialog"
    @cancel="loginDialog = false"
    @save="tokenLogin"
  />
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import TokenLoginDialog from '@/components/token/TokenLoginDialog.vue';
import { Token } from '@/openapi-types';
import { mapActions, mapState } from 'pinia';

import { useNotifications } from '@/store/modules/notifications';
import { useTokens } from '@/store/modules/tokens';
import { useCsr } from '@/store/modules/certificateSignRequest';

export default defineComponent({
  components: {
    TokenLoginDialog,
  },
  emits: ['cancel', 'previous', 'done'],
  data() {
    return {
      search: undefined as string | undefined,
      disableDone: true,
      tokenGroup: undefined as Token | undefined,
      loginDialog: false,
    };
  },
  computed: {
    ...mapState(useTokens, ['tokens', 'tokensFilteredByName']),

    filteredTokens() {
      return this.tokensFilteredByName(this.search);
    },

    disableSelection() {
      if (this.tokens.length === 1) {
        return true;
      }
      return false;
    },

    disableNext() {
      if (this.tokenGroup) {
        return false;
      }
      return true;
    },
  },
  created() {
    this.fetchData();
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    ...mapActions(useCsr, ['setCsrTokenId']),
    ...mapActions(useTokens, ['setSelectedToken', 'fetchTokens']),
    cancel(): void {
      this.$emit('cancel');
    },
    previous(): void {
      this.$emit('previous');
    },
    done(): void {
      if (!this.tokenGroup?.id) {
        return;
      }
      this.setCsrTokenId(this.tokenGroup.id);
      this.$emit('done');
    },
    confirmLogin(token: Token): void {
      this.setSelectedToken(token);
      this.loginDialog = true;
    },
    tokenLogin(): void {
      this.fetchData();
      this.loginDialog = false;
    },
    fetchData(): void {
      // Fetch tokens from backend
      this.fetchTokens()
        .then(() => {
          // Preselect the token if there is only one
          if (
            this.filteredTokens.length === 1 &&
            this.filteredTokens[0].logged_in
          ) {
            this.tokenGroup = this.filteredTokens[0];
          }
        })
        .catch((error) => {
          this.showError(error);
        });
    },
  },
});
</script>

<style lang="scss" scoped>
@import '@/assets/wizards';

.wizard-token-step-form-content {
  padding: 30px;
}

.search-input {
  width: 300px;
}

.radio-row {
  display: flex;
  width: 100%;
  justify-content: space-between;
  padding-right: 10px;
  flex-direction: row;
  flex-wrap: wrap;
  border-bottom: 1px solid $XRoad-WarmGrey30;
  padding-left: 12px;
  padding-bottom: 5px;
  padding-top: 5px;
}
</style>
