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
  <XrdView :title="title" data-test="admin-users-view">
    <template v-if="$slots.tabs" #tabs>
      <slot name="tabs" />
    </template>
    <XrdSubView>
      <template #header>
        <v-spacer />
        <XrdBtn
          v-if="canAdd()"
          data-test="add-admin-user-button"
          text="adminUsers.addUser.button"
          prepend-icon="add_circle"
          @click="addUser"
        />
      </template>

      <!-- Table -->
      <v-data-table
        :loading="loading"
        :headers="headers"
        :items="users"
        :must-sort="true"
        :items-per-page="-1"
        class="xrd bg-surface-container xrd-rounded-16"
        item-key="id"
        :loader-height="2"
        data-test="admin-users-table"
        hide-default-footer
      >
        <template #[`item.id`]="{ item }">
          <div class="username">
            <XrdLabelWithIcon data-test="username" icon="person" semi-bold :label="item.username" />
          </div>
        </template>

        <template #[`item.roles`]="{ item }">
          <span :data-test="`admin-user-row-${item.username}-roles`">
            {{ commaSeparate(translateRoles(item.roles)) }}
          </span>
        </template>

        <template #[`item.button`]="{ item }">
          <div class="button-wrap">
            <XrdBtn
              v-if="canEdit()"
              variant="text"
              text="action.edit"
              color="tertiary"
              :data-test="`admin-user-row-${item.username}-edit-button`"
              @click="showRolesEdit(item)"
            />

            <XrdBtn
              v-if="canEdit()"
              color="tertiary"
              :data-test="`admin-user-row-${item.username}-change-password-button`"
              variant="text"
              @click="showPasswordChange(item)"
            >
              <v-icon icon="lock_reset" />
            </XrdBtn>

            <XrdBtn
              v-if="canDelete(item)"
              variant="text"
              text="action.delete"
              color="tertiary"
              :data-test="`admin-user-row-${item.username}-delete-button`"
              @click="showDeleteConfirmation(item)"
            />
          </div>
        </template>
      </v-data-table>

      <!-- Edit Roles dialog -->
      <xrd-simple-dialog
        v-if="showRolesEditDialog"
        :translated-title="$t('adminUsers.table.action.edit.dialog.title', { username: selectedUser?.username })"
        :dialog="showRolesEditDialog"
        save-button-text="action.save"
        :disable-save="selectedRoles.length === 0"
        :loading="savingChanges"
        @save="saveRoles"
        @cancel="showRolesEditDialog = false"
      >
        <template #text>
          {{ $t('adminUsers.table.action.edit.dialog.message') }}
        </template>
        <template #content>
          <XrdFormBlock :data-test="`admin-user-row-${selectedUser?.username}-edit-dialog-content`">
            <v-checkbox
              v-for="role in rolesToEdit"
              :key="role"
              v-model="selectedRoles"
              class="xrd"
              hide-details
              height="10px"
              :value="role"
              :data-test="`role-${role}-checkbox`"
            >
              <template #label>
                <span>{{ $t(`adminUsers.role.${role}`) }}</span>
                <span v-if="!adminUsersHandler.hasRole(role)" class="remove-only-role">
                  &nbsp;{{ $t('adminUsers.edit.roleRemoveOnly') }}
                </span>
              </template>
            </v-checkbox>
          </XrdFormBlock>
        </template>
      </xrd-simple-dialog>

      <XrdAdminUserPasswordChangeDialog
        v-if="showPasswordChangeDialog"
        :require-old-password="isOldPasswordRequired()"
        :username="selectedUser!.username"
        :admin-users-handler="adminUsersHandler"
        @cancel="showPasswordChangeDialog = false"
        @password-changed="showPasswordChangeDialog = false"
      />

      <!-- Confirm delete dialog -->
      <xrd-confirm-dialog
        v-if="showDeleteConfirmationDialog"
        :data-test="`admin-user-row-${selectedUser?.username}-delete-confirmation`"
        :dialog="showDeleteConfirmationDialog"
        title="adminUsers.table.action.delete.confirmationDialog.title"
        text="adminUsers.table.action.delete.confirmationDialog.message"
        :data="{ username: selectedUser?.username }"
        :loading="savingChanges"
        @cancel="showDeleteConfirmationDialog = false"
        @accept="deleteUser"
      />
    </XrdSubView>
  </XrdView>
