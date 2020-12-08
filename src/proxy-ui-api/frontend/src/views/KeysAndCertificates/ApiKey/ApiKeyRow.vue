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
  <tr :data-test="`api-key-row-${apiKey.id}`">
    <td>
      <icon-base icon-name="key" class="icon"><icon-key /></icon-base>
    </td>
    <td>{{ apiKey.id }}</td>
    <td>{{ translateRoles(apiKey.roles) | commaSeparate }}</td>
    <td class="text-right">
      <large-button
        v-if="canEdit"
        @click="openEditDialog"
        text
        :disabled="removingApiKey"
        :data-test="`api-key-row-${apiKey.id}-edit-button`"
        >{{ $t('apiKey.table.action.edit.button') }}</large-button
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
      <large-button
        v-if="canRevoke"
        class="ml-5"
        text
        :data-test="`api-key-row-${apiKey.id}-revoke-button`"
        :loading="removingApiKey"
        @click="confirmRevoke = true"
        >{{ $t('apiKey.table.action.revoke.button') }}</large-button
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
import { Roles, Permissions } from '@/global';
import { encodePathParameter } from '@/util/api';
import IconBase from '@/components/ui/icons/IconBase.vue';
import IconKey from '@/components/ui/icons/IconKey.vue';

export default Vue.extend({
  name: 'ApiKeyRow',
  components: {
    IconKey,
    IconBase,
  },
  props: {
    apiKey: {
      type: Object as Prop<ApiKey>,
      required: true,
    },
  },
  computed: {
    canEdit(): boolean {
      return this.$store.getters.hasPermission(Permissions.UPDATE_API_KEY);
    },
    canRevoke(): boolean {
      return this.$store.getters.hasPermission(Permissions.REVOKE_API_KEY);
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
        .remove(`/api-keys/${encodePathParameter(this.apiKey.id)}`)
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
        .put(
          `/api-keys/${encodePathParameter(this.apiKey.id)}`,
          this.selectedRoles,
        )
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
