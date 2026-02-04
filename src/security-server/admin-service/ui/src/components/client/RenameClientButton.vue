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
    <XrdBtn data-test="rename-client-button" variant="outlined" text="action.edit" prepend-icon="edit_square" @click="openDialog" />

    <XrdSimpleDialog
      v-if="showDialog"
      :disable-save="!canSave"
      :loading="loading"
      cancel-button-text="action.cancel"
      save-button-text="action.save"
      title="client.action.renameSubsystem.title"
      submittable
      @cancel="showDialog = false"
      @save="rename"
    >
      <template #content>
        <XrdFormBlock>
          <XrdFormBlockRow full-length>
            <v-text-field
              v-model="name"
              v-bind="nameAttrs"
              data-test="subsystem-name-input"
              class="xrd"
              autofocus
              :label="$t('client.subsystemName')"
            />
          </XrdFormBlockRow>
        </XrdFormBlock>
      </template>
    </XrdSimpleDialog>
  </div>
</template>

<script lang="ts" setup>
import { computed, PropType, ref } from 'vue';
import { useForm } from 'vee-validate';
import { ClientStatus } from '@/openapi-types';
import { useClient } from '@/store/modules/client';
import { XrdBtn, useNotifications, XrdFormBlock, XrdFormBlockRow, XrdSimpleDialog } from '@niis/shared-ui';

const props = defineProps({
  id: {
    type: String,
    required: true,
  },
  subsystemName: {
    type: String,
    default: '',
  },
  clientStatus: {
    type: String as PropType<ClientStatus>,
    default: '',
  },
});

const emits = defineEmits(['done']);

const { defineField, meta, handleSubmit, resetForm } = useForm({
  validationSchema: { subsystemName: 'required' },
  initialValues: { subsystemName: props.subsystemName || '' },
});

const { addError, addSuccessMessage } = useNotifications();

const [name, nameAttrs] = defineField('subsystemName', {
  props: (state) => ({ 'error-messages': state.errors }),
});

resetForm();

const loading = ref(false);
const showDialog = ref(false);

const canSave = computed(() => meta.value.valid && meta.value.dirty && (name.value ? true : props.subsystemName));

const client = useClient();

function openDialog() {
  resetForm();
  showDialog.value = true;
}

const rename = handleSubmit((values) => {
  loading.value = true;
  client
    .renameClient(props.id, values.subsystemName)
    .then(() => {
      if (props.clientStatus === ClientStatus.REGISTERED) {
        addSuccessMessage('client.action.renameSubsystem.changeSubmitted');
      } else {
        addSuccessMessage('client.action.renameSubsystem.changeAdded');
      }
      emits('done', props.id);
    })
    .catch((error) => addError(error))
    .finally(() => {
      loading.value = false;
      showDialog.value = false;
    });
});
</script>
