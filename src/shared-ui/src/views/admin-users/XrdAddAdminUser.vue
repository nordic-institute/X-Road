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
  <XrdElevatedViewSimple data-test="add-admin-user-stepper-view" title="adminUsers.addUser.title" @close="close">
    <XrdWizard v-model="step">
      <template #header-items>
        <v-stepper-item :complete="step > 1" :value="1">{{ $t('adminUsers.addUser.step.roles.name') }}</v-stepper-item>
        <v-divider />
        <v-stepper-item :complete="userAdded" :value="2">{{ $t('adminUsers.addUser.step.credentials.name') }}</v-stepper-item>
      </template>
      <v-stepper-window-item data-test="add-admin-user-step-1" :value="1" class="pa-0 centered">
        <XrdWizardStep title="adminUsers.addUser.step.roles.selectRoles" sub-title="adminUsers.addUser.step.roles.description">
          <XrdFormBlock>
            <div v-for="(role, idx) in availableRoles" :key="role" :class="{ 'mb-5': idx < availableRoles.length - 1 }">
              <v-checkbox
                v-model="roles"
                class="xrd"
                density="compact"
                hide-details
                :value="role"
                :label="$t(`apiKey.role.${role}`)"
                :data-test="`role-${role}-checkbox`"
              />
            </div>
          </XrdFormBlock>
          <template #footer>
            <XrdBtn data-test="cancel-button" variant="outlined" text="action.cancel" @click="close" />
            <v-spacer />
            <XrdBtn
              data-test="next-button"
              text="action.next"
              append-icon="arrow_forward"
              :disabled="isNextButtonDisabled"
              @click="step++"
            />
          </template>
        </XrdWizardStep>
      </v-stepper-window-item>
      <v-stepper-window-item data-test="add-admin-user-step-2" :value="2" class="pa-0">
        <XrdWizardStep>
          <XrdFormBlock>
            <XrdFormBlockRow full-length>
              <v-text-field
                v-model="username"
                v-bind="usernameAttrs"
                class="xrd"
                type="text"
                :label="$t('adminUsers.addUser.step.credentials.username')"
                data-test="username-input"
              />
            </XrdFormBlockRow>
            <XrdFormBlockRow full-length>
              <v-text-field
                v-model="password"
                v-bind="passwordAttrs"
                class="xrd"
                type="password"
                :label="$t('adminUsers.addUser.step.credentials.password')"
                data-test="password-input"
              />
            </XrdFormBlockRow>
            <XrdFormBlockRow full-length>
              <v-text-field
                v-model="passwordConfirm"
                v-bind="passwordConfirmAttrs"
                class="xrd"
                type="password"
                :label="$t('adminUsers.addUser.step.credentials.passwordConfirm')"
                data-test="confirm-password-input"
              />
            </XrdFormBlockRow>
          </XrdFormBlock>

          <template #footer>
            <XrdBtn data-test="cancel-button" variant="outlined" text="action.cancel" @click="close" />
            <v-spacer />
            <XrdBtn
              data-test="previous-button"
              text="action.previous"
              prepend-icon="arrow_back"
              variant="outlined"
              class="mr-2"
              :disabled="step < 2"
              @click="step--"
            />
            <XrdBtn
              data-test="add-button"
              text="action.add"
              prepend-icon="add_circle"
              :disabled="isAddButtonDisabled"
              :loading="addingUser"
              @click="addUser"
            />
          </template>
        </XrdWizardStep>
      </v-stepper-window-item>
    </XrdWizard>
  </XrdElevatedViewSimple>
</template>

<script lang="ts" setup>
import { ref, computed, PropType } from 'vue';
import { useRouter } from 'vue-router';
import { PublicPathState, useForm } from 'vee-validate';
import { AdminUser } from '../../openapi-types';
import { XrdElevatedViewSimple } from '../../layouts';
import { XrdWizard, XrdWizardStep } from '../../components/wizard';
import { XrdBtn, XrdFormBlock, XrdFormBlockRow } from '../../components';
import { AdminUsersHandler } from '../../types';

const props = defineProps({
  adminUsersHandler: {
    type: Object as PropType<AdminUsersHandler>,
    required: true,
  },
});

const router = useRouter();

const step = ref(1);
const roles = ref<string[]>([]);
const userAdded = ref(false);
const addingUser = ref(false);

const availableRoles = computed(() => props.adminUsersHandler.availableRoles());
const isNextButtonDisabled = computed(() => roles.value.length === 0);
const isAddButtonDisabled = computed(() => !meta.value.dirty || !meta.value.valid);

const close = () => {
  router.back();
};

const validationSchema = computed(() => {
  return {
    username: 'required|min:3|max:30',
    password: 'required|min:6|max:255|confirmed:@passwordConfirm',
    passwordConfirm: 'required',
  };
});

const componentConfig = (state: PublicPathState) => ({
  props: {
    'error-messages': state.errors,
  },
});

const { meta, defineField } = useForm({
  validationSchema,
  initialValues: {
    username: '',
    password: '',
    passwordConfirm: '',
    roles: [],
  },
});

const [username, usernameAttrs] = defineField('username', componentConfig);
const [password, passwordAttrs] = defineField('password', componentConfig);
const [passwordConfirm, passwordConfirmAttrs] = defineField('passwordConfirm', componentConfig);

const addUser = () => {
  addingUser.value = true;
  props.adminUsersHandler
    .add({
      username: username.value,
      password: password.value,
      roles: roles.value,
    } as AdminUser)
    .finally(() => {
      addingUser.value = false;
    });
};
</script>

<style lang="scss" scoped></style>
