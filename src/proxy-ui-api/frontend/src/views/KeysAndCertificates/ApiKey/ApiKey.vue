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
      <table class="xrd-table">
        <thead>
          <tr class="keytable-header">
            <td>&nbsp;</td>
            <td>{{ $t('apiKey.table.header.id') }}</td>
            <td>{{ $t('apiKey.table.header.roles') }}</td>
            <td>&nbsp;</td>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="apiKey in apiKeys"
            :key="apiKey.id"
            class="grey--text text--darken-1"
          >
            <td><i class="icon-xrd_key icon"></i></td>
            <td>{{ apiKey.id }}</td>
            <td>{{ translateRoles(apiKey.roles) | commaSeparate }}</td>
            <td class="actions-column">
              <small-button>{{ $t('apiKey.table.actions.edit') }}</small-button>
              <small-button class="button-spacing">{{
                $t('apiKey.table.actions.revoke')
              }}</small-button>
            </td>
          </tr>
        </tbody>
      </table>
    </v-card>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import LargeButton from '@/components/ui/LargeButton.vue';
import * as api from '@/util/api';
import SmallButton from '@/components/ui/SmallButton.vue';
import { RouteName } from '@/global';
import { ApiKey } from '@/global-types';

export default Vue.extend({
  components: {
    LargeButton,
    SmallButton,
  },
  data() {
    return {
      apiKeys: new Array<ApiKey>(),
    };
  },
  methods: {
    loadKeys(): void {
      api
        .get('/api-keys')
        .then((resp) => (this.apiKeys = resp.data))
        .catch((error) => this.$store.dispatch('showError', error));
    },
    createApiKey(): void {
      this.$router.push({
        name: RouteName.CreateApiKey,
      });
    },
    translateRoles(roles: string[]): string[] {
      return !roles
        ? []
        : roles.map((role) => this.$t(`apiKey.role.${role}`) as string);
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

.actions-column {
  display: flex;
  align-items: center;
  justify-content: flex-end;
}
</style>
