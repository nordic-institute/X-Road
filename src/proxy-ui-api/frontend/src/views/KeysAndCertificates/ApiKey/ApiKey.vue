<template>
  <div class="wrapper">
    <div class="details-view-tools">
      <large-button
        class="button-spacing"
        outlined
        data-test="api-key-create-key-button"
        @click="createApiKey"
        >{{ $t('apiKey.createApiKey.button') }}</large-button
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
import LargeButton from '@/components/ui/LargeButton.vue';
import * as api from '@/util/api';
import { RouteName } from '@/global';
import { ApiKey } from '@/global-types';
import ApiKeyRow from '@/views/KeysAndCertificates/ApiKey/ApiKeyRow.vue';

export default Vue.extend({
  components: {
    LargeButton,
    ApiKeyRow,
  },
  data() {
    return {
      apiKeys: new Array<ApiKey>(),
    };
  },
  methods: {
    loadKeys(): void {
      api
        .get<ApiKey[]>('/api-keys')
        .then((resp) => (this.apiKeys = resp.data))
        .catch((error) => this.$store.dispatch('showError', error));
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
</style>
