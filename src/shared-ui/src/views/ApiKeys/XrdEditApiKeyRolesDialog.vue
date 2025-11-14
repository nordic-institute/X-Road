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
  <XrdSimpleDialog
    save-button-text="action.save"
    title=""
    submittable
    :data-test="`api-key-row-${apiKey.id}-edit-dialog-content`"
    :disable-save="selectedRoles.length === 0"
    :loading="saving"
    @save="save"
    @cancel="emit('cancel')"
  >
    <template #title>
      <span :data-test="`api-key-row-${apiKey.id}-edit-dialog-title`">
        {{
          $t('apiKey.table.action.edit.dialog.title', {
            id: apiKey.id,
          })
        }}
      </span>
    </template>
    <template #text>
      <span class="font-weight-medium">{{ $t('apiKey.table.action.edit.dialog.message') }}</span>
    </template>
    <template #content>
      <XrdFormBlock>
        <v-checkbox
          v-for="role in rolesToEdit"
          :key="role"
          v-model="selectedRoles"
          class="xrd"
          hide-details
          height="10px"
          :value="role"
          :data-test="`role-${role}-checkbox`"
        >
          <template #label>
            <span>{{ $t(`apiKey.role.${role}`) }}</span>
            <span
              v-if="!handler.canAssignRole(role)"
              class="remove-only-role"
            >
              &nbsp; {{ $t('apiKey.edit.roleRemoveOnly') }}
            </span>
          </template>
        </v-checkbox>
      </XrdFormBlock>
    </template>
  </XrdSimpleDialog>
</template>
<script setup lang="ts">
import { PropType, ref, computed } from 'vue';

import { useNotifications, useRunning } from '../../composables';

import { XrdSimpleDialog, XrdFormBlock } from '../../components';

import { ApiKeysHandler, ApiKey } from '../../types';

const props = defineProps({
  apiKey: {
    type: Object as PropType<ApiKey>,
    required: true,
  },
  handler: {
    type: Object as PropType<ApiKeysHandler>,
    required: true,
  },
});

const emit = defineEmits(['cancel', 'save']);

const { addSuccessMessage, addError } = useNotifications();
const { saving, startSaving, stopSaving } = useRunning('saving');

const selectedRoles = ref<string[]>([]);

const rolesToEdit = computed(() =>
  props.handler?.availableRoles().filter((role) => selectedRoles.value.includes(role) || props.handler.canAssignRole(role)),
);

function save() {
  startSaving();
  return props.handler
    ?.updateApiKey(props.apiKey.id, selectedRoles.value)
    .then((key) => {
      addSuccessMessage('apiKey.table.action.edit.success', {
        id: key.id,
      });
      emit('save');
    })
    .catch((error) => addError(error))
    .finally(() => {
      stopSaving();
    });
}

selectedRoles.value = [...(props.apiKey?.roles || [])];
</script>
