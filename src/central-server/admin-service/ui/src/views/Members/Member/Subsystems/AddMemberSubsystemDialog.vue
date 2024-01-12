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
  <xrd-simple-dialog
    :disable-save="!meta.valid"
    :loading="loading"
    cancel-button-text="action.cancel"
    title="members.member.subsystems.addClient"
    submittable
    @cancel="cancel"
    @save="add"
  >
    <template #content>
      <div class="dlg-input-width">
        <v-text-field
          v-model="subsystemCode"
          v-bind="subsystemCodeAttrs"
          :label="$t('members.member.subsystems.subsystemcode')"
          variant="outlined"
          autofocus
          data-test="add-subsystem-input"
        ></v-text-field>
      </div>
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts" setup>
import { PropType, ref } from 'vue';
import { ClientId } from '@/openapi-types';
import { useNotifications } from '@/store/modules/notifications';
import { useSubsystem } from '@/store/modules/subsystems';
import { useForm } from 'vee-validate';
import { i18n } from '@/plugins/i18n';

const props = defineProps({
  member: {
    type: Object as PropType<{ client_id: ClientId }>,
    required: true,
  },
});

const emits = defineEmits(['save', 'cancel']);

const { defineField, meta, handleSubmit, resetForm } = useForm({
  validationSchema: { subsystemCode: 'required' },
  initialValues: { subsystemCode: '' },
});

const { addSubsystem } = useSubsystem();
const { showError, showSuccess } = useNotifications();

const [subsystemCode, subsystemCodeAttrs] = defineField('subsystemCode', {
  props: (state) => ({ 'error-messages': state.errors }),
});

const loading = ref(false);

function cancel() {
  emits('cancel');
  resetForm();
}

const { t } = i18n.global;
const add = handleSubmit((values) => {
  loading.value = true;
  addSubsystem({
    subsystem_id: {
      member_class: props.member.client_id.member_class,
      member_code: props.member.client_id.member_code,
      subsystem_code: values.subsystemCode,
    },
  })
    .then(() => {
      showSuccess(
        t('members.member.subsystems.subsystemSuccessfullyAdded', {
          subsystemCode: values.subsystemCode,
        }),
      );
      emits('save');
      resetForm();
    })
    .catch((error) => {
      showError(error);
      emits('cancel');
    })
    .finally(() => (loading.value = false));
});
</script>
