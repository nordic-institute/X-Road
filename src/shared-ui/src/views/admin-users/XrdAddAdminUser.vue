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
  <xrd-sub-view-container data-test="add-admin-user-stepper-view">
    <!-- eslint-disable-next-line vuetify/no-deprecated-components -->
    <v-stepper v-model="step" :alt-labels="true" class="stepper mt-2">
      <xrd-sub-view-title :title="$t('adminUsers.addUser.title')" :show-close="true" class="pa-4" @close="close"></xrd-sub-view-title>

      <v-stepper-header class="stepper-header">
        <v-stepper-item :complete="step > 1" :value="1">{{ $t('adminUsers.addUser.step.roles.name') }}</v-stepper-item>
        <v-divider />
        <v-stepper-item :complete="userAdded" :value="2">{{ $t('adminUsers.addUser.step.credentials.name') }}</v-stepper-item>
      </v-stepper-header>
      <v-stepper-window>
        <v-stepper-window-item data-test="add-admin-user-step-1" :value="1" class="pa-0 centered">
          <v-container class="wide-width">
            <v-row class="mt-4">
              <v-col
                ><h3>{{ $t('adminUsers.addUser.step.roles.name') }}</h3></v-col
              >
            </v-row>
          </v-container>
          <v-container class="narrow-width">
            <v-row class="mb-5">
              <v-col>
                <h4>{{ $t('adminUsers.addUser.step.roles.selectRoles') }}</h4>
                <br />
                {{ $t('adminUsers.addUser.step.roles.description') }}
              </v-col>
              <v-col>
                <v-row v-for="role in availableRoles" :key="role" no-gutters>
                  <v-col>
                    <v-checkbox
                      v-model="roles"
                      height="10px"
                      :value="role"
                      :label="$t(`adminUsers.role.${role}`)"
                      :data-test="`role-${role}-checkbox`"
                    />
                  </v-col>
                </v-row>
              </v-col>
            </v-row>
          </v-container>

          <v-row class="button-footer mt-12" no-gutters>
            <xrd-button data-test="cancel-button" outlined @click="close">
              {{ $t('action.cancel') }}
            </xrd-button>

            <xrd-button data-test="next-button" :disabled="isNextButtonDisabled" @click="step++">
              {{ $t('action.next') }}
            </xrd-button>
          </v-row>
        </v-stepper-window-item>
        <v-stepper-window-item data-test="add-admin-user-step-2" :value="2" class="pa-0">
          <div class="wizard-step-form-content">
            <div class="wizard-row-wrap">
              <div class="wizard-label">
                {{ $t('adminUsers.addUser.step.credentials.username') }}
              </div>

              <div>
                <v-text-field
                  v-model="username"
                  v-bind="usernameAttrs"
                  class="wizard-form-input"
                  type="text"
                  :label="$t('adminUsers.addUser.step.credentials.username')"
                  variant="outlined"
                  data-test="username-input"
                ></v-text-field>
              </div>
            </div>
          </div>
          <div class="wizard-step-form-content">
            <div class="wizard-row-wrap">
              <div class="wizard-label">
                {{ $t('adminUsers.addUser.step.credentials.password') }}
              </div>

              <div>
                <v-text-field
                  v-model="password"
                  v-bind="passwordAttrs"
                  class="wizard-form-input"
                  type="password"
                  :label="$t('adminUsers.addUser.step.credentials.password')"
                  variant="outlined"
                  data-test="password-input"
                ></v-text-field>
              </div>
            </div>
          </div>
          <div class="wizard-step-form-content">
            <div class="wizard-row-wrap">
              <div class="wizard-label">
                {{ $t('adminUsers.addUser.step.credentials.passwordConfirm') }}
              </div>

              <div>
                <v-text-field
                  v-model="passwordConfirm"
                  v-bind="passwordConfirmAttrs"
                  class="wizard-form-input"
                  type="password"
                  :label="$t('adminUsers.addUser.step.credentials.passwordConfirm')"
                  variant="outlined"
                  data-test="confirm-password-input"
                ></v-text-field>
              </div>
            </div>
          </div>

          <v-row class="button-footer mt-12" no-gutters>
            <xrd-button data-test="cancel-button" outlined @click="close">
              {{ $t('action.cancel') }}
            </xrd-button>

            <xrd-button data-test="previous-button" outlined class="mr-5" :disabled="step < 2" @click="step--">
              {{ $t('action.previous') }}
            </xrd-button>
            <xrd-button data-test="add-button" :disabled="isAddButtonDisabled" :loading="addingUser" @click="addUser">
              {{ $t('action.add') }}
            </xrd-button>
          </v-row>
        </v-stepper-window-item>
      </v-stepper-window>
    </v-stepper>
  </xrd-sub-view-container>
</template>

<script lang="ts" setup>
import { ref, computed, inject } from 'vue';
import { useRouter } from 'vue-router';
import { PublicPathState, useForm } from 'vee-validate';
import { key } from '../../utils';
import { AdminUser } from '@/openapi-types';

const adminUsersHandler = inject(key.adminUsersHandler)!;

const router = useRouter();

const step = ref(1);
const roles = ref<string[]>([]);
const userAdded = ref(false);
const addingUser = ref(false);

const availableRoles = computed(() => adminUsersHandler.availableRoles());
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
  adminUsersHandler
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

<style lang="scss" scoped>
@use '@niis/shared-ui/src/assets/wizards';
@use '@niis/shared-ui/src/assets/colors';

.stepper-header {
  box-shadow: unset;
  width: 50%;
  margin: auto;
}

.wide-width {
  max-width: 1040px;
}

.narrow-width {
  max-width: 840px;
}

h3 {
  color: colors.$Black100;
  font-size: 18px;
  font-weight: 700;
}

h4 {
  color: colors.$Black100;
  font-size: 14px;
  font-weight: 700;
}
</style>
