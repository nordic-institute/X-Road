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
    :loading="loading"
    title="members.member.details.editMemberName"
    save-button-text="action.save"
    cancel-button-text="action.cancel"
    submittable
    :disable-save="!meta.valid || !meta.dirty"
    @cancel="cancelEdit"
    @save="saveNewMemberName"
  >
    <template #content>
      <div class="dlg-input-width">
        <v-text-field
          v-bind="memberName"
          variant="outlined"
          data-test="edit-member-name"
          autofocus
          :error-messages="errors.memberName"
        ></v-text-field>
      </div>
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts" setup>
import { useMember } from '@/store/modules/members';
import { Client } from '@/openapi-types';
import { useNotifications } from '@/store/modules/notifications';
import { toIdentifier } from '@/util/helpers';
import { PropType, ref } from 'vue';
import { useForm } from 'vee-validate';
import { useI18n } from 'vue-i18n';

const props = defineProps({
  member: {
    type: Object as PropType<Client>,
    required: true,
  },
});

const emits = defineEmits(['save', 'cancel']);

const { defineComponentBinds, errors, meta, handleSubmit } = useForm({
  validationSchema: { memberName: 'required' },
  initialValues: { memberName: props.member.member_name },
});
const memberName = defineComponentBinds('memberName');

const { editMemberName } = useMember();
const { showError, showSuccess } = useNotifications();
const loading = ref(false);

function cancelEdit() {
  emits('cancel');
}

const { t } = useI18n();
const saveNewMemberName = handleSubmit((values) => {
  loading.value = true;
  editMemberName(toIdentifier(props.member.client_id), {
    member_name: values.memberName,
  })
    .then(() => {
      showSuccess(t('members.member.details.memberNameSaved'));
      emits('save');
    })
    .catch((error) => showError(error))
    .finally(() => (loading.value = false));
});
</script>

<style lang="scss" scoped></style>
