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
  <XrdSubView>
    <!-- Member Details -->
    <XrdCard data-test="member-details" :loading="memberStore.loadingCurrent">
      <XrdCardTable>
        <XrdCardTableRow data-test="member-name" label="global.memberName" :value="memberStore.current?.member_name">
          <XrdBtn
            v-if="allowMemberRename"
            data-test="info-card-edit-button"
            variant="text"
            color="tertiary"
            text="action.edit"
            @click="showEditNameDialog = true"
          />
        </XrdCardTableRow>
        <XrdCardTableRow data-test="member-class" label="global.memberClass" :value="memberStore.current?.client_id.member_class" />
        <XrdCardTableRow data-test="member-code" label="global.memberCode" :value="memberStore.current?.client_id.member_code" />
      </XrdCardTable>
    </XrdCard>

    <!-- Owned Servers -->
    <div id="owned-servers" class="mt-4">
      <ServersList
        title-key="members.member.details.ownedServers"
        :loading="loadingOwnedServers"
        :servers="ownedServers"
        data-test="owned-servers-table"
      />
    </div>

    <!-- Used Servers -->
    <div id="used-servers" class="mt-4">
      <ServersList
        title-key="members.member.details.usedServers"
        :loading="loadingUsedServers"
        :servers="usedServers"
        data-test="used-servers-table"
      >
        <template #actions="{ server }">
          <XrdBtn
            v-if="allowUnregisterMember"
            variant="text"
            color="tertiary"
            text="action.unregister"
            :data-test="`unregister-${server.server_id.server_code}`"
            @click="unregisterFromServer = server"
          />
        </template>
      </ServersList>

      <UnregisterMemberDialog
        v-if="allowUnregisterMember && unregisterFromServer && memberStore.current"
        :member="memberStore.current"
        :server="unregisterFromServer"
        data-test="unregister-member"
        @cancel="unregisterFromServer = null"
        @unregister="
          unregisterFromServer = null;
          loadClientServers();
        "
      />
    </div>

    <!-- Global Groups -->
    <div id="global-groups" class="mt-4">
      <XrdCard title="members.member.details.globalGroups">
        <template #append-title>
          <XrdSearchField v-model="searchGroups" data-test="search-query-field" width="360" :label="$t('action.search')" />
        </template>
        <v-data-table
          data-test="global-groups-table"
          class="xrd bg-surface-container"
          hide-default-footer
          :loading="loadingGroups"
          :headers="groupsHeaders"
          :items="globalGroups"
          :search="searchGroups"
          :must-sort="true"
          :items-per-page="-1"
          :loader-height="2"
        >
          <template #[`item.group_code`]="{ item }">
            <div class="d-flex flex-row align-center">
              <v-icon icon="group" color="tertiary" size="24" />
              <span class="text-primary font-weight-medium ml-3">{{ item.group_code }}</span>
            </div>
          </template>
          <template #[`item.added_to_group`]="{ item }">
            <XrdDateTime :value="item.added_to_group" />
          </template>
        </v-data-table>
      </XrdCard>
    </div>

    <!-- Edit member name dialog -->
    <EditMemberNameDialog
      v-if="showEditNameDialog && memberStore.current"
      :member="memberStore.current"
      @cancel="cancelEditMemberName"
      @save="memberNameChanged"
    />
  </XrdSubView>
</template>

<script lang="ts">
import { defineComponent } from 'vue';

import { DataTableHeader } from 'vuetify/lib/components/VDataTable/types';
import { mapState, mapStores } from 'pinia';

import { useNotifications, XrdBtn, XrdCard, XrdCardTable, XrdCardTableRow, XrdDateTime, XrdSubView } from '@niis/shared-ui';

import { Permissions } from '@/global';
import { MemberGlobalGroup, SecurityServer } from '@/openapi-types';
import { useMember } from '@/store/modules/members';
import { useUser } from '@/store/modules/user';

import EditMemberNameDialog from './EditMemberNameDialog.vue';
import ServersList from './ServersList.vue';
import UnregisterMemberDialog from './UnregisterMemberDialog.vue';

// To provide the Vue instance to debounce
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let that: any;

/**
 * Component for a Members details view
 */
export default defineComponent({
  name: 'MemberDetails',
  components: {
    ServersList,
    XrdDateTime,
    EditMemberNameDialog,
    UnregisterMemberDialog,
    XrdBtn,
    XrdCard,
    XrdCardTable,
    XrdCardTableRow,
    XrdSubView,
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
      showEditNameDialog: false,

      loadingOwnedServers: false,
      ownedServers: [] as SecurityServer[],

      loadingUsedServers: false,
      usedServers: [] as SecurityServer[],
      unregisterFromServer: null as SecurityServer | null,

      loadingGroups: false,
      searchGroups: '',
      globalGroups: [] as MemberGlobalGroup[],
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
      ] as DataTableHeader[],
    };
  },
  computed: {
    ...mapState(useUser, ['hasPermission']),
    ...mapStores(useMember),
    allowMemberRename(): boolean {
      return this.hasPermission(Permissions.EDIT_MEMBER_NAME);
    },
    allowUnregisterMember(): boolean {
      return this.hasPermission(Permissions.UNREGISTER_MEMBER);
    },
  },
  watch: {
    memberId: {
      immediate: true,
      handler(memberId) {
        this.loadingGroups = true;
        this.memberStore
          .getMemberGlobalGroups(memberId)
          .then((resp) => {
            this.globalGroups = resp;
          })
          .catch((error) => {
            this.addError(error);
          })
          .finally(() => {
            this.loadingGroups = false;
          });

        this.loadingOwnedServers = true;
        this.memberStore
          .getMemberOwnedServers(memberId)
          .then((resp) => (this.ownedServers = resp))
          .catch((error) => this.addError(error))
          .finally(() => (this.loadingOwnedServers = false));

        this.loadClientServers();
      },
    },
  },
  methods: {
    cancelEditMemberName() {
      this.showEditNameDialog = false;
    },
    memberNameChanged() {
      this.showEditNameDialog = false;
    },
    loadClientServers() {
      this.loadingUsedServers = true;
      this.memberStore
        .getUsedServers(this.memberId)
        .then((resp) => (this.usedServers = resp))
        .catch((error) => this.addError(error))
        .finally(() => (this.loadingUsedServers = false));
    },
  },
});
</script>

<style lang="scss" scoped></style>
