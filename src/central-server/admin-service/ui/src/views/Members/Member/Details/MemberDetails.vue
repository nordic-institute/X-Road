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
<!--
  Member details view
-->
<template>
  <main id="member-details-content">
    <!-- Member Details -->
    <div id="member-details">
      <info-card
        :title-text="$t('global.memberName')"
        :action-text="$t('action.edit')"
        :show-action="allowMemberRename"
        :info-text="memberStore.currentMember.member_name || ''"
        data-test="member-name-card"
        @action-clicked="showEditNameDialog = true"
      />

      <info-card
        :title-text="$t('global.memberClass')"
        :info-text="memberStore.currentMember.client_id.member_class || ''"
        data-test="member-class-card"
      />

      <info-card
        :title-text="$t('global.memberCode')"
        :info-text="memberStore.currentMember.client_id.member_code || ''"
        data-test="member-code-card"
      />
    </div>

    <!-- Owned Servers -->
    <div id="owned-servers">
      <searchable-titled-view
        v-model="searchServers"
        title-key="members.member.details.ownedServers"
      >
        <v-data-table
          :loading="loadingServers"
          :headers="serversHeaders"
          :items="servers"
          :search="searchServers"
          :must-sort="true"
          :items-per-page="-1"
          class="elevation-0 data-table"
          item-key="id"
          :loader-height="2"
          data-test="owned-servers-table"
        >
          <template #[`item.server_id.server_code`]="{ item }">
            <div
              class="server-code xrd-clickable"
              :data-test="`owned-server-${item.server_id.server_code}`"
              @click="toSecurityServerDetails(item)"
            >
              {{ item.server_id.server_code }}
            </div>
          </template>
          <template #bottom>
            <custom-data-table-footer />
          </template>
        </v-data-table>
      </searchable-titled-view>
    </div>

    <!-- Global Groups -->
    <div id="global-groups">
      <searchable-titled-view
        v-model="searchGroups"
        title-key="members.member.details.globalGroups"
      >
        <v-data-table
          :loading="loadingGroups"
          :headers="groupsHeaders"
          :items="globalGroups"
          :search="searchGroups"
          :must-sort="true"
          :items-per-page="-1"
          class="elevation-0 data-table xrd-data-table"
          :loader-height="2"
          data-test="global-groups-table"
        >
          <template #[`item.added_to_group`]="{ item }">
            <date-time :value="item.added_to_group" />
          </template>
          <template #bottom>
            <custom-data-table-footer />
          </template>
        </v-data-table>
      </searchable-titled-view>

      <div
        v-if="allowMemberDelete"
        class="delete-action"
        data-test="delete-member"
        @click="showDeleteDialog = true"
      >
        <div>
          <v-icon
            class="xrd-large-button-icon"
            :color="colors.Purple100"
            icon="mdi-close-circle"
          />
        </div>
        <div class="action-text">
          {{
            `${$t('members.member.details.deleteMember')} "${
              memberStore.currentMember.member_name || ''
            }"`
          }}
        </div>
      </div>
    </div>

    <!-- Edit member name dialog -->
    <EditMemberNameDialog
      v-if="showEditNameDialog"
      :member="memberStore.currentMember"
      @cancel="cancelEditMemberName"
      @name-changed="memberNameChanged"
    />

    <!-- Delete member - Check member code dialog -->
    <MemberDeleteDialog
      v-if="showDeleteDialog"
      :member="memberStore.currentMember"
      @cancel="cancelDelete"
    />
  </main>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { Colors, Permissions, RouteName } from '@/global';
import InfoCard from '@/components/ui/InfoCard.vue';
import { mapActions, mapState, mapStores } from 'pinia';
import { useMember } from '@/store/modules/members';
import { MemberGlobalGroup, SecurityServer } from '@/openapi-types';
import { useNotifications } from '@/store/modules/notifications';
import { useUser } from '@/store/modules/user';
import MemberDeleteDialog from '@/views/Members/Member/Details/DeleteMemberDialog.vue';
import EditMemberNameDialog from '@/views/Members/Member/Details/EditMemberNameDialog.vue';
import { VDataTable } from 'vuetify/labs/VDataTable';
import SearchableTitledView from '@/components/ui/SearchableTitledView.vue';
import DateTime from '@/components/ui/DateTime.vue';
import CustomDataTableFooter from '@/components/ui/CustomDataTableFooter.vue';

