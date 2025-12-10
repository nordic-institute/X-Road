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
  <XrdView data-test="api-keys-view" :title="title">
    <template v-if="$slots.tabs" #tabs>
      <slot name="tabs" />
    </template>
    <template v-if="$slots['append-header']" #append-header>
      <slot name="append-header" />
    </template>

    <XrdSubView>
      <template v-if="canCreate" #header>
        <v-spacer />
        <XrdBtn
          variant="flat"
          text="apiKey.createApiKey.title"
          prepend-icon="add_circle"
          data-test="api-key-create-key-button"
          @click="createApiKey()"
        />
      </template>
      <XrdCard>
        <v-data-table
          v-model:sort-by="sortBy"
          item-value="id"
          class="xrd"
          hide-default-footer
          :loading="loading"
          :headers="headers"
          :items="apiKeys"
          :search="search"
          :must-sort="true"
          :items-per-page="-1"
          :loader-height="2"
        >
          <template #[`item.id`]="{ item }">
            <XrdLabelWithIcon
              data-test="api-key-id"
              icon="key_vertical"
              label-color="on-surface"
              icon-color="on-surface"
              semi-bold
              :label="item.id"
            />
          </template>

          <template #[`item.roles`]="{ item }">
            <span :data-test="`api-key-row-${item.id}-roles`">{{ translateRoles(item.roles).join(', ') }}</span>
          </template>

          <template #[`item.button`]="{ item }">
            <div class="button-wrap">
              <XrdBtn
                v-if="canEdit"
                variant="text"
                color="tertiary"
                text="action.edit"
                :data-test="`api-key-row-${item.id}-edit-button`"
                @click="editKey(item)"
              />

              <XrdBtn
                v-if="canRevoke"
                variant="text"
                color="tertiary"
                text="apiKey.table.action.revoke.button"
                :data-test="`api-key-row-${item.id}-revoke-button`"
                @click="showRevokeDialog(item)"
              />
            </div>
          </template>
        </v-data-table>
      </XrdCard>
      <XrdEditApiKeyRolesDialog
        v-if="showEditDialog && selectedKey"
        :api-key="selectedKey"
        :handler="handler"
        @cancel="showEditDialog = false"
        @save="
          showEditDialog = false;
          loadKeys();
        "
      />
      <!-- Confirm revoke dialog -->
      <XrdConfirmDialog
        v-if="selectedKey && confirmRevoke"
        title="apiKey.table.action.revoke.confirmationDialog.title"
        text="apiKey.table.action.revoke.confirmationDialog.message"
        focus-on-accept
        :data-test="`api-key-row-${selectedKey.id}-revoke-confirmation`"
        :data="{ id: selectedKey.id }"
        :loading="removingApiKey"
        @cancel="confirmRevoke = false"
        @accept="revokeApiKey"
      />
    </XrdSubView>
  </XrdView>
</template>

<script lang="ts">
/**
 * View for 'API keys' tab
 */
import { defineComponent, PropType } from 'vue';

import { SortItem } from 'vuetify/lib/components/VDataTable/composables/sort';
import { DataTableHeader } from 'vuetify/lib/components/VDataTable/types';

import { useNotifications } from '../../composables';
import { XrdBtn, XrdCard, XrdLabelWithIcon, XrdConfirmDialog } from '../../components';
import { XrdSubView, XrdView } from '../../layouts';

import XrdEditApiKeyRolesDialog from './XrdEditApiKeyRolesDialog.vue';
import { ApiKeysHandler, ApiKey } from '../../types';

export default defineComponent({
  components: {
    XrdEditApiKeyRolesDialog,
    XrdView,
    XrdSubView,
    XrdCard,
    XrdBtn,
    XrdLabelWithIcon,
    XrdConfirmDialog,
  },
  props: {
    handler: {
      type: Object as PropType<ApiKeysHandler>,
      required: true,
    },
    canCreate: {
      type: Boolean,
      required: true,
    },
    canEdit: {
      type: Boolean,
      required: true,
    },
    canRevoke: {
      type: Boolean,
      required: true,
    },
    canView: {
      type: Boolean,
      required: true,
    },
    createApiKeyRouteName: {
      type: String,
      required: true,
    },
    title: {
      type: String,
      required: true,
    },
  },
  setup() {
    const { addError, addSuccessMessage } = useNotifications();
    return { addError, addSuccessMessage };
  },
  data() {
    return {
      sortBy: [{ key: 'id', order: 'asc' }] as SortItem[],
      search: '' as string,
      loading: false,
      showOnlyPending: false,
      selectedKey: undefined as undefined | ApiKey,
      showEditDialog: false,
      confirmRevoke: false,
      savingChanges: false,
      removingApiKey: false,
      apiKeys: new Array<ApiKey>(),
    };
  },
  computed: {
    headers(): DataTableHeader<ApiKey>[] {
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
          align: 'end',
          sortable: false,
        },
      ];
    },
  },
  created(): void {
    this.loadKeys();
  },

  methods: {
    loadKeys(): void {
      if (this.canView) {
        this.loading = true;
        this.handler
          .fetchApiKeys()
          .then((data) => (this.apiKeys = data))
          .catch((error) => this.addError(error))
          .finally(() => (this.loading = false));
      }
    },
    editKey(apiKey: ApiKey): void {
      this.selectedKey = apiKey;
      this.showEditDialog = true;
    },
    showRevokeDialog(apiKey: ApiKey): void {
      this.selectedKey = apiKey;
      this.confirmRevoke = true;
    },
    createApiKey(): void {
      this.$router.push({
        name: this.createApiKeyRouteName,
      });
    },
    translateRoles(roles: string[]): string[] {
      return !roles ? [] : roles.map((role) => this.$t(`apiKey.role.${role}`) as string);
    },
    revokeApiKey() {
      if (!this.selectedKey) return;

      this.removingApiKey = true;
      return this.handler
        .deleteApiKey(this.selectedKey.id)
        .then((id) => {
          this.addSuccessMessage('apiKey.table.action.revoke.success', {
            id,
          });
        })
        .catch((error) => this.addError(error))
        .finally(() => {
          this.confirmRevoke = false;
          this.removingApiKey = false;
          this.loadKeys();
        });
    },
  },
});
</script>
