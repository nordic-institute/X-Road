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
  <main>
    <v-card class="mt-8" flat>
      <!-- Table -->
      <v-table
        :loading="loading"
        class="elevation-0 data-table subsystems-table"
        data-test="subsystems-table"
      >
        <template #top>
          <data-table-toolbar>
            <template #default>
              <xrd-button
                v-if="allowMemberSubsystemAdd"
                outlined
                data-test="add-subsystem"
                @click="showAddSubsystemDialog = true"
              >
                <xrd-icon-base class="xrd-large-button-icon">
                  <xrd-icon-add />
                </xrd-icon-base>
                {{ $t('members.member.subsystems.addClient') }}
              </xrd-button>
            </template>
          </data-table-toolbar>
        </template>
        <thead>
          <tr>
            <th>
              {{
                `${$t('members.member.subsystems.subsystemcode')} (${
                  subsystems.length
                })`
              }}
            </th>
            <th>{{ $t('members.member.subsystems.servercode') }}</th>
            <th>{{ $t('members.member.subsystems.serverOwner') }}</th>
            <th>{{ $t('members.member.subsystems.status') }}</th>
            <th></th>
          </tr>
        </thead>
        <tbody v-for="(item, index) in subsystems" :key="index">
          <tr v-if="item.used_security_servers.length === 0" class="">
            <td class="unregistered-subsystem">
              {{ item.subsystem_id.subsystem_code }}
            </td>
            <td class="unregistered-subsystem" />
            <td class="unregistered-subsystem" />
            <td class="status unregistered-subsystem">
              <xrd-icon-base>
                <xrd-icon-error />
              </xrd-icon-base>
              {{ getStatusText(undefined) }}
            </td>
            <td class="subsystem-actions unregistered-subsystem">
              <div>
                <xrd-button
                  v-if="allowMemberSubsystemDelete"
                  text
                  :outlined="false"
                  data-test="delete-subsystem"
                  @click="deleteClicked(item)"
                >
                  {{ $t('action.delete') }}
                </xrd-button>
              </div>
            </td>
          </tr>
          <tr
            v-for="(subitem, iSub) in item.used_security_servers"
            :key="`${item.subsystem_id.subsystem_code}:${subitem.server_code}`"
            class=""
          >
            <td v-if="iSub === 0" :rowspan="item.used_security_servers.length">
              {{ item.subsystem_id.subsystem_code }}
            </td>
            <td>{{ subitem.server_code }}</td>
            <td>{{ subitem.server_owner }}</td>
            <td class="status">
              <xrd-icon-base>
                <XrdIconChecked
                  v-if="subitem.status === 'APPROVED'"
                  :color="colors.Success100"
                />
                <xrd-icon-in-progress
                  v-if="
                    subitem.status === 'WAITING' ||
                    subitem.status === 'SUBMITTED FOR APPROVAL'
                  "
                  :color="colors.Success100"
                />
                <xrd-icon-error
                  v-if="subitem.status === 'DISABLED'"
                  :color="colors.WarmGrey100"
                />
                <xrd-icon-error v-if="subitem.status === undefined" />
              </xrd-icon-base>
              {{ getStatusText(subitem.status) }}
            </td>
            <td class="subsystem-actions">
              <div>
                <xrd-button
                  v-if="
                    (subitem.status === 'APPROVED' ||
                      subitem.status === 'DISABLED') &&
                    allowToUnregisterMemberSubsystem
                  "
                  text
                  :outlined="false"
                  @click="unregisterClicked(item, subitem)"
                >
                  {{ $t('action.unregister') }}
                </xrd-button>

                <xrd-button
                  v-if="subitem.status === 'WAITING'"
                  text
                  :outlined="false"
                >
                  {{ $t('action.approve') }}
                </xrd-button>

                <xrd-button
                  v-if="subitem.status === 'WAITING'"
                  text
                  :outlined="false"
                >
                  {{ $t('action.decline') }}
                </xrd-button>
              </div>
            </td>
          </tr>
        </tbody>

        <template #bottom>
          <custom-data-table-footer />
        </template>
      </v-table>
    </v-card>
    <add-member-subsystem-dialog
      v-if="showAddSubsystemDialog"
      :member="memberStore.currentMember"
      data-test="add-member-to-group"
      @cancel="cancel"
      @added-subsystem="addedSubsystem"
    />

    <delete-member-subsystem-dialog
      v-if="clickedSubsystemCode && showDeleteDialog"
      :member="memberStore.currentMember"
      :subsystem-code="clickedSubsystemCode"
      data-test="delete-subsystem"
      @cancel="cancel"
      @delete="deletedSubsystem"
    />

    <unregister-member-subsystem-dialog
      v-if="clickedSubsystemCode && showUnregisterDialog"
      :member="memberStore.currentMember"
      :subsystem-code="clickedSubsystemCode"
      :server-code="clickedServerCode"
      data-test="unregister-subsystem"
      @cancel="cancel"
      @unregistered-subsystem="unregisteredSubsystem"
    />
  </main>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { Colors, Permissions } from '@/global';
