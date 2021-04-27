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
  <div class="mt-3">
    <div class="title-row">
      <div class="xrd-view-title">{{ $t('tab.keys.apiKey') }}</div>
      <div>
        <help-button
          helpImage="api_keys.png"
          helpTitle="keys.helpTitleApi"
          helpText="keys.helpTextApi"
        ></help-button>
      </div>
    </div>

    <div class="details-view-tools">
      <xrd-button
        v-if="canCreateApiKey"
        class="button-spacing"
        outlined
        data-test="api-key-create-key-button"
        @click="createApiKey"
        >{{ $t('apiKey.createApiKey.button') }}</xrd-button
      >
    </div>

    <v-card flat>
      <table class="xrd-table" data-test="api-key-keys-table">
        <thead>
          <tr class="keytable-header">
            <td>&nbsp;</td>
            <td>{{ $t('apiKey.table.header.id') }}</td>
            <td>{{ $t('apiKey.table.header.roles') }}</td>
            <td>&nbsp;</td>
          </tr>
        </thead>
        <tbody>
          <api-key-row
            v-for="apiKey in apiKeys"
            :key="apiKey.id"
            :api-key="apiKey"
            @change="loadKeys"
          />
        </tbody>
      </table>
    </v-card>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import * as api from '@/util/api';
import { RouteName, Permissions } from '@/global';
import { ApiKey } from '@/global-types';
import ApiKeyRow from '@/views/KeysAndCertificates/ApiKey/ApiKeyRow.vue';
import HelpButton from '../HelpButton.vue';

export default Vue.extend({
  components: {
    ApiKeyRow,
    HelpButton,
  },
  computed: {
    canCreateApiKey(): boolean {
      return this.$store.getters.hasPermission(Permissions.CREATE_API_KEY);
    },
  },
  data() {
    return {
      apiKeys: new Array<ApiKey>(),
    };
  },
  methods: {
    loadKeys(): void {
      if (this.$store.getters.hasPermission(Permissions.VIEW_API_KEYS)) {
        api
          .get<ApiKey[]>('/api-keys')
          .then((resp) => (this.apiKeys = resp.data))
          .catch((error) => this.$store.dispatch('showError', error));
      }
    },
    createApiKey(): void {
      this.$router.push({
        name: RouteName.CreateApiKey,
      });
    },
  },
  created(): void {
    this.loadKeys();
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/detail-views';
@import '../../../assets/tables';
@import '../../../assets/colors';

.keytable-header {
  font-weight: 500;
  color: $XRoad-Black;
}

.title-row {
  display: flex;
  flex-direction: row;
  align-items: flex-end;
}
</style>
