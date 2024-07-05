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
    title="tokens.loginDialog.title"
    save-button-text="tokens.logIn"
    submittable
    :disable-save="!meta.valid"
    :loading="loading"
    @save="login"
    @cancel="$emit('cancel')"
  >
    <template #content>
      <v-text-field
        v-model="tokenPin"
        v-bind="tokenPinAttrs"
        type="password"
        name="tokenPin"
        data-test="token-pin-input"
        variant="outlined"
        autofocus
        :label="$t('tokens.pin')"
      />
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts" setup>
import { PropType } from 'vue';
import { useToken } from '@/store/modules/tokens';
import { Token } from '@/openapi-types';
import { useForm } from 'vee-validate';
import { useBasicForm } from '@/util/composables';

const props = defineProps({
  token: {
    type: Object as PropType<Token>,
    required: true,
  },
});

const emit = defineEmits(['token-login', 'cancel']);

const { meta, defineField, setFieldError, handleSubmit } = useForm({
  validationSchema: { tokenPin: 'required' },
});

const [tokenPin, tokenPinAttrs] = defineField('tokenPin', {
  props: (state) => ({ 'error-messages': state.errors }),
});

const { loginToken } = useToken();
const { showSuccess, showError, t, loading } = useBasicForm();

const login = handleSubmit((values) => {
  loading.value = true;
  loginToken(props.token.id, { password: values.tokenPin })
    .then(() => {
      showSuccess(t('tokens.loginDialog.success'));
      emit('token-login');
    })
    .catch((error) => {
      const metadata: string[] = error.response?.data?.error?.metadata;
      if (metadata && metadata.length > 0) {
        setFieldError(
          'tokenPin',
          metadata.map((code) => t('tokens.errors.' + code) as string),
        );
      }
      showError(error);
    })
    .finally(() => (loading.value = false));
});
</script>

<style lang="scss" scoped></style>
