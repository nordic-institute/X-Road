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
  <div data-test="api-keys-view">
    <!-- Title and button -->
    <div class="table-toolbar align-fix mt-0 pl-0">
      <div class="xrd-view-title align-fix">
        {{ $t('tab.settings.apiKeys') }}
      </div>

      <xrd-button data-test="api-key-create-key-button" @click="createApiKey()">
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
      :sort-by="['id']"
      :must-sort="true"
      :items-per-page="-1"
      class="elevation-0 data-table"
      item-key="id"
      :loader-height="2"
      hide-default-footer
    >
      <template #[`item.id`]="{ item }">
        <div data-test="api-key-id" class="server-code">
          <xrd-icon-base class="mr-4"><XrdIconKey /></xrd-icon-base>
          {{ item.id }}
        </div>
      </template>

      <template #[`item.roles`]="{ item }">
        <span :data-test="`api-key-row-${item.id}-roles`">{{
          translateRoles(item.roles) | commaSeparate
        }}</span>
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

      <template #footer>
        <div class="custom-footer"></div>
      </template>
    </v-data-table>

    <!-- Edit dialog -->
    <xrd-simple-dialog
      v-if="showEditDialog"
      :dialog="showEditDialog"
      save-button-text="action.save"
      :disable-save="selectedRoles.length === 0"
      :loading="savingChanges"
      @save="save"
      @cancel="showEditDialog = false"
    >
      <span
        slot="title"
        class="headline"
        :data-test="`api-key-row-${selectedKey.id}-edit-dialog-title`"
      >
        {{
          $t('apiKey.table.action.edit.dialog.title', { id: selectedKey.id })
        }}
      </span>
      <div
        slot="content"
        :data-test="`api-key-row-${selectedKey.id}-edit-dialog-content`"
      >
        <v-row class="mt-12">
          <v-col>
            {{ $t('apiKey.table.action.edit.dialog.message') }}
          </v-col>
        </v-row>
        <v-row v-for="role in roles" :key="role" no-gutters>
          <v-col class="checkbox-wrapper">
            <v-checkbox
              v-model="selectedRoles"
              height="10px"
              :value="role"
              :label="$t(`apiKey.role.${role}`)"
              :data-test="`role-${role}-checkbox`"
            />
          </v-col>
        </v-row>
      </div>
    </xrd-simple-dialog>

    <!-- Confirm revoke dialog -->
    <xrd-confirm-dialog
      v-if="confirmRevoke"
      :data-test="`api-key-row-${selectedKey.id}-revoke-confirmation`"
      :dialog="confirmRevoke"
      title="apiKey.table.action.revoke.confirmationDialog.title"
      text="apiKey.table.action.revoke.confirmationDialog.message"
      :data="{ id: selectedKey.id }"
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
import Vue from 'vue';
import { DataTableHeader } from 'vuetify';
import { ApiKey } from '@/api-types';
import { RouteName, Roles } from '@/global';
import * as api from '@/util/api';
import { Permissions } from '@/global';
import { mapActions, mapState } from 'pinia';
import { userStore } from '@/store/modules/user';
import { notificationsStore } from '@/store/modules/notifications';

export default Vue.extend({
  data() {
    return {
      search: '' as string,
      loading: false,
      showOnlyPending: false,
      selectedKey: undefined as undefined | ApiKey,
      selectedRoles: [] as string[],
      showEditDialog: false,
      confirmRevoke: false,
      savingChanges: false,
      removingApiKey: false,
      roles: Roles,
      apiKeys: new Array<ApiKey>(),
    };
  },
  computed: {
    ...mapState(userStore, ['hasPermission']),
    canEdit(): boolean {
      return this.hasPermission(Permissions.UPDATE_API_KEY);
    },
    canRevoke(): boolean {
      return this.hasPermission(Permissions.REVOKE_API_KEY);
    },
    headers(): DataTableHeader[] {
      return [
        {
          text: this.$t('apiKey.table.header.id') as string,
          align: 'start',
          value: 'id',
          class: 'xrd-table-header ts-table-header-server-code',
        },
        {
          text: this.$t('apiKey.table.header.roles') as string,
          align: 'start',
          value: 'roles',
          class: 'xrd-table-header ts-table-header-valid-from',
        },

        {
          text: '',
          value: 'button',
          sortable: false,
          class: 'xrd-table-header mr-table-header-buttons',
        },
      ];
    },
  },
  created(): void {
    this.loadKeys();
  },

  methods: {
    ...mapActions(notificationsStore, ['showError', 'showSuccess']),
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
            this.$t('apiKey.table.action.revoke.success', { id: key.id }),
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
        .put(
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
@import '~styles/tables';

.button-wrap {
  width: 100%;
  display: flex;
  justify-content: flex-end;
}

.server-code {
  font-weight: 600;
  font-size: 14px;
}

.align-fix {
  align-items: center;
}

.margin-fix {
  margin-top: -10px;
}

.custom-footer {
  border-top: thin solid rgba(0, 0, 0, 0.12); /* Matches the color of the Vuetify table line */
  height: 16px;
}
</style>
