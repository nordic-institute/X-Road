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
  <XrdSubView>
    <template #header>
      <XrdBtn
        v-if="allowMemberSubsystemAdd"
        prepend-icon="add_circle"
        data-test="add-subsystem"
        class="ml-auto"
        text="members.member.subsystems.addClient"
        @click="showAddSubsystemDialog = true"
      />
    </template>
    <v-table
      :loading="loading"
      class="xrd bg-surface-container xrd-rounded-12 border subsystems-table"
      data-test="subsystems-table"
    >
      <thead>
        <tr>
          <th>
            {{
              `${$t('members.member.subsystems.subsystemcode')} (${
                subsystems.length
              })`
            }}
          </th>
          <th>{{ $t('members.member.subsystems.subsystemname') }}</th>
          <th>{{ $t('members.member.subsystems.servercode') }}</th>
          <th>{{ $t('members.member.subsystems.serverOwner') }}</th>
          <th>{{ $t('members.member.subsystems.status') }}</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <template v-for="(item, index) in subsystems" :key="index">
          <tr
            v-if="item.used_security_servers.length === 0"
            class="bg-unregistered"
          >
            <td class="unregistered-subsystem">
              <XrdLabelWithIcon
                class="opacity-60"
                icon="folder_copy"
                :label="item.subsystem_id.subsystem_code"
              />
            </td>
            <td class="unregistered-subsystem">
              {{ item.subsystem_name }}
              <rename-subsystem-btn
                v-if="allowMemberSubsystemRename"
                :subsystem-name="item.subsystem_name"
                @click="renameClicked(item)"
              />
            </td>
            <td class="unregistered-subsystem" />
            <td class="unregistered-subsystem" />
            <td class="status unregistered-subsystem">
              <v-chip
                class="xrd opacity-60 font-weight-medium"
                prepend-icon="cancel filled"
                color="primary"
                variant="outlined"
                size="small"
                :text="$t('securityServers.unregistered')"
              />
            </td>
            <td class="subsystem-actions unregistered-subsystem">
              <div>
                <XrdBtn
                  v-if="allowMemberSubsystemDelete"
                  data-test="delete-subsystem"
                  variant="text"
                  color="tertiary"
                  text="action.delete"
                  @click="deleteClicked(item)"
                />
              </div>
            </td>
          </tr>
          <tr
            v-for="(subitem, iSub) in item.used_security_servers"
            :key="`${item.subsystem_id.subsystem_code}:${subitem.server_code}`"
          >
            <td
              :class="{
                'border-0': item.used_security_servers.length - 1 > iSub,
              }"
            >
              <XrdLabelWithIcon
                v-if="iSub === 0"
                icon="folder_copy"
                :label="item.subsystem_id.subsystem_code"
              />
            </td>
            <td
              :class="{
                'border-0': item.used_security_servers.length - 1 > iSub,
              }"
            >
              <span v-if="iSub === 0">
                {{ item.subsystem_name }}
                <rename-subsystem-btn
                  v-if="allowMemberSubsystemRename"
                  :subsystem-name="item.subsystem_name"
                  @click="renameClicked(item)"
                />
              </span>
            </td>
            <td class="text-primary font-weight-medium">
              {{ subitem.server_code }}
            </td>
            <td class="text-primary font-weight-medium">
              {{ subitem.server_owner }}
            </td>
            <td class="status">
              <v-chip
                v-if="subitem.status === 'APPROVED'"
                class="xrd font-weight-medium"
                color="success-container"
                variant="flat"
                size="small"
                :text="$t('securityServers.registered')"
              >
                <template #prepend>
                  <v-icon
                    class="status-icon"
                    icon="check_circle filled"
                    color="success"
                  />
                </template>
              </v-chip>
              <v-chip
                v-if="
                  subitem.status === 'WAITING' ||
                  subitem.status === 'SUBMITTED FOR APPROVAL'
                "
                class="xrd font-weight-medium"
                color="warning-container"
                variant="flat"
                size="small"
                :text="$t('securityServers.pending')"
              >
                <template #prepend>
                  <v-icon
                    class="status-icon"
                    icon="warning filled"
                    color="warning"
                  />
                </template>
              </v-chip>
              <v-chip
                v-if="subitem.status === 'DISABLED'"
                class="xrd font-weight-medium"
                color="warning-container"
                variant="flat"
                size="small"
                :text="$t('securityServers.disabled')"
              >
                <template #prepend>
                  <v-icon
                    class="status-icon"
                    icon="cancel filled"
                    color="warning"
                  />
                </template>
              </v-chip>
              <v-chip
                v-if="subitem.status === undefined"
                class="xrd font-weight-medium"
                color="error-container"
                variant="flat"
                size="small"
                :text="$t('securityServers.unregistered')"
              >
                <template #prepend>
                  <v-icon
                    class="status-icon"
                    icon="error filled"
                    color="error"
                  />
                </template>
              </v-chip>
            </td>
            <td class="subsystem-actions">
              <div>
                <XrdBtn
                  v-if="
                    (subitem.status === 'APPROVED' ||
                      subitem.status === 'DISABLED') &&
                    allowToUnregisterMemberSubsystem
                  "
                  variant="text"
                  color="tertiary"
                  text="action.unregister"
                  @click="unregisterClicked(item, subitem)"
                />

                <XrdBtn
                  v-if="subitem.status === 'WAITING'"
                  variant="text"
                  color="tertiary"
                  text="action.approve"
                />

                <XrdBtn
                  v-if="subitem.status === 'WAITING'"
                  variant="text"
                  color="tertiary"
                  text="action.decline"
                />
              </div>
            </td>
          </tr>
        </template>
      </tbody>
    </v-table>
    <add-member-subsystem-dialog
      v-if="memberStore.current && showAddSubsystemDialog"
      :member="memberStore.current"
      data-test="add-member-to-group"
      @cancel="cancel"
      @save="addedSubsystem"
    />

    <rename-member-subsystem-dialog
      v-if="memberStore.current && clickedSubsystemCode && showRenameDialog"
      :member="memberStore.current"
      :subsystem-code="clickedSubsystemCode"
      :subsystem-name="clickedSubsystemName"
      data-test="rename-subsystem"
      @cancel="cancel"
      @save="renamedSubsystem"
    />

    <delete-member-subsystem-dialog
      v-if="memberStore.current && clickedSubsystemCode && showDeleteDialog"
      :member="memberStore.current"
      :subsystem-code="clickedSubsystemCode"
      data-test="delete-subsystem"
      @cancel="cancel"
      @delete="deletedSubsystem"
    />

    <unregister-member-subsystem-dialog
      v-if="
        memberStore.current &&
        clickedSubsystemCode &&
        clickedServer &&
        showUnregisterDialog
      "
      :member="memberStore.current"
      :server-id="clickedServer.encoded_id!"
      :subsystem-code="clickedSubsystemCode"
      :server-code="clickedServer.server_code!"
      data-test="unregister-subsystem"
      @cancel="cancel"
      @unregistered-subsystem="unregisteredSubsystem"
    />
  </XrdSubView>
