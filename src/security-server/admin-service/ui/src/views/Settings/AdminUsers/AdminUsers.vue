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
      <xrd-button
        v-if="canCreate"
        data-test="create-admin-user-button"
        @click="addUser"
      >
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
      hide-default-footer
    >

      <template #[`item.id`]="{ item }">
        <div class="username">
          {{ item.username }}
        </div>
      </template>

      <template #[`item.roles`]="{ item }">
        <span :data-test="`admin-user-row-${item.id}-roles`">
          {{ $filters.commaSeparate(translateRoles(item.roles)) }}
        </span>
      </template>

      <template #[`item.button`]="{ item }">
        <div class="button-wrap">
          <xrd-button
            v-if="canEdit"
            text
            :data-test="`admin-user-row-${item.id}-edit-button`"
            :outlined="false"
            @click="showRolesEdit(item)"
          >{{ $t('action.edit') }}
          </xrd-button>

          <xrd-button
            v-if="canEdit"
            text
            :data-test="`admin-user-row-${item.id}-change-password-button`"
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
            :data-test="`admin-user-row-${item.id}-delete-button`"
            :outlined="false"
            @click="showDeleteConfirmation(item)"
          >{{ $t('action.delete') }}
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
        <span
          class="text-h5"
          :data-test="`admin-user-row-${selectedUser?.id}-edit-dialog-title`"
        >
          {{
            $t('adminUsers.table.action.edit.dialog.title', { username: selectedUser?.username })
          }}
        </span>
      </template>
      <template #content>
        <div :data-test="`admin-users-row-${selectedUser?.id}-edit-dialog-content`">
          <v-row class="mt-12">
            <v-col>
              {{ $t('adminUsers.table.action.edit.dialog.message') }}
            </v-col>
          </v-row>
          <v-row v-for="role in rolesToEdit" :key="role" no-gutters>
            <v-col class="checkbox-wrapper">
              <v-checkbox
                v-model="selectedRoles"
                height="10px"
                :value="role"
                :data-test="`role-${role}-checkbox`"
              >
                <template #label>
                  <span>{{ $t(`adminUsers.role.${role}`) }}</span>
                  <span v-if="!hasRole(role)" class="remove-only-role">
                    &nbsp;{{ $t('adminUsers.edit.roleRemoveOnly') }}
                  </span>
                </template>
              </v-checkbox>
            </v-col>
          </v-row>
        </div>
      </template>
    </xrd-simple-dialog>

    <AdminUserPasswordChangeDialog
      v-if="showPasswordChangeDialog"
      :username="selectedUser!.username"
      @cancel="showPasswordChangeDialog = false"
      @password-changed="showPasswordChangeDialog = false"
    />

    <!-- Confirm delete dialog -->
    <xrd-confirm-dialog
      v-if="showDeleteConfirmationDialog"
      :data-test="`admin-user-row-${selectedUser?.id}-delete-confirmation`"
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
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { useNotifications } from '@/store/modules/notifications';
import { useUser } from '@/store/modules/user';
import { Permissions, Roles, RouteName } from '@/global';
import * as api from '@/util/api';
import { DataTableHeader } from '@/ui-types';
import { useI18n } from "vue-i18n";
import { AdminUser } from "@/openapi-types";
import { XrdIconKey } from "@niis/shared-ui";
import AdminUserPasswordChangeDialog from "@/components/user/AdminUserPasswordChangeDialog.vue";

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
    sortable: false,
  },
]);

const { t } = useI18n();
const { username, hasPermission, hasRole } = useUser();
const { showError, showSuccess } = useNotifications();
const router = useRouter();

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

const canCreate = computed(() => hasPermission(Permissions.ADD_ADMIN_USER));
const canEdit = computed(() => hasPermission(Permissions.UPDATE_ADMIN_USER));
const canDelete = (user: AdminUser) => hasPermission(Permissions.DELETE_ADMIN_USER) && username !== user.username;

const loadUsers = () => {
  if (hasPermission(Permissions.VIEW_ADMIN_USERS)) {
    loading.value = true;
    api
      .get<AdminUser[]>('/users')
      .then((resp) => {
        users.value = resp.data;
      })
      .catch((err) => showError(err))
      .finally(() => {
        loading.value = false;
      });
  }
};

const translateRoles = (roles: string[]): string[] =>
  roles.map((role) => t(`adminUsers.role.${role}`) as string);

const showPasswordChange = (user: AdminUser) => {
  selectedUser.value = user;
  showPasswordChangeDialog.value = true;
};

const showRolesEdit = (user: AdminUser) => {
  selectedUser.value = user;
  selectedRoles.value = [...user.roles];
  rolesToEdit.value = Roles.filter(
    (role) => selectedRoles.value.includes(role) || hasRole(role),
  );
  showRolesEditDialog.value = true;
};

const showDeleteConfirmation = (user: AdminUser) => {
  selectedUser.value = user;
  showDeleteConfirmationDialog.value = true;
};

const addUser = () => {
  router.push({ name: RouteName.AddAdminUser });
};

const saveRoles = () => {
  if (!selectedUser.value) return;
  savingChanges.value = true;

  api
    .put(
      `/users/${api.encodePathParameter(selectedUser.value.username)}/roles`,
      selectedRoles.value,
    )
    .then(() => {
      showSuccess(t('adminUsers.table.action.edit.success',{ username: selectedUser?.value?.username }));
    })
    .catch((err) => showError(err))
    .finally(() => {
      savingChanges.value = false;
      showRolesEditDialog.value = false;
      loadUsers();
    });
};

const deleteUser = () => {
  if (!selectedUser.value) return;
  savingChanges.value = true;

  api
    .remove(`/users/${api.encodePathParameter(selectedUser.value.id!)}`)
    .then(() => {
      showSuccess(t('adminUsers.table.action.delete.success', { username: selectedUser?.value?.username }));
    })
    .catch((err) => showError(err))
    .finally(() => {
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
@use '@/assets/tables';
@use '@/assets/colors';

.username {
  color: colors.$Purple100;
  font-weight: 600;
  font-size: 14px;
  display: flex;
  align-items: center;
}

</style>
