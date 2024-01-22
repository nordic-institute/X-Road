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
    title="tlsCertificates.managementService.generateCsr.title"
    save-button-text="action.generateCsr"
    cancel-button-text="action.cancel"
    submittable
    :loading="loading"
    :disable-save="!meta.valid"
    @cancel="emit('cancel')"
    @save="submit"
  >
    <template #text>
      {{ $t('tlsCertificates.managementService.generateCsr.content') }}
    </template>
    <template #content>
      <v-text-field
        v-model="distinguishedName"
        v-bind="distinguishedNameAttrs"
        variant="outlined"
        autofocus
        data-test="enter-distinguished-name"
        :placeholder="
          $t('tlsCertificates.managementService.generateCsr.example')
        "
        :label="
          $t('tlsCertificates.managementService.generateCsr.distinguishedName')
        "
      >
      </v-text-field>
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts" setup>
import { useManagementServices } from '@/store/modules/management-services';
import { useBasicForm } from '@/util/composables';
import { useForm } from 'vee-validate';

const emit = defineEmits(['cancel', 'generate']);

const { handleSubmit, defineField, meta } = useForm({
  validationSchema: { distinguishedName: 'required' },
});

const [distinguishedName, distinguishedNameAttrs] = defineField(
  'distinguishedName',
  {
    props: (state) => ({ 'error-messages': state.errors }),
  },
);

const { generateCsr } = useManagementServices();
const { loading, showError } = useBasicForm();

const submit = handleSubmit((values) => {
  loading.value = true;
  generateCsr(values.distinguishedName)
    .then(() => emit('generate'))
    .catch((error) => {
      showError(error);
      emit('cancel');
    })
    .finally(() => (loading.value = false));
});
</script>

<style lang="scss" scoped>
$spacing: 12rem;

.icon-wrapper {
  display: flex;
  flex-direction: row;
  justify-content: space-evenly;
}

.first-action {
  margin-top: $spacing;
}
</style>
