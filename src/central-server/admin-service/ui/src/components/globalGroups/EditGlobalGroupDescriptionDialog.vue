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
    save-button-text="action.save"
    title="globalGroup.editDescription"
    submittable
    :loading="loading"
    :disable-save="!meta.valid || !meta.dirty"
    @save="saveDescription"
    @cancel="$emit('cancel')"
  >
    <template #content>
      <v-text-field
        v-model="description"
        v-bind="descriptionAttrs"
        variant="outlined"
        autofocus
        persistent-hint
        :label="$t('globalGroup.description')"
      />
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts" setup>
import { useForm } from 'vee-validate';
import { useGlobalGroups } from '@/store/modules/global-groups';
import { useBasicForm } from '@/util/composables';

const props = defineProps({
  groupCode: {
    type: String,
    required: true,
  },
  groupDescription: {
    type: String,
    required: true,
  },
});

const emit = defineEmits(['save', 'cancel']);

const { meta, defineField, handleSubmit } = useForm({
  validationSchema: { description: 'required' },
  initialValues: { description: props.groupDescription },
});
const [description, descriptionAttrs] = defineField('description', {
  props: (state) => ({ 'error-messages': state.errors }),
});

const { editGroupDescription } = useGlobalGroups();
const { loading, showSuccess, t, showError } = useBasicForm();

const saveDescription = handleSubmit((values) => {
  loading.value = true;
  editGroupDescription(props.groupCode, {
    description: values.description,
  })
    .then((resp) => {
      showSuccess(t('globalGroup.descriptionSaved'));
      emit('save', resp.data);
    })
    .catch((error) => {
      showError(error);
    })
    .finally(() => (loading.value = false));
});
</script>

<style lang="scss" scoped></style>
