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
  <div>
    <sub-tabs :tab="currentTab">
      <v-tab v-for="tab in tabs" :key="tab.key" :to="tab.to">{{
        $t(tab.name)
      }}</v-tab>
    </sub-tabs>
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { Permissions, RouteName } from '@/global';
import { Tab } from '@/ui-types';
import SubTabs from '@/components/layout/SubTabs.vue';

import { mapState } from 'pinia';

import { useUser } from '@/store/modules/user';
import { useClient } from '@/store/modules/client';

export default defineComponent({
  components: {
    SubTabs,
  },
  props: {
    id: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      currentTab: undefined as undefined | Tab,
      confirmUnregisterClient: false as boolean,
      unregisterLoading: false as boolean,
    };
  },
  computed: {
    ...mapState(useUser, ['hasPermission', 'getAllowedTabs']),
    ...mapState(useClient, ['client']),

    showUnregister(): boolean {
      if (!this.client) return false;
      return (
        this.client &&
        this.hasPermission(Permissions.SEND_CLIENT_DEL_REQ) &&
        (this.client.status === 'REGISTERED' ||
          this.client.status === 'REGISTRATION_IN_PROGRESS')
      );
    },

    showDelete(): boolean {
      if (
        !this.client ||
        this.client.status === 'REGISTERED' ||
        this.client.status === 'REGISTRATION_IN_PROGRESS'
      ) {
        return false;
      }

      return this.hasPermission(Permissions.DELETE_CLIENT);
    },

    tabs(): Tab[] {
      const allTabs: Tab[] = [
        {
          key: 'details',
          name: 'tab.client.details',
          to: {
            name: RouteName.SubsystemDetails,
            params: { id: this.id },
          },
        },
        {
          key: 'serviceClients',
          name: 'tab.client.serviceClients',
          to: {
            name: RouteName.SubsystemServiceClients,
            params: { id: this.id },
          },
          permissions: [Permissions.VIEW_CLIENT_ACL_SUBJECTS],
        },
        {
          key: 'services',
          name: 'tab.client.services',
          to: {
            name: RouteName.SubsystemServices,
            params: { id: this.id },
          },
          permissions: [Permissions.VIEW_CLIENT_SERVICES],
        },
        {
          key: 'internalServers',
          name: 'tab.client.internalServers',
          to: {
            name: RouteName.SubsystemServers,
            params: { id: this.id },
          },
          permissions: [Permissions.VIEW_CLIENT_INTERNAL_CERTS],
        },
        {
          key: 'localGroups',
          name: 'tab.client.localGroups',
          to: {
            name: RouteName.SubsystemLocalGroups,
            params: { id: this.id },
          },
          permissions: [Permissions.VIEW_CLIENT_LOCAL_GROUPS],
        },
      ];

      return this.getAllowedTabs(allTabs);
    },
  },
});
</script>
