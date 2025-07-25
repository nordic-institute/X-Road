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
    <XrdCard id="member-details">
      <v-table class="bg-surface-container xrd-rounded-12">
        <tbody>
          <tr>
            <td class="on-surface-variant font-weight-medium">{{ $t('global.memberName') }}</td>
            <td data-test="member-name-card">{{ memberStore.currentMember.member_name }}</td>
            <td class="text-end">
              <XrdBtn
                v-if="allowMemberRename"
                variant="text"
                color="tertiary"
                text-key="action.edit"
                @click="showEditNameDialog = true"
              />
            </td>
          </tr>
          <tr>
            <td class="on-surface-variant font-weight-medium">{{ $t('global.memberClass') }}</td>
            <td data-test="member-class-card">{{ memberStore.currentMember.client_id.member_class }}</td>
            <td></td>
          </tr>
          <tr>
            <td class="on-surface-variant font-weight-medium">{{ $t('global.memberCode') }}</td>
            <td data-test="member-code-card">{{ memberStore.currentMember.client_id.member_code }}</td>
            <td></td>
          </tr>
        </tbody>
      </v-table>
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
          <xrd-button
            v-if="allowUnregisterMember"
            text
            :data-test="`unregister-${server.server_id.server_code}`"
            :outlined="false"
            @click="unregisterFromServer = server"
          >
            {{ $t('action.unregister') }}
          </xrd-button>
        </template>
      </ServersList>

      <UnregisterMemberDialog
        v-if="allowUnregisterMember && unregisterFromServer"
        :member="memberStore.currentMember"
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
      <XrdCard title-key="members.member.details.globalGroups">
        <template #append-title>
          <v-text-field
            v-model="searchGroups"
            data-test="search-query-field"
            class="xrd-text-field"
            width="360"
            prepend-inner-icon="search"
            single-line
            :label="$t('action.search')"
          />
        </template>
        <v-data-table
          data-test="global-groups-table"
          class="xrd-data-table bg-surface-container"
          hide-default-footer
          :loading="loadingGroups"
          :headers="groupsHeaders"
          :items="globalGroups"
          :search="searchGroups"
          :must-sort="true"
          :items-per-page="-1"
          :loader-height="2"
        >
          <template #[`item.added_to_group`]="{ item }">
            <date-time :value="item.added_to_group" />
          </template>
        </v-data-table>
      </XrdCard>
    </div>

    <!-- Edit member name dialog -->
    <EditMemberNameDialog
      v-if="showEditNameDialog"
      :member="memberStore.currentMember"
      @cancel="cancelEditMemberName"
      @save="memberNameChanged"
    />
  </XrdSubView>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { Permissions } from '@/global';
import { mapActions, mapState, mapStores } from 'pinia';
import { useMember } from '@/store/modules/members';
import { MemberGlobalGroup, SecurityServer } from '@/openapi-types';
import { useNotifications } from '@/store/modules/notifications';
import { useUser } from '@/store/modules/user';
import EditMemberNameDialog from './EditMemberNameDialog.vue';
import DateTime from '@/components/ui/DateTime.vue';
import { Colors, XrdBtn, XrdCard, XrdSubView } from '@niis/shared-ui';
import { DataTableHeader } from '@/ui-types';
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
    DateTime,
    EditMemberNameDialog,
    UnregisterMemberDialog,
    XrdBtn,
    XrdCard,
    XrdSubView,
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
  created() {
    //eslint-disable-next-line @typescript-eslint/no-this-alias
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

    this.loadingOwnedServers = true;
    this.memberStore
      .getMemberOwnedServers(this.memberid)
      .then((resp) => (this.ownedServers = resp))
      .catch((error) => this.showError(error))
      .finally(() => (this.loadingOwnedServers = false));

    this.loadClientServers();
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    cancelEditMemberName() {
      this.showEditNameDialog = false;
    },
    memberNameChanged() {
      this.showEditNameDialog = false;
    },
    loadClientServers() {
      this.loadingUsedServers = true;
      this.memberStore
        .getUsedServers(this.memberid)
        .then((resp) => (this.usedServers = resp))
        .catch((error) => this.showError(error))
        .finally(() => (this.loadingUsedServers = false));
    },
  },
});
</script>

<style lang="scss" scoped></style>
