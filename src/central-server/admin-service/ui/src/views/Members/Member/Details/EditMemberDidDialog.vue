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
    title="members.member.details.editMemberDid"
    save-button-text="action.save"
    cancel-button-text="action.cancel"
    submittable
    :disable-save="!meta.valid || !meta.dirty"
    @cancel="cancelEdit"
    @save="saveNewMemberDid"
  >
    <template #content>
      <div class="dlg-input-width">
        <v-text-field
          v-bind="memberDid"
          variant="outlined"
          data-test="edit-member-did"
          autofocus
          :error-messages="errors.memberDid"
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
import { i18n } from '@/plugins/i18n';

const props = defineProps({
  member: {
    type: Object as PropType<Client>,
    required: true,
  },
});

const emits = defineEmits(['save', 'cancel']);

const { defineComponentBinds, errors, meta, handleSubmit } = useForm({
  initialValues: { memberDid: props.member.did },
});
const memberDid = defineComponentBinds('memberDid');

const { editMember } = useMember();
const { showError, showSuccess } = useNotifications();
const loading = ref(false);

function cancelEdit() {
  emits('cancel');
}

const { t } = i18n.global;
const saveNewMemberDid = handleSubmit((values) => {
  loading.value = true;
  editMember(toIdentifier(props.member.client_id),
    { member_name: props.member.member_name },
    { did: values.memberDid }
  )
    .then(() => {
      showSuccess(t('members.member.details.memberDidSaved'));
      emits('save');
    })
    .catch((error) => showError(error))
    .finally(() => (loading.value = false));
});
</script>

<style lang="scss" scoped></style>
