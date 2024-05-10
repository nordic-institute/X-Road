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
    cancel-button-text="action.cancel"
    title="globalResources.addGlobalGroup"
    submittable
    :disable-save="!meta.valid"
    :loading="loading"
    @cancel="$emit('cancel')"
    @save="save"
  >
    <template #content>
      <v-text-field
        v-model="code"
        v-bind="codeAttrs"
        variant="outlined"
        :label="$t('globalResources.code')"
        autofocus
        data-test="add-global-group-code-input"
      />

      <v-text-field
        v-model="description"
        v-bind="descriptionAttrs"
        :label="$t('globalResources.description')"
        variant="outlined"
        data-test="add-global-group-description-input"
      />
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts" setup>
import { useGlobalGroups } from '@/store/modules/global-groups';
import { useForm } from 'vee-validate';
import { useBasicForm } from '@/util/composables';
import { AxiosError } from 'axios';

const emit = defineEmits(['save', 'cancel']);
const { defineField, meta, handleSubmit, setFieldError } = useForm({
  validationSchema: {
    code: 'required',
    description: 'required',
  },
});
const [code, codeAttrs] = defineField('code', {
  props: (state) => ({ 'error-messages': state.errors }),
});
const [description, descriptionAttrs] = defineField('description', {
  props: (state) => ({ 'error-messages': state.errors }),
});

const { add } = useGlobalGroups();
const { loading, showSuccess, t, showOrTranslateErrors } = useBasicForm(
  setFieldError,
  {
    code: 'globalGroupCodeAndDescriptionDto.code',
  },
);

const save = handleSubmit((values) => {
  loading.value = true;
  add({ code: values.code, description: values.description })
    .then(() => {
      showSuccess(t('globalResources.globalGroupSuccessfullyAdded'));
      emit('save');
    })
    .catch((error) => showOrTranslateErrors(error as AxiosError))
    .finally(() => (loading.value = false));
});
</script>
<style lang="scss" scoped>
@import '@/assets/tables';

div.v-input {
  padding-bottom: 8px;
}
</style>
