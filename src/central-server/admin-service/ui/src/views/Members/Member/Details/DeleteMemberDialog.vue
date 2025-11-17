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
    save-button-text="action.delete"
    save-button-icon="delete_forever"
    title="members.member.details.deleteMember"
    submittable
    :loading="loading"
    :disable-save="!meta.valid"
    @save="proceedDelete"
    @cancel="cancelDelete"
  >
    <template #text>
      <i18n-t scope="global" keypath="members.member.details.confirmDelete">
        <template #member>
          <span class="font-weight-bold">{{ member.member_name }}</span>
        </template>
      </i18n-t>
    </template>
    <template #content>
      <XrdFormBlock>
        <XrdFormBlockRow full-length>
          <v-text-field
            v-model="memberCode"
            v-bind="memberCodeAttrs"
            data-test="member-code"
            class="xrd"
            autofocus
            :label="$t('members.member.details.enterCode')"
          />
        </XrdFormBlockRow>
      </XrdFormBlock>
    </template>
  </XrdSimpleDialog>
</template>

<script lang="ts" setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { Client } from '@/openapi-types';
import { useMember } from '@/store/modules/members';
import { toIdentifier } from '@/util/helpers';
import { useForm } from 'vee-validate';
import { RouteName } from '@/global';
import {
  XrdSimpleDialog,
  XrdFormBlock,
  XrdFormBlockRow,
  useNotifications,
} from '@niis/shared-ui';

const props = defineProps({
  member: {
    type: Object as () => Client,
    required: true,
  },
});

const emits = defineEmits(['cancel']);

const { meta, handleSubmit, defineField, resetForm } = useForm({
  validationSchema: {
    memberCode: 'required|is:' + props.member.client_id.member_code,
  },
});

const [memberCode, memberCodeAttrs] = defineField('memberCode', {
  props: (state) => ({ 'error-messages': state.errors }),
});

const { deleteById: deleteMember } = useMember();
const { addError, addSuccessMessage } = useNotifications();

function cancelDelete() {
  resetForm();
  emits('cancel');
}

const loading = ref(false);
const router = useRouter();
const proceedDelete = handleSubmit(() => {
  loading.value = true;
  deleteMember(toIdentifier(props.member.client_id))
    .then(() => {
      addSuccessMessage('members.member.details.memberDeleted', {}, true);
      router.replace({ name: RouteName.Members });
    })
    .catch((error) => addError(error))
    .finally(() => (loading.value = false));
});
</script>

<style lang="scss" scoped></style>