</template>

<script lang="ts">
import { defineComponent } from 'vue';

import { mapActions, mapState, mapStores } from 'pinia';

import {
  useNotifications,
  XrdBtn,
  XrdLabelWithIcon,
  XrdSubView,
} from '@niis/shared-ui';

import { Permissions } from '@/global';
import { Subsystem, UsedSecurityServers } from '@/openapi-types';
import { useMember } from '@/store/modules/members';
import { useSubsystem } from '@/store/modules/subsystems';
import { useUser } from '@/store/modules/user';

import RenameSubsystemBtn from './RenameSubsystemBtn.vue';
import AddMemberSubsystemDialog from '@/views/Members/Member/Subsystems/AddMemberSubsystemDialog.vue';
import DeleteMemberSubsystemDialog from '@/views/Members/Member/Subsystems/DeleteMemberSubsystemDialog.vue';
import RenameMemberSubsystemDialog from '@/views/Members/Member/Subsystems/RenameMemberSubsystemDialog.vue';
import UnregisterMemberSubsystemDialog from '@/views/Members/Member/Subsystems/UnregisterMemberSubsystemDialog.vue';

// To provide the Vue instance to debounce
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let that: any;

/**
 * Component for Member Subsystem
 */
export default defineComponent({
  name: 'MemberSubsystems',
  components: {
    RenameSubsystemBtn,
    RenameMemberSubsystemDialog,
    DeleteMemberSubsystemDialog,
    AddMemberSubsystemDialog,
    UnregisterMemberSubsystemDialog,
    XrdSubView,
    XrdBtn,
    XrdLabelWithIcon,
  },
  props: {
    memberId: {
      type: String,
      required: true,
    },
  },
  setup() {
    const { addError } = useNotifications();
    return { addError };
  },
  data() {
    return {
      showAddSubsystemDialog: false,
      showDeleteDialog: false,
      showRenameDialog: false,
      showUnregisterDialog: false,

      loading: false,
      search: '',

      subsystems: [] as Subsystem[],

      clickedSubsystemCode: '',
      clickedSubsystemName: '',
      clickedServer: null as UsedSecurityServers | null,
    };
  },
  computed: {
    ...mapState(useUser, ['hasPermission']),
    ...mapStores(useMember, useSubsystem),
    allowMemberSubsystemAdd(): boolean {
      return this.hasPermission(Permissions.ADD_MEMBER_SUBSYSTEM);
    },
    allowMemberSubsystemDelete(): boolean {
      return this.hasPermission(Permissions.REMOVE_MEMBER_SUBSYSTEM);
    },
    allowMemberSubsystemRename(): boolean {
      return this.hasPermission(Permissions.EDIT_MEMBER_SUBSYSTEM);
    },
    allowToUnregisterMemberSubsystem(): boolean {
      return this.hasPermission(Permissions.UNREGISTER_SUBSYSTEM);
    },
  },
  watch: {
    memberId: {
      immediate: true,
      handler(memberId) {
        this.loading = true;
        this.subsystemStore
          .loadByMemberId(memberId)
          .then((resp) => {
            this.subsystems = resp;
          })
          .catch((error) => this.addError(error))
          .finally(() => (this.loading = false));
      },
    },
  },
  methods: {
    deleteClicked(subsystem: Subsystem) {
      this.clickedSubsystemCode = subsystem.subsystem_id
        ?.subsystem_code as string;
      this.showDeleteDialog = true;
    },
    renameClicked(subsystem: Subsystem) {
      this.clickedSubsystemCode = subsystem.subsystem_id
        ?.subsystem_code as string;
      this.clickedSubsystemName = subsystem.subsystem_name as string;
      this.showRenameDialog = true;
    },
    unregisterClicked(subsystem: Subsystem, subitem: UsedSecurityServers) {
      this.clickedSubsystemCode = subsystem.subsystem_id
        ?.subsystem_code as string;
      this.clickedServer = subitem;
      this.showUnregisterDialog = true;
    },
    addedSubsystem() {
      this.showAddSubsystemDialog = false;
      this.refetchSubsystems();
    },
    deletedSubsystem() {
      this.showDeleteDialog = false;
      this.clickedSubsystemCode = '';
      this.refetchSubsystems();
    },
    renamedSubsystem() {
      this.showRenameDialog = false;
      this.clickedSubsystemCode = '';
      this.clickedSubsystemName = '';
      this.refetchSubsystems();
    },
    unregisteredSubsystem() {
      this.showUnregisterDialog = false;
      this.clickedSubsystemCode = '';
      this.clickedServer = null;
      this.refetchSubsystems();
    },
    cancel() {
      this.showAddSubsystemDialog = false;
      this.showRenameDialog = false;
      this.showDeleteDialog = false;
      this.showUnregisterDialog = false;
      this.clickedSubsystemCode = '';
      this.clickedSubsystemName = '';
      this.clickedServer = null;
    },
    refetchSubsystems() {
      this.loading = true;

      this.subsystemStore
        .loadByMemberId(this.memberId)
        .then((resp) => {
          this.subsystems = resp;
        })
        .catch((error) => this.addError(error))
        .finally(() => (this.loading = false));
    },
  },
});
</script>

<style lang="scss" scoped>
.status-icon {
  margin: 0 6px 0 -6px;
}

.bg-unregistered {
  background-color: rgba(var(--v-theme-on-surface-variant), 0.08) !important;
}
</style>
