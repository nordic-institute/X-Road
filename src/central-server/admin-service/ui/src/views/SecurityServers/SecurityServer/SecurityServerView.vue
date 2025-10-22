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
    data-test="security-server-view"
    translated-title
    :title="securityServerCode"
    :breadcrumbs
  >
    <template #append-header>
      <XrdBtn
        v-if="canDeleteServer && securityServerCode"
        data-test="btn-delete-security-server"
        class="ml-auto"
        prepend-icon="delete_forever"
        variant="outlined"
        text="action.delete"
        @click="showDeleteServerDialog = true"
      />
    </template>
    <template #tabs>
      <XrdViewNavigation :allowed-tabs="allowedTabs" />
    </template>
    <router-view v-slot="{ Component, route }">
      <transition mode="out-in" name="fade">
        <component :is="Component" :key="route.fullPath" />
      </transition>
    </router-view>
    <delete-security-server-dialog
      v-if="securityServerCode && showDeleteServerDialog"
      :server-code="securityServerCode"
      :security-server-id="serverId"
      @cancel="showDeleteServerDialog = false"
    />
  </XrdView>
</template>

<script lang="ts" setup>
import { computed, ref, watchEffect } from 'vue';
import { Permissions, RouteName } from '@/global';
import { useSecurityServer } from '@/store/modules/security-servers';
import {
  XrdView,
  XrdBtn,
  useNotifications,
  useHistory,
  XrdViewNavigation,
} from '@niis/shared-ui';
import DeleteSecurityServerDialog from './dialogs/DeleteSecurityServerDialog.vue';
import { useUser } from '@/store/modules/user';
import { useMember } from '@/store/modules/members';

/**
 * Wrapper component for a security server view
 */
const props = defineProps({
  serverId: {
    type: String,
    required: true,
  },
});

const historyStore = useHistory();
const { addError } = useNotifications();

const securityServerStore = useSecurityServer();
const { hasPermission, getAllowedTabs } = useUser();

const securityServerCode = computed(
  () => securityServerStore?.current?.server_id.server_code || '',
);
const canDeleteServer = computed(() =>
  hasPermission(Permissions.DELETE_SECURITY_SERVER),
);
const tabs = computed(() => {
  return [
    {
      key: 'security-server-details-tab-button',
      name: 'securityServers.securityServer.tabs.details',
      to: {
        name: RouteName.SecurityServerDetails,
        params: { serverId: props.serverId },
        replace: true,
      },
      permissions: [Permissions.VIEW_SECURITY_SERVER_DETAILS],
    },

    {
      key: 'security-server-clients-tab-button',
      name: 'securityServers.securityServer.tabs.clients',
      to: {
        name: RouteName.SecurityServerClients,
        params: { serverId: props.serverId },
        replace: true,
      },
      permissions: [Permissions.VIEW_SECURITY_SERVER_DETAILS],
    },

    {
      key: 'security-server-auth-certs-tab-button',
      name: 'securityServers.securityServer.tabs.authCertificates',
      to: {
        name: RouteName.SecurityServerAuthenticationCertificates,
        params: { serverId: props.serverId },
        replace: true,
      },
      permissions: [Permissions.VIEW_SECURITY_SERVER_DETAILS],
    },
  ];
});

const allowedTabs = computed(() => getAllowedTabs(tabs.value));

const breadcrumbs = computed(() => {
  const memberStore = useMember();
  if (historyStore.cameFrom(RouteName.MemberDetails) && memberStore.current) {
    return [
      {
        title: 'members.header',
        to: {
          name: RouteName.Members,
        },
      },
      {
        title: memberStore.current?.member_name || '',
        translatedTitle: true,
        to: {
          name: RouteName.MemberDetails,
          params: {
            memberId: memberStore.current?.client_id.encoded_id,
          },
        },
      },
    ];
  }
  return [
    {
      title: 'tab.main.securityServers',
      to: {
        name: RouteName.SecurityServers,
      },
    },
  ];
});

const showDeleteServerDialog = ref(false);

watchEffect(() => {
  securityServerStore
    .loadById(props.serverId)
    .catch((err) => addError(err, { navigate: true }));
});
</script>
