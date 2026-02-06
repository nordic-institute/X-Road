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
  <XrdViewNavigation :allowed-tabs="tabs" />
</template>

<script lang="ts" setup>
import { computed } from 'vue';
import { Permissions, RouteName } from '@/global';
import { Tab, XrdViewNavigation } from '@niis/shared-ui';

import { useUser } from '@/store/modules/user';

const props = defineProps({
  id: {
    type: String,
    required: true,
  },
});

const { getAllowedTabs } = useUser();

const tabs = computed(() => {
  const allTabs: Tab[] = [
    {
      key: 'details',
      name: 'tab.client.details',
      icon: 'list_alt',
      to: {
        name: RouteName.SubsystemDetails,
        params: { id: props.id },
      },
    },
    {
      key: 'serviceClients',
      name: 'tab.client.serviceClients',
      icon: 'id_card',
      to: {
        name: RouteName.SubsystemServiceClients,
        params: { id: props.id },
      },
      permissions: [Permissions.VIEW_CLIENT_ACL_SUBJECTS],
    },
    {
      key: 'services',
      name: 'tab.client.services',
      icon: 'smb_share',
      to: {
        name: RouteName.SubsystemServices,
        params: { id: props.id },
      },
      permissions: [Permissions.VIEW_CLIENT_SERVICES],
    },
    {
      key: 'internalServers',
      name: 'tab.client.internalServers',
      icon: 'host',
      to: {
        name: RouteName.SubsystemServers,
        params: { id: props.id },
      },
      permissions: [Permissions.VIEW_CLIENT_INTERNAL_CERTS],
    },
    {
      key: 'localGroups',
      name: 'tab.client.localGroups',
      icon: 'group',
      to: {
        name: RouteName.SubsystemLocalGroups,
        params: { id: props.id },
      },
      permissions: [Permissions.VIEW_CLIENT_LOCAL_GROUPS],
    },
  ];

  return getAllowedTabs(allTabs);
});
</script>
