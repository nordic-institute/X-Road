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
    <div class="header-row">
      <div class="title-search">
        <div class="xrd-view-title">Signing Keys</div>
      </div>
    </div>

    <XrdEmptyPlaceholder
      :data="tokens"
      :loading="tokensLoading"
      :no-items-text="$t('noData.noTokens')"
      skeleton-type="table-heading"
    />

    <token-expandable
      v-for="token in tokens"
      :key="token.id"
      :token="token"
      @token-login="fetchData"
      @token-logout="fetchData"
      @add-key="addKey"
    />

    <!-- Internal configuration -->
    <div class="header-row mt-7">
      <div class="xrd-view-title">{{ title }}</div>
    </div>

    <!-- Anchor -->
    <configuration-anchor :configuration-type="configurationType" />

    <!-- Download URL -->
    <configuration-download-url :configuration-type="configurationType" />

    <!-- Configuration parts -->
    <configuration-parts-list :configuration-type="configurationType" />
  </div>
</template>

<script lang="ts">
/**
 * View for 'backup and restore' tab
 */
import Vue from 'vue';
import { mapActions, mapState } from 'pinia';
import ConfigurationAnchor from './ConfigurationAnchor.vue';
import ConfigurationPartsList from './ConfigurationPartsList.vue';
import ConfigurationDownloadUrl from './ConfigurationDownloadUrl.vue';
import { tokenStore } from '@/store/modules/tokens';
import { ConfigurationType } from '@/openapi-types';
import { Prop } from 'vue/types/options';
import TokenExpandable from '@/components/tokens/TokenExpandable.vue';

export default Vue.extend({
  components: {
    ConfigurationDownloadUrl,
    ConfigurationAnchor,
    ConfigurationPartsList,
    TokenExpandable,
  },
  props: {
    title: {
      type: String,
      required: true,
    },
    configurationType: {
      type: String as Prop<ConfigurationType>,
      required: true,
    },
  },
  data() {
    return {
      tokensLoading: false,
      creatingBackup: false,
      uploadingBackup: false,
      needsConfirmation: false,
      uploadedFile: null as File | null,
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

    addKey(): void {
      // TODO
    },
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/colors';

.card-title {
  font-size: 12px;
  text-transform: uppercase;
  color: $XRoad-Black70;
  font-weight: bold;
  padding-top: 5px;
  padding-bottom: 5px;
}

.card-corner-button {
  display: flex;
}

.card-top {
  padding-top: 15px;
  margin-bottom: 10px;
  width: 100%;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}

.card-main-title {
  color: $XRoad-Black100;
  font-style: normal;
  font-weight: bold;
  font-size: 18px;
  line-height: 24px;
  margin-left: 16px;
}

.internal-conf-icon {
  margin-right: 15px;
  color: $XRoad-Purple100;
}

.td-align-right {
  text-align: right;
}
</style>
