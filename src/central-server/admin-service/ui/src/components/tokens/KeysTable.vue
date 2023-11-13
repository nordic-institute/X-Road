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
    <v-data-table
      v-if="keys"
      :headers="headers"
      :items="keys"
      :items-per-page="-1"
      :loading="loadingKeys"
      item-key="id"
      class="keys-table"
    >
      <template #[`item.id`]="{ item }">
        <xrd-icon-base class="key-icon">
          <xrd-icon-key />
        </xrd-icon-base>
        <span :data-test="`key-label-text`">{{ keyLabel(item) }}</span>
      </template>
      <template #[`item.createdAt`]="{ item }">
        <date-time :value="item.created_at" />
      </template>
      <template #[`item.actions`]="{ item }">
        <xrd-button
          v-if="canActivateKey(item)"
          :data-test="`key-${keyLabel(item)}-activate-button`"
          text
          @click="openActivateKeyDialog(item)"
        >
          {{ $t('action.activate') }}
        </xrd-button>
        <xrd-button
          v-if="canDeleteKey(item)"
          :data-test="`key-${keyLabel(item)}-delete-button`"
          text
          @click="openDeleteKeyDialog(item)"
        >
          {{ $t('action.delete') }}
        </xrd-button>
      </template>
      <template #bottom />
    </v-data-table>
    <signing-key-delete-dialog
      v-if="selectedKey && showDeleteKeyDialog"
      :signing-key="selectedKey"
      @cancel="closeDialogs"
      @key-delete="updateKeys('delete')"
    />
    <signing-key-activate-dialog
      v-if="selectedKey && showActivateKeyDialog"
      :signing-key="selectedKey"
      @cancel="closeDialogs"
      @key-activate="updateKeys('activate')"
    />
  </div>
</template>

<script lang="ts">
/**
 * Table component for an array of keys
 */
import { defineComponent, PropType } from 'vue';
import { ConfigurationSigningKey, PossibleKeyAction } from '@/openapi-types';
import { DataTableHeader } from '@/ui-types';
import { VDataTable } from 'vuetify/labs/VDataTable';
import { mapState } from 'pinia';
import { useUser } from '@/store/modules/user';
import { Permissions } from '@/global';
import SigningKeyDeleteDialog from '@/components/signingKeys/SigningKeyDeleteDialog.vue';
import SigningKeyActivateDialog from '@/components/signingKeys/SigningKeyActivateDialog.vue';
import DateTime from '@/components/ui/DateTime.vue';
import { XrdIconKey } from '@niis/shared-ui';

export default defineComponent({
  components: {
    DateTime,
    SigningKeyActivateDialog,
    SigningKeyDeleteDialog,
    VDataTable,
    XrdIconKey,
  },
  props: {
    keys: {
      type: Array as PropType<ConfigurationSigningKey[]>,
      required: true,
    },
    loadingKeys: {
      type: Boolean,
      default: false,
    },
  },
  emits: ['update-keys'],
  data() {
    return {
      selectedKey: null as ConfigurationSigningKey | null,
      showDeleteKeyDialog: false,
      showActivateKeyDialog: false,
    };
  },
  computed: {
    ...mapState(useUser, ['hasPermission']),
    canDeleteKeys(): boolean {
      return this.hasPermission(Permissions.DELETE_SIGNING_KEY);
    },
    canActivateKeys(): boolean {
      return this.hasPermission(Permissions.ACTIVATE_SIGNING_KEY);
    },
    headers(): DataTableHeader[] {
      return [
        {
          title: this.$t('keys.signKey') as string,
          align: 'start',
          key: 'id',
        },
        {
          title: this.$t('keys.created') as string,
          align: 'start',
          key: 'createdAt',
        },
        {
          title: '',
          align: 'end',
          key: 'actions',
        },
      ];
    },
  },
  methods: {
    keyLabel(key: ConfigurationSigningKey): string {
      return key?.label?.label || key.id;
    },
    canDeleteKey(key: ConfigurationSigningKey): boolean {
      return (
        this.canDeleteKeys &&
        key.possible_actions.includes(PossibleKeyAction.DELETE)
      );
    },
    canActivateKey(key: ConfigurationSigningKey): boolean {
      return (
        this.canActivateKeys &&
        key.possible_actions.includes(PossibleKeyAction.ACTIVATE)
      );
    },
    openDeleteKeyDialog(key: ConfigurationSigningKey) {
      this.showDeleteKeyDialog = true;
      this.selectedKey = key;
    },
    openActivateKeyDialog(key: ConfigurationSigningKey) {
      this.showActivateKeyDialog = true;
      this.selectedKey = key;
    },
    updateKeys(action: string) {
      this.closeDialogs();
      this.$emit('update-keys', action);
    },
    closeDialogs() {
      this.showDeleteKeyDialog = false;
      this.showActivateKeyDialog = false;
      this.selectedKey = null;
    },
  },
});
</script>

<style lang="scss" scoped>
@import '@/assets/tables';

.key-icon {
  margin-right: 18px;
  color: $XRoad-Purple100;
}

.keys-table {
  transform-origin: top;
  transition: transform 0.4s ease-in-out;
}
</style>
