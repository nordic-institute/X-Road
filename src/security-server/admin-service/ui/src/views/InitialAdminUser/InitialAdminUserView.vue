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
  <XrdElevatedViewSimple id="initial-admin-user" title="initialAdminUser.title">
    <XrdWizardStep>
      <XrdFormBlock title="initialAdminUser.info">
        <XrdFormBlockRow>
          <v-text-field
            v-model="usernameMdl"
            v-bind="usernameRef"
            data-test="admin-username-input"
            class="xrd"
            autofocus
            :label="$t('initialAdminUser.username')"
          />
        </XrdFormBlockRow>
        <XrdFormBlockRow>
          <v-text-field
            v-model="passwordMdl"
            v-bind="passwordRef"
            data-test="admin-password-input"
            class="xrd"
            type="password"
            :label="$t('initialAdminUser.password')"
          />
        </XrdFormBlockRow>
        <XrdFormBlockRow>
          <v-text-field
            v-model="confirmPasswordMdl"
            v-bind="confirmPasswordRef"
            data-test="admin-confirm-password-input"
            class="xrd"
            type="password"
            :label="$t('initialAdminUser.confirmPassword')"
          />
        </XrdFormBlockRow>
      </XrdFormBlock>
      <template #footer>
        <v-spacer />
        <XrdBtn
          data-test="admin-user-save-button"
          text="action.submit"
          :disabled="!meta.valid"
          :loading="saving"
          @click="submit"
        />
      </template>
    </XrdWizardStep>
  </XrdElevatedViewSimple>
</template>

<script lang="ts" setup>
import { ref } from 'vue';
import { useForm } from 'vee-validate';

import {
  useNotifications,
  veeDefaultFieldConfig,
  XrdBtn,
  XrdElevatedViewSimple,
  XrdFormBlock,
  XrdFormBlockRow,
  XrdWizardStep
} from '@niis/shared-ui';

import { RouteName } from '@/global';
import { useInitializeServer } from '@/store/modules/initializeServer';
import { useRouter } from "vue-router";

const { addError } = useNotifications();
const router = useRouter();
const { createInitialAdminUser } = useInitializeServer();
const { meta, values, defineField } = useForm({
  validationSchema: {
    username: 'required|min:3|max:30',
    password: 'required|min:6|max:255|confirmed:@passwordConfirm',
    passwordConfirm: 'required',
  },
});

const componentConfig = veeDefaultFieldConfig();
const [usernameMdl, usernameRef] = defineField('username', componentConfig);
const [passwordMdl, passwordRef] = defineField('password', componentConfig);
const [confirmPasswordMdl, confirmPasswordRef] = defineField('passwordConfirm', componentConfig);

const saving = ref(false);

async function submit() {
  const username = values.username as string;
  const password = values.password as string;
  saving.value = true;
  createInitialAdminUser({ username, password })
    .then(() => {
      router.replace({ name: RouteName.Login });
    })
    .catch((error) => addError(error))
    .finally(() => saving.value = false);
}
</script>

<style lang="scss" scoped></style>
