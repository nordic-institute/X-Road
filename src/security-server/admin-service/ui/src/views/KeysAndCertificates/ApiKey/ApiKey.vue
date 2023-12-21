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
  <div class="mt-3" data-test="api-keys-view">
    <div class="xrd-table-toolbar mt-0 pl-0">
      <div class="xrd-title-search">
        <div class="xrd-view-title">{{ $t('tab.keys.apiKey') }}</div>

        <help-button
          :help-image="helpImg"
          help-title="keys.helpTitleApi"
          help-text="keys.helpTextApi"
        ></help-button>
      </div>
      <xrd-button
        v-if="canCreateApiKey"
        data-test="api-key-create-key-button"
        @click="createApiKey()"
      >
        <xrd-icon-base class="xrd-large-button-icon"
          ><XrdIconAdd
        /></xrd-icon-base>
        {{ $t('apiKey.createApiKey.title') }}</xrd-button
      >
    </div>

    <!-- Table -->
    <v-data-table
      :loading="loading"
      :headers="headers"
      :items="apiKeys"
      :search="search"
      :must-sort="true"
      :items-per-page="-1"
      class="elevation-0 data-table"
      item-key="id"
      :loader-height="2"
      hide-default-footer
    >
      <template #[`item.id`]="{ item }">
        <div class="server-code">
          <xrd-icon-base class="mr-4"><XrdIconKey /></xrd-icon-base>
          {{ item.id }}
        </div>
      </template>

      <template #[`item.roles`]="{ item }">
        <span :data-test="`api-key-row-${item.id}-roles`">
          {{ $filters.commaSeparate(translateRoles(item.roles)) }}
        </span>
      </template>

      <template #[`item.button`]="{ item }">
        <div class="button-wrap">
          <xrd-button
            v-if="canEdit"
            text
            :data-test="`api-key-row-${item.id}-edit-button`"
            :outlined="false"
            @click="editKey(item)"
            >{{ $t('action.edit') }}</xrd-button
          >

          <xrd-button
            v-if="canRevoke"
            text
            :data-test="`api-key-row-${item.id}-revoke-button`"
            :outlined="false"
            @click="showRevokeDialog(item)"
            >{{ $t('apiKey.table.action.revoke.button') }}</xrd-button
          >
        </div>
      </template>

      <template #bottom>
        <div class="custom-footer"></div>
      </template>
    </v-data-table>

    <!-- Edit dialog -->
    <xrd-simple-dialog
      v-if="showEditDialog"
      :dialog="showEditDialog"
      save-button-text="action.save"
      :disable-save="selectedRoles.length === 0"
      @save="save"
      @cancel="showEditDialog = false"
    >
      <template #title>
        <span
          class="text-h5"
          :data-test="`api-key-row-${selectedKey?.id}-edit-dialog-title`"
        >
          {{
            $t('apiKey.table.action.edit.dialog.title', { id: selectedKey?.id })
          }}
        </span>
      </template>
      <template #content>
        <div :data-test="`api-key-row-${selectedKey?.id}-edit-dialog-content`">
          <v-row class="mt-12">
            <v-col>
              {{ $t('apiKey.table.action.edit.dialog.message') }}
            </v-col>
          </v-row>
          <v-row v-for="role in rolesToEdit" :key="role" no-gutters>
            <v-col class="checkbox-wrapper">
              <v-checkbox
                v-model="selectedRoles"
                height="10px"
                :value="role"
                :data-test="`role-${role}-checkbox`"
              >
                <template #label>
                  <span>{{ $t(`apiKey.role.${role}`) }}</span>
                  <span v-if="!hasRole(role)" class="remove-only-role">
                    &nbsp;{{ $t('apiKey.edit.roleRemoveOnly') }}
                  </span>
                </template>
              </v-checkbox>
            </v-col>
          </v-row>
        </div>
      </template>
    </xrd-simple-dialog>

    <!-- Confirm revoke dialog -->
    <xrd-confirm-dialog
      v-if="confirmRevoke"
      :data-test="`api-key-row-${selectedKey?.id}-revoke-confirmation`"
      :dialog="confirmRevoke"
      title="apiKey.table.action.revoke.confirmationDialog.title"
      text="apiKey.table.action.revoke.confirmationDialog.message"
      :data="{ id: selectedKey?.id }"
      :loading="removingApiKey"
      @cancel="confirmRevoke = false"
      @accept="revokeApiKey"
    />
  </div>
