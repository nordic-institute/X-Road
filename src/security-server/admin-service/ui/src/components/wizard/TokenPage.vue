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
  <XrdWizardStep sub-title="wizard.token.info">
    <XrdFormBlock>
      <v-text-field
        v-model="search"
        data-test="token-search-input"
        class="xrd w-50 mb-6"
        density="compact"
        prepend-inner-icon="search"
        single-line
        hide-details
        autofocus
        :label="$t('wizard.token.tokenName')"
      />

      <v-radio-group v-model="tokenGroup">
        <v-data-table
          class="xrd"
          hide-default-footer
          :headers="headers"
          :items="filteredTokens"
        >
          <template #item.radio="{ item }">
            <v-radio
              data-test="token-radio-button"
              class="xrd"
              :value="item"
              :disabled="!item.logged_in"
            />
          </template>
          <template #item.name="{ value: name }">
            {{ `Token ${name}` }}
          </template>
          <template #item.actions="{ item }">
            <XrdBtn
              v-if="item.logged_in"
              data-test="token-logout-button"
              variant="text"
              text="wizard.token.loggedIn"
              disabled
            />
            <XrdBtn
              v-else
              data-test="token-login-button"
              variant="text"
              text="keys.logIn"
              :disabled="!item.available"
              @click="confirmLogin(item)"
            />
          </template>
        </v-data-table>
      </v-radio-group>
    </XrdFormBlock>
    <TokenLoginDialog
      :dialog="loginDialog"
      @cancel="loginDialog = false"
      @save="tokenLogin"
    />

    <template #footer>
      <XrdBtn
        data-test="cancel-button"
        variant="outlined"
        text="action.cancel"
        :disabled="!disableDone"
        @click="cancel"
      />
      <v-spacer />

      <XrdBtn
        data-test="previous-button"
        variant="outlined"
        class="mr-2"
        text="action.previous"
        @click="previous"
      />

      <XrdBtn
        data-test="next-button"
        text="action.next"
        :disabled="disableNext"
        @click="done"
      />
    </template>
  </XrdWizardStep>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import TokenLoginDialog from '@/components/token/TokenLoginDialog.vue';
import { Token } from '@/openapi-types';
import { mapActions, mapState } from 'pinia';

import { useTokens } from '@/store/modules/tokens';
import { useCsr } from '@/store/modules/certificateSignRequest';
import {
  XrdWizardStep,
  XrdFormBlock,
  XrdBtn,
  useNotifications,
} from '@niis/shared-ui';
import { DataTableHeader } from 'vuetify/lib/components/VDataTable/types';

export default defineComponent({
  components: {
    TokenLoginDialog,
    XrdWizardStep,
    XrdFormBlock,
    XrdBtn,
  },
  emits: ['cancel', 'previous', 'done'],
  setup() {
    const { addError } = useNotifications();

    return { addError };
  },
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

    disableNext() {
      return !this.tokenGroup;
    },
    headers() {
      return [
        { title: '', key: 'radio' },
        { title: this.$t('keys.name') as string, key: 'name', align: 'start' },
        { title: '', key: 'actions' },
      ] as DataTableHeader[];
    },
  },
  created() {
    this.fetchData();
  },
  methods: {
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
        .catch((error) => this.addError(error));
    },
  },
});
</script>

<style lang="scss" scoped></style>
