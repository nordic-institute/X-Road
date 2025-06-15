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
    title="adminUsers.dialog.ChangePassword.dialog.title"
    save-button-text="action.save"
    :disable-save="isChangePasswordButtonDisabled"
    :loading="savingChanges"
    @save="changePassword"
    @cancel="emit('cancel')"
  >
    <template #title>
      <span class="text-h5" :data-test="`admin-user-row-${username}-edit-dialog-title`">
        {{ $t('adminUsers.table.action.changePassword.dialog.title', { username: username }) }}
      </span>
    </template>
    <template #content>
      <div :data-test="`admin-users-row-${username}-change-password-dialog-content`">
        <v-text-field
          v-model="oldPassword"
          v-bind="oldPasswordAttrs"
          type="password"
          :label="$t('adminUsers.table.action.changePassword.dialog.oldPassword')"
          variant="outlined"
          data-test="old-password-input"
        ></v-text-field>
        <v-text-field
          v-model="newPassword"
          v-bind="newPasswordAttrs"
          type="password"
          :label="$t('adminUsers.table.action.changePassword.dialog.newPassword')"
          variant="outlined"
          data-test="new-password-input"
        ></v-text-field>
        <v-text-field
          v-model="newPasswordConfirmation"
          v-bind="newPasswordConfirmationAttrs"
          type="password"
          :label="$t('adminUsers.table.action.changePassword.dialog.newPasswordConfirmation')"
          variant="outlined"
          data-test="new-password-confirm-input"
        ></v-text-field>
      </div>
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts" setup>
import { computed, PropType, ref } from 'vue';
import { PublicPathState, useForm } from 'vee-validate';
import { AdminUsersHandler } from '@/utils';

const props = defineProps({
  username: {
    type: String,
    required: true,
  },
  adminUsersHandler: {
    type: Object as PropType<AdminUsersHandler>,
    required: true,
  },
});

const emit = defineEmits(['cancel', 'password-changed']);
const savingChanges = ref(false);

const passwordChangeValidationSchema = computed(() => {
  return {
    oldPassword: 'required|max:255',
    newPassword: 'required|confirmed:@newPasswordConfirmation',
    newPasswordConfirmation: 'required',
  };
});

const componentConfig = (state: PublicPathState) => ({
  props: {
    'error-messages': state.errors,
  },
});

const { meta, defineField } = useForm({
  validationSchema: passwordChangeValidationSchema,
  initialValues: {
    oldPassword: '',
    newPassword: '',
    newPasswordConfirmation: '',
  },
});

const [oldPassword, oldPasswordAttrs] = defineField('oldPassword', componentConfig);
const [newPassword, newPasswordAttrs] = defineField('newPassword', componentConfig);
const [newPasswordConfirmation, newPasswordConfirmationAttrs] = defineField('newPasswordConfirmation', componentConfig);

const isChangePasswordButtonDisabled = computed(() => !meta.value.dirty || !meta.value.valid);

const changePassword = () => {
  savingChanges.value = true;
  props.adminUsersHandler.changePassword(props.username, oldPassword.value as string, newPassword.value as string).finally(() => {
    savingChanges.value = false;
    emit('password-changed');
  });
};
</script>