</template>

<script lang="ts" setup>
import { ref, computed, onMounted, PropType } from 'vue';
import { useI18n } from 'vue-i18n';
import { AdminUser } from '@/openapi-types';
import { AdminUsersHandler } from '../../types';
import { DataTableHeader } from 'vuetify/lib/components/VDataTable/types';
import { XrdView, XrdSubView } from '../../layouts';
import { XrdBtn, XrdFormBlock, XrdLabelWithIcon } from '../../components';
import { XrdAdminUserPasswordChangeDialog } from '../../components/admin-users';

const props = defineProps({
  adminUsersHandler: {
    type: Object as PropType<AdminUsersHandler>,
    required: true,
  },
  username: {
    type: String,
    required: true,
  },
  title: {
    type: String,
    required: true,
  },
});

const { t } = useI18n();

const headers = computed<DataTableHeader[]>(() => [
  {
    title: t('adminUsers.table.header.username') as string,
    align: 'start',
    key: 'id',
  },
  {
    title: t('adminUsers.table.header.roles') as string,
    align: 'start',
    key: 'roles',
  },
  {
    title: '',
    key: 'button',
    align: 'end',
    sortable: false,
  },
]);

const users = ref<AdminUser[]>([]);
const loading = ref(false);
const selectedUser = ref<AdminUser | undefined>();
const selectedRoles = ref<string[]>([]);
const showRolesEditDialog = ref(false);
const showPasswordChangeDialog = ref(false);
const showDeleteConfirmationDialog = ref(false);
const savingChanges = ref(false);
const rolesToEdit = ref<string[]>([]);

const canAdd = () => props.adminUsersHandler.canAdd();
const canEdit = () => props.adminUsersHandler.canEdit();
const canDelete = (adminUser: AdminUser) => props.adminUsersHandler.canDelete(adminUser);

const translateRoles = (roles: string[]): string[] => roles.map((role) => t(`adminUsers.role.${role}`) as string);

const isOldPasswordRequired = () => selectedUser.value?.username === props.username;

const showPasswordChange = (user: AdminUser) => {
  selectedUser.value = user;
  showPasswordChangeDialog.value = true;
};

const showRolesEdit = (user: AdminUser) => {
  selectedUser.value = user;
  selectedRoles.value = [...user.roles];
  rolesToEdit.value = props.adminUsersHandler
    .availableRoles()
    .filter((role) => selectedRoles.value.includes(role) || props.adminUsersHandler.hasRole(role));
  showRolesEditDialog.value = true;
};

const showDeleteConfirmation = (user: AdminUser) => {
  selectedUser.value = user;
  showDeleteConfirmationDialog.value = true;
};

const addUser = () => {
  props.adminUsersHandler.navigateToAddUser();
};

const commaSeparate = (value: string[]): string => {
  return value.join(', ');
};

const loadUsers = () => {
  loading.value = true;
  props.adminUsersHandler
    .fetchAll()
    .then((result) => {
      if (result) {
        users.value = result;
      }
    })
    .finally(() => {
      loading.value = false;
    });
};

const saveRoles = () => {
  props.adminUsersHandler.updateRoles(selectedUser.value!.username, selectedRoles.value).finally(() => {
    savingChanges.value = false;
    showRolesEditDialog.value = false;
    loadUsers();
  });
};

const deleteUser = () => {
  props.adminUsersHandler.delete(selectedUser.value!.username).finally(() => {
    showDeleteConfirmationDialog.value = false;
    savingChanges.value = false;
    loadUsers();
  });
};

onMounted(() => {
  loadUsers();
});
</script>

<style lang="scss" scoped></style>
