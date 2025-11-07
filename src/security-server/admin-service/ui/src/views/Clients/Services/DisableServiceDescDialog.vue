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
    title="services.disableTitle"
    save-button-text="action.ok"
    submittable
    :loading="disabling"
    @save="save"
    @cancel="cancel"
  >
    <template #content>
      <XrdFormBlock>
        <XrdFormBlockRow full-length>
          <v-text-field
            v-model="notice"
            data-test="disable-notice-text-field"
            class="xrd"
            single-line
            autofocus
            :label="$t('services.disableNotice')"
          />
        </XrdFormBlockRow>
      </XrdFormBlock>
    </template>
  </XrdSimpleDialog>
</template>

<script lang="ts" setup>
// Dialog to confirm service description disabling
import { PropType, ref } from 'vue';
import { ServiceDescription } from '@/openapi-types';
import {
  XrdFormBlock,
  XrdFormBlockRow,
  XrdSimpleDialog,
  useNotifications,
  DialogSaveHandler,
} from '@niis/shared-ui';
import { useServiceDescriptions } from '@/store/modules/service-descriptions';

const props = defineProps({
  subject: {
    type: Object as PropType<ServiceDescription>,
    required: true,
  },
  subjectIndex: {
    type: Number,
    required: true,
  },
});

const emit = defineEmits(['cancel', 'save']);

const { addSuccessMessage } = useNotifications();
const { disableServiceDescription } = useServiceDescriptions();

const notice = ref('');
const disabling = ref(false);

function cancel(): void {
  emit('cancel');
  clear();
}

function save(evt: Event, handler: DialogSaveHandler): void {
  disabling.value = true;
  disableServiceDescription(props.subject.id, notice.value)
    .then(() => addSuccessMessage('services.disableSuccess'))
    .then(() => emit('save', notice.value, props.subject))
    .catch((error) => handler.addError(error))
    .finally(() => (disabling.value = false))
    .finally(() => clear());
}

function clear(): void {
  notice.value = '';
}
</script>

<style lang="scss" scoped></style>
