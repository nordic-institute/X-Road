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
  <div>
    <XrdEmptyPlaceholder
      :data="tokens"
      :loading="tokensLoading"
      :no-items-text="$t('noData.noTokens')"
      skeleton-type="table-heading"
    />

    <template v-if="!tokensLoading">
      <token-expandable
        v-for="token in tokens"
        :key="token.id"
        :token="token"
        :configuration-type="configurationType"
        :loading-keys="loadingKeys"
        @token-login="fetchData"
        @token-logout="fetchData"
        @update-keys="fetchKeys"
      />
    </template>
  </div>
</template>

<script lang="ts">
/**
 * Table component for an array of keys
 */
import Vue from 'vue';
import { Prop } from 'vue/types/options';
import { ConfigurationType } from '@/openapi-types';
import { mapActions, mapState } from 'pinia';
import { tokenStore } from '@/store/modules/tokens';
import TokenExpandable from '@/components/tokens/TokenExpandable.vue';

export default Vue.extend({
  components: { TokenExpandable },
  props: {
    configurationType: {
      type: String as Prop<ConfigurationType>,
      required: true,
    },
  },
  data() {
    return {
      tokensLoading: false,
      loadingKeys: false,
    };
  },
  computed: {
    ...mapState(tokenStore, { tokens: 'getSortedTokens' }),
  },
  created() {
    this.fetchData();
  },
  methods: {
    ...mapActions(tokenStore, ['fetchTokens']),

    fetchData(): void {
      this.tokensLoading = true;
      this.fetchTokens().finally(() => (this.tokensLoading = false));
    },
    fetchKeys(action: string): void {
      this.loadingKeys = true;
      this.fetchTokens().finally(() => (this.loadingKeys = false));
      this.$emit('update-keys', action);
    },
  },
});
</script>

<style lang="scss" scoped></style>
