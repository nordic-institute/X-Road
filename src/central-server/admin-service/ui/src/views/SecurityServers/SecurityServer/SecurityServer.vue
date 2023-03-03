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
  <div data-test="security-server-view">
    <div class="header-row">
      <div class="title-search">
        <div class="xrd-view-title">FOO 1</div>
      </div>
    </div>
    <PageNavigation :tabs="securityServerNavigationTabs"></PageNavigation>
    <router-view />
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import PageNavigation, {
  PageNavigationTab,
} from '@/components/layout/PageNavigation.vue';
import { Colors, Permissions, RouteName } from '@/global';

/**
 * Wrapper component for a security server view
 */
export default Vue.extend({
  components: { PageNavigation },
  props: {
    serverId: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      colors: Colors,
    };
  },
  computed: {
    securityServerNavigationTabs(): PageNavigationTab[] {
      return [
        {
          key: 'security-server-details-tab-button',
          name: 'securityServers.securityServer.tabs.details',
          to: {
            name: RouteName.SecurityServerDetails,
          },
          permissions: [Permissions.VIEW_SECURITY_SERVER_DETAILS],
        },

        {
          key: 'security-server-clients-tab-button',
          name: 'securityServers.securityServer.tabs.clients',
          to: {
            name: RouteName.SecurityServerClients,
          },
          permissions: [Permissions.VIEW_SECURITY_SERVER_DETAILS],
        },

        {
          key: 'security-server-auth-certs-tab-button',
          name: 'securityServers.securityServer.tabs.authCertificates',
          to: {
            name: RouteName.SecurityServerAuthenticationCertificates,
          },
          permissions: [Permissions.VIEW_SECURITY_SERVER_DETAILS],
        },

        {
          key: 'security-server-management-requests-tab-button',
          name: 'securityServers.securityServer.tabs.managementRequests',
          to: {
            name: RouteName.SecurityServerManagementRequests,
          },
          permissions: [Permissions.VIEW_SECURITY_SERVER_DETAILS],
          showAttention: true,
        },
      ];
    },
  },
});
</script>
