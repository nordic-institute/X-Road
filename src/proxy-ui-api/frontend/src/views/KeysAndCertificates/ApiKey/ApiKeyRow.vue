<template>
  <tr class="grey--text text--darken-1" :data-test="`api-key-row-${apiKey.id}`">
    <td><i class="icon-xrd_key icon"></i></td>
    <td>{{ apiKey.id }}</td>
    <td>{{ translateRoles(apiKey.roles) | commaSeparate }}</td>
    <td class="text-right">
      <small-button
        :disabled="removingApiKey"
        :data-test="`api-key-row-${apiKey.id}-edit-button`"
        >{{ $t('apiKey.table.action.edit') }}</small-button
      >
      <small-button
        class="ml-5"
        :data-test="`api-key-row-${apiKey.id}-revoke-button`"
        :loading="removingApiKey"
        @click="confirmRevoke = true"
        >{{ $t('apiKey.table.action.revoke.button') }}</small-button
      >
      <confirm-dialog
        :data-test="`api-key-row-${apiKey.id}-revoke-confirmation`"
        :dialog="confirmRevoke"
        title="apiKey.table.action.revoke.confirmationDialog.title"
        text="apiKey.table.action.revoke.confirmationDialog.message"
        :data="{ id: apiKey.id }"
        @cancel="confirmRevoke = false"
        @accept="revokeApiKey"
      />
    </td>
  </tr>
</template>

<script lang="ts">
import Vue from 'vue';
import * as api from '@/util/api';
import { Prop } from 'vue/types/options';
import { ApiKey } from '@/global-types';
import SmallButton from '@/components/ui/SmallButton.vue';
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue';
export default Vue.extend({
  name: 'ApiKeyRow',
  components: {
    SmallButton,
    ConfirmDialog,
  },
  props: {
    apiKey: {
      type: Object as Prop<ApiKey>,
      required: true,
    },
  },
  data() {
    return {
      confirmRevoke: false,
      removingApiKey: false,
    };
  },
  methods: {
    translateRoles(roles: string[]): string[] {
      return !roles
        ? []
        : roles.map((role) => this.$t(`apiKey.role.${role}`) as string);
    },
    async revokeApiKey() {
      this.removingApiKey = true;
      this.confirmRevoke = false;
      api
        .remove(`/api-keys/${this.apiKey.id}`)
        .then(() => {
          this.$store.dispatch(
            'showSuccessRaw',
            this.$t('apiKey.table.action.revoke.success', {
              id: this.apiKey.id,
            }),
          );
          this.$emit('change');
        })
        .catch((error) => this.$store.dispatch('showError', error))
        .finally(() => (this.removingApiKey = false));
    },
  },
});
</script>

<style scoped lang="scss">
@import '../../../assets/tables';
</style>
