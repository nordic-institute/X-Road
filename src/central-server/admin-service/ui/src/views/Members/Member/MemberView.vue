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
  <XrdView
    id="memberview"
    translated-title
    :title="memberStore.current?.member_name || ''"
    :breadcrumbs
  >
    <template #append-header>
      <XrdBtn
        v-if="allowMemberDelete"
        data-test="delete-member"
        class="ml-auto"
        prepend-icon="delete_forever"
        variant="outlined"
        text="action.delete"
        @click="showDeleteDialog = true"
      />
    </template>
    <template #tabs>
      <XrdViewNavigation :allowed-tabs="allowedTabs" />
    </template>

    <router-view />

    <!-- Delete member - Check member code dialog -->
    <MemberDeleteDialog
      v-if="showDeleteDialog && memberStore.current"
      :member="memberStore.current"
      @cancel="showDeleteDialog = false"
    />
  </XrdView>
</template>

<script lang="ts" setup>
import { computed, ref, watchEffect } from 'vue';
import { Permissions, RouteName } from '@/global';
import { useMember } from '@/store/modules/members';
import {
  XrdView,
  XrdBtn,
  useNotifications,
  useHistory,
  XrdViewNavigation,
  PageNavigationTab,
} from '@niis/shared-ui';
import MemberDeleteDialog from '@/views/Members/Member/Details/DeleteMemberDialog.vue';
import { useUser } from '@/store/modules/user';
import { useSecurityServer } from '@/store/modules/security-servers';

/**
 * Wrapper component for a member view
 */

const props = defineProps({
  memberId: {
    type: String,
    required: true,
  },
});

const historyStore = useHistory();
const memberStore = useMember();
const { hasPermission } = useUser();

const allowMemberDelete = computed(() =>
  hasPermission(Permissions.DELETE_MEMBER),
);

const tabs = computed(() => {
  return [
    {
      key: 'member-details-tab-button',
      name: 'members.member.pagenavigation.details',
      to: {
        name: RouteName.MemberDetails,
        params: { memberId: props.memberId },
        replace: true,
      },
      icon: 'list_alt',
      permissions: [Permissions.VIEW_MEMBER_DETAILS],
    },

    {
      key: 'member-subsystems-tab-button',
      name: 'members.member.pagenavigation.subsystems',
      to: {
        name: RouteName.MemberSubsystems,
        params: { memberId: props.memberId },
        replace: true,
      },
      icon: 'folder_copy',
      permissions: [Permissions.VIEW_MEMBER_DETAILS],
    },
  ] as PageNavigationTab[];
});

const showDeleteDialog = ref(false);
const { addError } = useNotifications();
const { getAllowedTabs } = useUser();

const allowedTabs = computed(() => getAllowedTabs(tabs.value));

const breadcrumbs = computed(() => {
  const ssStore = useSecurityServer();
  if (
    historyStore.cameFrom(RouteName.SecurityServerClients) &&
    ssStore.current
  ) {
    return [
      {
        title: 'tab.main.securityServers',
        to: {
          name: RouteName.SecurityServers,
        },
      },
      {
        title: ssStore.current?.server_id.server_code || '',
        translatedTitle: true,
        to: {
          name: RouteName.SecurityServerDetails,
          params: {
            serverId: ssStore.current?.server_id.encoded_id,
          },
        },
      },
    ];
  }
  return [
    {
      title: 'members.header',
      to: {
        name: RouteName.Members,
      },
    },
  ];
});

watchEffect(() => {
  memberStore
    .loadById(props.memberId)
    .catch((err) => addError(err, { navigate: true }));
});
</script>
