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
      item-key="id"
      class="xrd bg-surface-container"
      :headers="headers"
      :items="keys"
      :items-per-page="-1"
      :loading="loadingKeys"
      :row-props="rowProps"
      hide-default-footer
    >
      <template #[`item.id`]="{ item }">
        <XrdLabelWithIcon
          data-test="key-label-text"
          label-color="on-surface"
          icon-color="on-surface"
          icon="key"
          :label="keyLabel(item)"
          :class="{ 'opacity-60': isInactive(item) }"
        />
      </template>
      <template #[`item.createdAt`]="{ item }">
        <XrdDateTime :class="{ 'opacity-60': isInactive(item) }" :value="item.created_at" />
      </template>
      <template #[`item.key_algorithm`]="{ item }">
        <span :class="{ 'opacity-60': isInactive(item) }">
          {{ item.key_algorithm }}
        </span>
      </template>
      <template #[`item.actions`]="{ item }">
        <XrdBtn
          v-if="canActivateKey(item)"
          variant="text"
          text="action.activate"
          :data-test="`key-${keyLabel(item)}-activate-button`"
          @click="openActivateKeyDialog(item)"
        />
        <XrdBtn
          v-if="canDeleteKey(item)"
          variant="text"
          text="action.delete"
          :data-test="`key-${keyLabel(item)}-delete-button`"
          @click="openDeleteKeyDialog(item)"
        />
      </template>
    </v-data-table>
    <SigningKeyDeleteDialog
      v-if="selectedKey && showDeleteKeyDialog"
      :signing-key="selectedKey"
      @cancel="closeDialogs"
      @key-delete="updateKeys('delete')"
    />
    <SigningKeyActivateDialog
      v-if="selectedKey && showActivateKeyDialog"
      :signing-key="selectedKey"
      @cancel="closeDialogs"
      @key-activate="updateKeys('activate')"
    />
  </div>
</template>

<script lang="ts" setup>
import { computed, PropType, ref } from 'vue';

import { useI18n } from 'vue-i18n';
import { DataTableHeader } from 'vuetify/lib/components/VDataTable/types';

import { XrdBtn, XrdDateTime, XrdLabelWithIcon } from '@niis/shared-ui';

import { Permissions } from '@/global';
import { ConfigurationSigningKey, PossibleKeyAction } from '@/openapi-types';
import { useUser } from '@/store/modules/user';

import SigningKeyActivateDialog from './dialogs/SigningKeyActivateDialog.vue';
import SigningKeyDeleteDialog from './dialogs/SigningKeyDeleteDialog.vue';

defineProps({
  keys: {
    type: Array as PropType<ConfigurationSigningKey[]>,
    required: true,
  },
  loadingKeys: {
    type: Boolean,
    default: false,
  },
});

const emit = defineEmits(['update-keys']);

const { t } = useI18n();
const { hasPermission } = useUser();

const selectedKey = ref<ConfigurationSigningKey | null>(null),
  showDeleteKeyDialog = ref(false),
  showActivateKeyDialog = ref(false);

const canDeleteKeys = computed(() => hasPermission(Permissions.DELETE_SIGNING_KEY)),
  canActivateKeys = computed(() => hasPermission(Permissions.ACTIVATE_SIGNING_KEY)),
  headers = computed(
    () =>
      [
        {
          title: t('keys.signKey') as string,
          align: 'start',
          key: 'id',
        },
        {
          title: t('keys.algorithm') as string,
          align: 'start',
          key: 'key_algorithm',
        },
        {
          title: t('keys.created') as string,
          align: 'start',
          key: 'createdAt',
        },
        {
          title: '',
          align: 'end',
          key: 'actions',
        },
      ] as DataTableHeader[],
  );

function keyLabel(key: ConfigurationSigningKey): string {
  return key?.label?.label || key.id;
}

function canDeleteKey(key: ConfigurationSigningKey): boolean {
  return canDeleteKeys.value && key.possible_actions.includes(PossibleKeyAction.DELETE);
}

function isInactive(key: ConfigurationSigningKey): boolean {
  return key.possible_actions.includes(PossibleKeyAction.ACTIVATE);
}

function canActivateKey(key: ConfigurationSigningKey): boolean {
  return canActivateKeys.value && isInactive(key);
}

function openDeleteKeyDialog(key: ConfigurationSigningKey) {
  showDeleteKeyDialog.value = true;
  selectedKey.value = key;
}

function openActivateKeyDialog(key: ConfigurationSigningKey) {
  showActivateKeyDialog.value = true;
  selectedKey.value = key;
}

function updateKeys(action: string) {
  closeDialogs();
  emit('update-keys', action);
}

function closeDialogs() {
  showDeleteKeyDialog.value = false;
  showActivateKeyDialog.value = false;
  selectedKey.value = null;
}

function rowProps({ item }: { item: ConfigurationSigningKey }) {
  return {
    class: isInactive(item) ? 'bg-inactive' : '',
  };
}
</script>

<style lang="scss" scoped>
:deep(.bg-inactive) {
  background-color: rgba(var(--v-theme-on-surface-variant), 0.08) !important;
}
</style>
