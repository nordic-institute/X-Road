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
    :title="title"
    data-test="system-settings-member-class-edit-dialog"
    save-button-text="action.save"
    submittable
    :scrollable="false"
    :show-close="true"
    :loading="loading"
    :disable-save="!meta.valid || !meta.dirty"
    @save="onSaveMemberClass"
    @cancel="$emit('cancel')"
  >
    <template #content>
      <v-text-field
        v-model="code"
        v-bind="codeAttrs"
        data-test="system-settings-member-class-code-edit-field"
        :disabled="!modeAdd"
        :label="$t('systemSettings.code')"
        :autofocus="modeAdd"
        variant="outlined"
        class="dlg-row-input space-out-bottom"
        name="code"
      />
      <v-text-field
        v-model="description"
        v-bind="descriptionAttrs"
        data-test="system-settings-member-class-description-edit-field"
        :label="$t('systemSettings.description')"
        :autofocus="!modeAdd"
        variant="outlined"
        class="dlg-row-input"
        name="memberClass"
      />
    </template>
  </xrd-simple-dialog>
</template>
<script lang="ts" setup>
import { computed, PropType } from 'vue';
import { useForm } from 'vee-validate';
import { MemberClass } from '@/openapi-types';
import { useMemberClass } from '@/store/modules/member-class';
import { useBasicForm } from '@/util/composables';

const props = defineProps({
  memberClass: {
    type: Object as PropType<MemberClass>,
    default: undefined,
  },
});

const emit = defineEmits(['save', 'cancel']);

const { meta, setFieldError, defineField, handleSubmit } = useForm({
  validationSchema: {
    code: 'required|min:1|max:255',
    description: 'required|min:1',
  },
  initialValues: {
    code: props.memberClass?.code || '',
    description: props.memberClass?.description || '',
  },
});
const [code, codeAttrs] = defineField('code', {
  props: (state) => ({ 'error-messages': state.errors }),
});
const [description, descriptionAttrs] = defineField('description', {
  props: (state) => ({ 'error-messages': state.errors }),
});

const { loading, showSuccess, showOrTranslateErrors, t } = useBasicForm(setFieldError, {
    code: 'memberClassDto.code',
    description: 'memberClassDescriptionDto.description',
  },
);
const { add, update } = useMemberClass();

const modeAdd = computed(() => !props.memberClass?.code);
const title = computed(() =>
  modeAdd.value
    ? 'systemSettings.addMemberClassTitle'
    : 'systemSettings.editMemberClassTitle',
);

function _save(
  values: { code: string; description: string },
  classCode?: string,
) {
  if (classCode) {
    return update(classCode, values.description);
  } else {
    return add({
      code: values.code,
      description: values.description,
    });
  }
}

const onSaveMemberClass = handleSubmit((values) => {
  loading.value = true;
  _save(values, props.memberClass?.code)
    .then(() => {
      showSuccess(t('systemSettings.memberClassSaved'));
      emit('save');
    })
    .catch((error) => showOrTranslateErrors(error))
    .finally(() => (loading.value = false));
});
</script>