</template>

<script lang="ts">
/**
 * View for 'API keys' tab
 */
import { defineComponent } from 'vue';

import { ApiKey } from '@/global-types';
import HelpButton from '../HelpButton.vue';
import { RouteName, Roles, Permissions } from '@/global';
import * as api from '@/util/api';
import { mapActions, mapState } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { useUser } from '@/store/modules/user';
import { DataTableHeader } from '@/ui-types';
import { VDataTable } from 'vuetify/labs/VDataTable';
import helpImg from '@/assets/api_keys.png';

export default defineComponent({
  components: {
    VDataTable,
    HelpButton,
  },
  data() {
    return {
      apiKeys: new Array<ApiKey>(),
      search: '' as string,
      loading: false,
      showOnlyPending: false,
      selectedKey: undefined as undefined | ApiKey,
      selectedRoles: [] as string[],
      showEditDialog: false,
      confirmRevoke: false,
      savingChanges: false,
      removingApiKey: false,
      rolesToEdit: [] as string[],
    };
  },
  computed: {
    ...mapState(useUser, ['hasPermission', 'hasRole']),
    helpImg(): string {
      return helpImg;
    },
    canCreateApiKey(): boolean {
      return this.hasPermission(Permissions.CREATE_API_KEY);
    },
    canEdit(): boolean {
      return this.hasPermission(Permissions.UPDATE_API_KEY);
    },
    canRevoke(): boolean {
      return this.hasPermission(Permissions.REVOKE_API_KEY);
    },
    headers(): DataTableHeader[] {
      return [
        {
          title: this.$t('apiKey.table.header.id') as string,
          align: 'start',
          key: 'id',
        },
        {
          title: this.$t('apiKey.table.header.roles') as string,
          align: 'start',
          key: 'roles',
        },

        {
          title: '',
          key: 'button',
          sortable: false,
        },
      ];
    },
  },
  created(): void {
    this.loadKeys();
  },

  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    loadKeys(): void {
      if (this.hasPermission(Permissions.VIEW_API_KEYS)) {
        this.loading = true;
        api
          .get<ApiKey[]>('/api-keys')
          .then((resp) => (this.apiKeys = resp.data))
          .catch((error) => this.showError(error))
          .finally(() => (this.loading = false));
      }
    },
    editKey(apiKey: ApiKey): void {
      this.selectedKey = apiKey;
      this.selectedRoles = [...this.selectedKey.roles];
      this.rolesToEdit = Roles.filter(
        (role) => this.selectedRoles.includes(role) || this.hasRole(role),
      );
      this.showEditDialog = true;
    },
    showRevokeDialog(apiKey: ApiKey): void {
      this.selectedKey = apiKey;
      this.confirmRevoke = true;
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
    revokeApiKey() {
      if (!this.selectedKey) return;

      this.removingApiKey = true;
      return api
        .remove<ApiKey>(
          `/api-keys/${api.encodePathParameter(this.selectedKey.id)}`,
        )
        .then((response) => {
          const key = response.data;
          this.showSuccess(
            this.$t('apiKey.table.action.revoke.success', {
              id: key.id,
            }),
          );
        })
        .catch((error) => this.showError(error))
        .finally(() => {
          this.confirmRevoke = false;
          this.removingApiKey = false;
          this.loadKeys();
        });
    },
    save() {
      if (!this.selectedKey) return;
      this.savingChanges = true;
      return api
        .put<ApiKey>(
          `/api-keys/${api.encodePathParameter(this.selectedKey.id)}`,
          this.selectedRoles,
        )
        .then((response) => {
          const key = response.data as ApiKey;
          this.showSuccess(
            this.$t('apiKey.table.action.edit.success', {
              id: key.id,
            }),
          );
        })
        .catch((error) => this.showError(error))
        .finally(() => {
          this.savingChanges = false;
          this.showEditDialog = false;
          this.loadKeys();
        });
    },
  },
});
</script>

<style lang="scss" scoped>
@import '@/assets/tables';

.button-wrap {
  width: 100%;
  display: flex;
  justify-content: flex-end;
}

.server-code {
  font-weight: 600;
  font-size: 14px;
}

.remove-only-role {
  font-style: italic;
}
</style>
