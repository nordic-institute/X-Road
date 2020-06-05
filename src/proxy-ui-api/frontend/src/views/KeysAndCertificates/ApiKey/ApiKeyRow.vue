<template>
  <tr class="grey--text text--darken-1" :data-test="`api-key-row-${apiKey.id}`">
    <td><i class="icon-xrd_key icon"></i></td>
    <td>{{ apiKey.id }}</td>
    <td>{{ translateRoles(apiKey.roles) | commaSeparate }}</td>
    <td class="text-right">
      <small-button
        @click="openEditDialog"
        :disabled="removingApiKey"
        :data-test="`api-key-row-${apiKey.id}-edit-button`"
        >{{ $t('apiKey.table.action.edit.button') }}</small-button
      >
      <simpleDialog
        :dialog="showEditDialog"
        @save="save"
        @cancel="showEditDialog = false"
        save-button-text="action.save"
        :disable-save="selectedRoles.length === 0"
      >
        <span
          slot="title"
          class="headline"
          :data-test="`api-key-row-${apiKey.id}-edit-dialog-title`"
        >
          {{ $t('apiKey.table.action.edit.dialog.title', { id: apiKey.id }) }}
        </span>
        <div
          slot="content"
          :data-test="`api-key-row-${apiKey.id}-edit-dialog-content`"
        >
          <v-row class="mt-12">
            <v-col>
              {{ $t('apiKey.table.action.edit.dialog.message') }}
            </v-col>
          </v-row>
          <v-row no-gutters v-for="role in roles" :key="role">
            <v-col class="checkbox-wrapper">
              <v-checkbox
                v-model="selectedRoles"
                height="10px"
                :value="role"
                :label="$t(`apiKey.role.${role}`)"
              />
            </v-col>
          </v-row>
        </div>
      </simpleDialog>
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
import SimpleDialog from '@/components/ui/SimpleDialog.vue';
import { Roles } from '@/global';
export default Vue.extend({
  name: 'ApiKeyRow',
  components: {
    SmallButton,
    ConfirmDialog,
    SimpleDialog,
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
      savingChanges: false,
      removingApiKey: false,
      showEditDialog: false,
      roles: Roles,
      selectedRoles: [...this.apiKey.roles] as string[],
    };
  },
  methods: {
    translateRoles(roles: string[]): string[] {
      return !roles
        ? []
        : roles.map((role) => this.$t(`apiKey.role.${role}`) as string);
    },
    openEditDialog(): void {
      this.selectedRoles = [...this.apiKey.roles];
      this.showEditDialog = true;
    },
    async revokeApiKey() {
      this.removingApiKey = true;
      this.confirmRevoke = false;
      return api
        .remove(`/api-keys/${this.apiKey.id}`)
        .then(() => {
          this.$store.dispatch(
            'showSuccessRaw',
            this.$t('apiKey.table.action.revoke.success', {
              id: this.apiKey.id,
            }),
          );
        })
        .catch((error) => this.$store.dispatch('showError', error))
        .finally(() => {
          this.removingApiKey = false;
          this.$emit('change');
        });
    },
    async save() {
      this.savingChanges = true;
      return api
        .put(`/api-keys/${this.apiKey.id}`, this.selectedRoles)
        .then(() => {
          this.$store.dispatch(
            'showSuccessRaw',
            this.$t('apiKey.table.action.edit.success', { id: this.apiKey.id }),
          );
        })
        .catch((error) => this.$store.dispatch('showError', error))
        .finally(() => {
          this.savingChanges = false;
          this.showEditDialog = false;
          this.$emit('change');
        });
    },
  },
});
</script>

<style scoped lang="scss">
@import '../../../assets/tables';
</style>