import { mapActions, mapState, mapStores } from 'pinia';
import { useUser } from '@/store/modules/user';
import { useMember } from '@/store/modules/members';
import { useSubsystem } from '@/store/modules/subsystems';
import { useNotifications } from '@/store/modules/notifications';
import AddMemberSubsystemDialog from '@/views/Members/Member/Subsystems/AddMemberSubsystemDialog.vue';
import DeleteMemberSubsystemDialog from '@/views/Members/Member/Subsystems/DeleteMemberSubsystemDialog.vue';
import UnregisterMemberSubsystemDialog from '@/views/Members/Member/Subsystems/UnregisterMemberSubsystemDialog.vue';
import { XrdIconError, XrdIconInProgress } from '@niis/shared-ui';
import {
  ManagementRequestStatus,
  Subsystem,
  UsedSecurityServers,
} from '@/openapi-types';
import DataTableToolbar from '@/components/ui/DataTableToolbar.vue';
import CustomDataTableFooter from '@/components/ui/CustomDataTableFooter.vue';

// To provide the Vue instance to debounce
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let that: any;

/**
 * Component for Member Subsystem
 */
export default defineComponent({
  name: 'MemberSubsystems',
  components: {
    CustomDataTableFooter,
    DataTableToolbar,
    DeleteMemberSubsystemDialog,
    AddMemberSubsystemDialog,
    UnregisterMemberSubsystemDialog,
    XrdIconError,
    XrdIconInProgress,
  },
  props: {
    memberid: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      colors: Colors,

      showAddSubsystemDialog: false,
      showDeleteDialog: false,
      showUnregisterDialog: false,

      loading: false,
      search: '',

      subsystems: [] as Subsystem[],

      clickedSubsystemCode: '',
      clickedServerCode: '',
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
    allowToUnregisterMemberSubsystem(): boolean {
      return this.hasPermission(Permissions.UNREGISTER_SUBSYSTEM);
    },
  },
  created() {
    that = this;

    this.loading = true;
    this.subsystemStore
      .loadByMemberId(this.memberid)
      .then((resp) => {
        this.subsystems = resp;
      })
      .catch((error) => {
        this.showError(error);
      })
      .finally(() => {
        this.loading = false;
      });
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    deleteClicked(subsystem: Subsystem) {
      this.clickedSubsystemCode = subsystem.subsystem_id
        ?.subsystem_code as string;
      this.showDeleteDialog = true;
    },
    unregisterClicked(subsystem: Subsystem, subitem: UsedSecurityServers) {
      this.clickedSubsystemCode = subsystem.subsystem_id
        ?.subsystem_code as string;
      this.clickedServerCode = subitem.server_code as string;
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
    unregisteredSubsystem() {
      this.showUnregisterDialog = false;
      this.clickedSubsystemCode = '';
      this.clickedServerCode = '';
      this.refetchSubsystems();
    },
    cancel() {
      this.showAddSubsystemDialog = false;
      this.showDeleteDialog = false;
      this.showUnregisterDialog = false;
      this.clickedSubsystemCode = '';
      this.clickedServerCode = '';
    },
    refetchSubsystems() {
      this.loading = true;

      this.subsystemStore
        .loadByMemberId(this.memberid)
        .then((resp) => {
          this.subsystems = resp;
        })
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => {
          this.loading = false;
        });
    },
    getStatusText(status: string | undefined) {
      if (status) {
        switch (status) {
          case ManagementRequestStatus.APPROVED:
            return this.$t('securityServers.registered') as string;
          case ManagementRequestStatus.WAITING:
          case ManagementRequestStatus.SUBMITTED_FOR_APPROVAL:
            return this.$t('securityServers.pending') as string;
          case 'DISABLED':
            return this.$t('securityServers.disabled') as string;
        }
      }
      return this.$t('securityServers.unregistered') as string;
    },
  },
});
</script>

<style lang="scss" scoped>
@import '@/assets/colors';

.subsystems-table {
  th {
    font-size: 12px;
    font-weight: 700 !important;
    height: 48px !important;
  }

  tbody > tr:hover td,
  tbody:hover td[rowspan] {
    cursor: pointer;
    background-color: $XRoad-Purple10;
  }
}

.card-corner-button {
  display: flex;
  justify-content: flex-end;
}

.status {
  text-transform: uppercase;
  font-weight: bold;
}

.subsystem-actions {
  text-align: right;

  .xrd-clickable {
    color: $XRoad-Link;
    margin-left: 10px;
  }
}

.unregistered-subsystem {
  background-color: $XRoad-WarmGrey30;
}

.custom-footer {
  height: 16px;
}
tbody tr:last-child td {
  border-bottom: thin solid rgba(0, 0, 0, 0.12); /* Matches the color of the Vuetify table line */
}
</style>
