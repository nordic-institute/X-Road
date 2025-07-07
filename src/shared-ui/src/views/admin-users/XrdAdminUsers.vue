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
  <XrdTitledView title-key="tab.settings.adminUsers" data-test="admin-users-view">
    <template #header-buttons>
      <xrd-button v-if="canAdd()" data-test="add-admin-user-button" @click="addUser">
        <xrd-icon-base class="xrd-large-button-icon">
          <XrdIconAdd />
        </xrd-icon-base>
        {{ $t('adminUsers.addUser.button') }}
      </xrd-button>
    </template>

    <!-- Table -->
    <v-data-table
      :loading="loading"
      :headers="headers"
      :items="users"
      :search="search"
      :must-sort="true"
      :items-per-page="-1"
      class="elevation-0 data-table"
      item-key="id"
      :loader-height="2"
      data-test="admin-users-table"
      hide-default-footer
    >
      <template #[`item.id`]="{ item }">
        <div class="username">
          {{ item.username }}
        </div>
      </template>

      <template #[`item.roles`]="{ item }">
        <span :data-test="`admin-user-row-${item.username}-roles`">
          {{ commaSeparate(translateRoles(item.roles)) }}
        </span>
      </template>

      <template #[`item.button`]="{ item }">
        <div class="button-wrap">
          <xrd-button
            v-if="canEdit()"
            text
            :data-test="`admin-user-row-${item.username}-edit-button`"
            :outlined="false"
            @click="showRolesEdit(item)"
            >{{ $t('action.edit') }}
          </xrd-button>

          <xrd-button
            v-if="canEdit()"
            text
            :data-test="`admin-user-row-${item.username}-change-password-button`"
            :outlined="false"
            @click="showPasswordChange(item)"
          >
            <xrd-icon-base>
              <XrdIconKey />
            </xrd-icon-base>
          </xrd-button>

          <xrd-button
            v-if="canDelete(item)"
            text
            :data-test="`admin-user-row-${item.username}-delete-button`"
            :outlined="false"
            @click="showDeleteConfirmation(item)"
          >
            {{ $t('action.delete') }}
          </xrd-button>
        </div>
      </template>

      <template #bottom>
        <div class="custom-footer"></div>
      </template>
    </v-data-table>

    <!-- Edit Roles dialog -->
    <xrd-simple-dialog
      v-if="showRolesEditDialog"
      title="adminUsers.dialog.editRoles.title"
      :dialog="showRolesEditDialog"
      save-button-text="action.save"
      :disable-save="selectedRoles.length === 0"
      :loading="savingChanges"
      @save="saveRoles"
      @cancel="showRolesEditDialog = false"
    >
      <template #title>
        <span class="text-h5" :data-test="`admin-user-row-${selectedUser?.username}-edit-dialog-title`">
          {{ $t('adminUsers.table.action.edit.dialog.title', { username: selectedUser?.username }) }}
        </span>
      </template>
      <template #content>
        <div :data-test="`admin-user-row-${selectedUser?.username}-edit-dialog-content`">
          <v-row class="mt-12">
            <v-col>
              {{ $t('adminUsers.table.action.edit.dialog.message') }}
            </v-col>
          </v-row>
          <v-row v-for="role in rolesToEdit" :key="role" no-gutters>
            <v-col class="checkbox-wrapper">
              <v-checkbox v-model="selectedRoles" height="10px" :value="role" :data-test="`role-${role}-checkbox`">
                <template #label>
                  <span>{{ $t(`adminUsers.role.${role}`) }}</span>
                  <span v-if="!adminUsersHandler.hasRole(role)" class="remove-only-role">
                    &nbsp;{{ $t('adminUsers.edit.roleRemoveOnly') }}
                  </span>
                </template>
              </v-checkbox>
            </v-col>
          </v-row>
        </div>
      </template>
    </xrd-simple-dialog>

    <xrd-admin-user-password-change-dialog
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
  </XrdTitledView>
</template>

<script lang="ts" setup>
import { ref, computed, onMounted, inject } from 'vue';
import { useI18n } from 'vue-i18n';
import { AdminUser } from '@/openapi-types';
import { key, XrdIconKey} from '@niis/shared-ui';
import { XrdAdminUserPasswordChangeDialog } from '@niis/shared-ui';

const { t } = useI18n();

const headers = computed<unknown[]>(() => [
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
    sortable: false,
  },
]);

const adminUsersHandler = inject(key.adminUsersHandler)!;
const currentUser = inject(key.user)!;

const users = ref<AdminUser[]>([]);
const search = ref('');
const loading = ref(false);
const selectedUser = ref<AdminUser | undefined>();
const selectedRoles = ref<string[]>([]);
const showRolesEditDialog = ref(false);
const showPasswordChangeDialog = ref(false);
const showDeleteConfirmationDialog = ref(false);
const savingChanges = ref(false);
const rolesToEdit = ref<string[]>([]);

const canAdd = () => adminUsersHandler.canAdd();
const canEdit = () => adminUsersHandler.canEdit();
const canDelete = (adminUser: AdminUser) => adminUsersHandler.canDelete(adminUser);

const translateRoles = (roles: string[]): string[] => roles.map((role) => t(`adminUsers.role.${role}`) as string);

const isOldPasswordRequired = () => selectedUser.value?.username === currentUser.username();

const showPasswordChange = (user: AdminUser) => {
  selectedUser.value = user;
  showPasswordChangeDialog.value = true;
};

const showRolesEdit = (user: AdminUser) => {
  selectedUser.value = user;
  selectedRoles.value = [...user.roles];
  rolesToEdit.value = adminUsersHandler
    .availableRoles()
    .filter((role) => selectedRoles.value.includes(role) || adminUsersHandler.hasRole(role));
  showRolesEditDialog.value = true;
};

const showDeleteConfirmation = (user: AdminUser) => {
  selectedUser.value = user;
  showDeleteConfirmationDialog.value = true;
};

const addUser = () => {
  adminUsersHandler.navigateToAddUser();
};

const commaSeparate = (value: string[]): string => {
  return value.join(', ');
};

const loadUsers = () => {
  loading.value = true;
  adminUsersHandler
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
  adminUsersHandler.updateRoles(selectedUser.value!.username, selectedRoles.value).finally(() => {
    savingChanges.value = false;
    showRolesEditDialog.value = false;
    loadUsers();
  });
};

const deleteUser = () => {
  adminUsersHandler.delete(selectedUser.value!.username).finally(() => {
    showDeleteConfirmationDialog.value = false;
    savingChanges.value = false;
    loadUsers();
  });
};

onMounted(() => {
  loadUsers();
});
</script>

<style lang="scss" scoped>
@use '@niis/shared-ui/src/assets/tables';
@use '@niis/shared-ui/src/assets/colors';

.username {
  color: colors.$Purple100;
  font-weight: 600;
  font-size: 14px;
  display: flex;
  align-items: center;
}
</style>
