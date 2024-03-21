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
    title="members.addMember"
    submittable
    @cancel="cancel"
    @save="add"
  >
    <template #content>
      <v-text-field
        v-model="memberName"
        v-bind="memberNameAttrs"
        variant="outlined"
        autofocus
        data-test="add-member-name-input"
        class="space-out-bottom"
        :label="$t('global.memberName')"
      />
      <v-select
        v-model="memberClass"
        v-bind="memberClassAttrs"
        item-title="code"
        item-value="code"
        variant="outlined"
        data-test="add-member-class-input"
        class="space-out-bottom"
        :items="memberClasses"
        :label="$t('global.memberClass')"
      />
      <v-text-field
        v-model="memberCode"
        v-bind="memberCodeAttrs"
        :label="$t('global.memberCode')"
        variant="outlined"
        data-test="add-member-code-input"
      />
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts" setup>
import { computed } from 'vue';
import { useMember } from '@/store/modules/members';
import { useMemberClass } from '@/store/modules/member-class';
import { useForm } from 'vee-validate';
import { useBasicForm } from '@/util/composables';

const emits = defineEmits(['save', 'cancel']);
const { defineField, setFieldError, meta, resetForm, handleSubmit } = useForm({
  validationSchema: {
    memberCode: 'required',
    memberName: 'required',
    memberClass: 'required',
  },
});
const [memberCode, memberCodeAttrs] = defineField('memberCode', {
  props: (state) => ({ 'error-messages': state.errors }),
});
const [memberName, memberNameAttrs] = defineField('memberName', {
  props: (state) => ({ 'error-messages': state.errors }),
});
const [memberClass, memberClassAttrs] = defineField('memberClass', {
  props: (state) => ({ 'error-messages': state.errors }),
});

const { add: addMember } = useMember();
const memberClassStore = useMemberClass();
const { showSuccess, t, loading, showOrTranslateErrors } = useBasicForm(
  setFieldError,
  { memberCode: 'memberAddDto.memberId.memberCode' },
);

const memberClasses = computed(() => memberClassStore.memberClasses);

function cancel() {
  emits('cancel');
  resetForm();
}

const add = handleSubmit((values) => {
  loading.value = true;
  addMember({
    member_name: values.memberName,
    member_id: {
      member_class: values.memberClass,
      member_code: values.memberCode,
    },
  })
    .then(() => {
      showSuccess(
        t('members.memberSuccessfullyAdded', {
          memberName: values.memberName,
        }),
      );
      emits('save');
      resetForm();
    })
    .catch((error) => showOrTranslateErrors(error))
    .finally(() => (loading.value = false));
});

memberClassStore.fetchAll();
</script>
