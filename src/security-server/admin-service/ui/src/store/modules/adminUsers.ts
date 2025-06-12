/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
import { useNotifications } from '@/store/modules/notifications';
import { useRouter } from 'vue-router';
import { useUser } from '@/store/modules/user';
import * as api from '@/util/api';
import {
  AdminUser,
  AdminUserPasswordChangeRequest,
  AdminUsersHandler,
  i18n,
} from '@niis/shared-ui';

import { Permissions, Roles, RouteName } from '@/global';

export function useAdminUsersHandler() {
  const { showError, showSuccess } = useNotifications();
  const router = useRouter();
  const { username, hasPermission, hasRole } = useUser();

  const fetchUsers = () => {
    return api
      .get<AdminUser[]>('/users')
      .then((resp) => {
        return resp.data;
      })
      .catch((err) => showError(err));
  };

  const deleteUser = (username: string) => {
    return api
      .remove(`/users/${api.encodePathParameter(username)}`)
      .then(() => {
        showSuccess(
          i18n.global.t('adminUsers.table.action.delete.success', {
            username: username,
          }),
        );
      })
      .catch((err) => showError(err));
  };

  const saveRoles = (username: string, roles: string[]) => {
    return api
      .put(`/users/${api.encodePathParameter(username)}/roles`, roles)
      .then(() => {
        showSuccess(
          i18n.global.t('adminUsers.table.action.edit.success', {
            username: username,
          }),
        );
      })
      .catch((err) => showError(err));
  };

  const changePassword = (
    username: string,
    oldPassword: string,
    newPassword: string,
  ) => {
    return api
      .put(`/users/${api.encodePathParameter(username)}/password`, {
        old_password: oldPassword,
        new_password: newPassword,
      } as AdminUserPasswordChangeRequest)
      .then(() => {
        showSuccess(
          i18n.global.t('adminUsers.table.action.changePassword.success', {
            username: username,
          }),
        );
      })
      .catch((err) => showError(err));
  };

  const addUser = (user: AdminUser) => {
    return api
      .post<AdminUser>('/users', user)
      .then(() => {
        router.push({ name: RouteName.AdminUsers });
        showSuccess(
          i18n.global.t('adminUsers.addUser.success', {
            username: user.username,
          }),
        );
      })
      .catch((err) => showError(err));
  };

  const canCreate = () => hasPermission(Permissions.ADD_ADMIN_USER);
  const canEdit = () => hasPermission(Permissions.UPDATE_ADMIN_USER);
  const canDelete = (user: AdminUser) =>
    hasPermission(Permissions.DELETE_ADMIN_USER) && username !== user.username;
  const availableRoles = () => Roles.filter((role) => hasRole(role));
  const navigateToAddUser = () => router.push({ name: RouteName.AddAdminUser });

  const adminUsersHandler = (): AdminUsersHandler => {
    return {
      fetchAll: fetchUsers,
      add: addUser,
      updateRoles: saveRoles,
      delete: deleteUser,
      changePassword: changePassword,
      canCreate: canCreate,
      canEdit: canEdit,
      canDelete: canDelete,
      hasRole: hasRole,
      availableRoles: availableRoles,
      navigateToAddUser: navigateToAddUser,
    };
  };

  return { adminUsersHandler };
}