// To provide the Vue instance to debounce
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let that: any;

/**
 * Component for a Members details view
 */
export default defineComponent({
  name: 'MemberDetails',
  components: {
    CustomDataTableFooter,
    DateTime,
    SearchableTitledView,
    EditMemberNameDialog,
    MemberDeleteDialog,
    InfoCard,
    VDataTable,
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

      showEditNameDialog: false,
      showDeleteDialog: false,

      loadingServers: false,
      searchServers: '',
      servers: [] as SecurityServer[],

      loadingGroups: false,
      searchGroups: '',
      globalGroups: [] as MemberGlobalGroup[],

      serversHeaders: [
        {
          title: this.$t('global.server') as string,
          align: 'start',
          key: 'server_id.server_code',
        },
      ],
      groupsHeaders: [
        {
          key: 'group_code',
          title: this.$t('members.member.details.group') as string,
          align: 'start',
        },
        {
          key: 'subsystem',
          title: this.$t('global.subsystem') as string,
          align: 'start',
        },
        {
          key: 'added_to_group',
          title: this.$t('members.member.details.addedToGroup') as string,
          align: 'start',
        },
      ],
    };
  },
  computed: {
    ...mapState(useUser, ['hasPermission']),
    ...mapStores(useMember),
    allowMemberDelete(): boolean {
      return this.hasPermission(Permissions.DELETE_MEMBER);
    },
    allowMemberRename(): boolean {
      return this.hasPermission(Permissions.EDIT_MEMBER_NAME);
    },
  },
  created() {
    that = this;

    this.loadingGroups = true;
    this.memberStore
      .getMemberGlobalGroups(this.memberid)
      .then((resp) => {
        this.globalGroups = resp;
      })
      .catch((error) => {
        this.showError(error);
      })
      .finally(() => {
        this.loadingGroups = false;
      });

    this.loadingServers = true;
    this.memberStore
      .getMemberOwnedServers(this.memberid)
      .then((resp) => {
        this.servers = resp;
      })
      .catch((error) => {
        this.showError(error);
      })
      .finally(() => {
        this.loadingServers = false;
      });
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    cancelEditMemberName() {
      this.showEditNameDialog = false;
    },
    memberNameChanged() {
      this.showEditNameDialog = false;
    },
    cancelDelete() {
      this.showDeleteDialog = false;
    },
    toSecurityServerDetails(securityServer: SecurityServer): void {
      this.$router.push({
        name: RouteName.SecurityServerDetails,
        params: { serverId: securityServer.server_id.encoded_id || '' },
      });
    },
  },
});
</script>

<style lang="scss" scoped>
@import '@/assets/colors';
@import '@/assets/tables';

.server-code {
  color: $XRoad-Purple100;
  font-weight: 600;
  font-size: 14px;
  display: flex;
  align-items: center;
}

.card-title {
  font-size: 12px;
  text-transform: uppercase;
  color: $XRoad-Black70;
  font-weight: bold;
  padding-top: 5px;
  padding-bottom: 5px;
}

.card-corner-button {
  display: flex;
  justify-content: flex-end;
}

.delete-action {
  margin-top: 34px;
  color: $XRoad-Link;
  cursor: pointer;
  display: flex;
  flex-direction: row;
  .action-text {
    margin-top: 2px;
  }
}

#member-details {
  margin-top: 24px;
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: flex-end;

  margin-bottom: 24px;

  .details-card {
    width: 100%;

    &:first-child {
      margin-right: 30px;
    }

    &:last-child {
      margin-left: 30px;
    }
  }
}

#global-groups-table {
  tbody tr td:last-child {
    width: 50px;
  }
}
</style>
