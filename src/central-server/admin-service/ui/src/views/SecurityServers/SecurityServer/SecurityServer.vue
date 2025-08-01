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
  <details-view :back-to="backTo" data-test="security-server-view">
    <xrd-titled-view :title="securityServerCode">
      <PageNavigation :tabs="securityServerNavigationTabs"></PageNavigation>
      <router-view />
    </xrd-titled-view>
  </details-view>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import PageNavigation, {
  PageNavigationTab,
} from '@/layouts/PageNavigation.vue';
import { Permissions, RouteName } from '@/global';
import { mapActions, mapStores } from 'pinia';
import { useSecurityServer } from '@/store/modules/security-servers';
import { useNotifications } from '@/store/modules/notifications';
import DetailsView from '@/components/ui/DetailsView.vue';
import { XrdTitledView } from '@niis/shared-ui';

/**
 * Wrapper component for a security server view
 */
export default defineComponent({
  components: { XrdTitledView, DetailsView, PageNavigation },
  props: {
    serverId: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      backTo: {
        name: RouteName.SecurityServers,
      },
    };
  },
  computed: {
    ...mapStores(useSecurityServer),
    securityServerCode(): string {
      return (
        this.securityServerStore?.currentSecurityServer?.server_id
          .server_code || ''
      );
    },
    securityServerNavigationTabs(): PageNavigationTab[] {
      return [
        {
          key: 'security-server-details-tab-button',
          name: 'securityServers.securityServer.tabs.details',
          to: {
            name: RouteName.SecurityServerDetails,
            params: { serverId: this.serverId },
            replace: true,
          },
          permissions: [Permissions.VIEW_SECURITY_SERVER_DETAILS],
        },

        {
          key: 'security-server-clients-tab-button',
          name: 'securityServers.securityServer.tabs.clients',
          to: {
            name: RouteName.SecurityServerClients,
            params: { serverId: this.serverId },
            replace: true,
          },
          permissions: [Permissions.VIEW_SECURITY_SERVER_DETAILS],
        },

        {
          key: 'security-server-auth-certs-tab-button',
          name: 'securityServers.securityServer.tabs.authCertificates',
          to: {
            name: RouteName.SecurityServerAuthenticationCertificates,
            params: { serverId: this.serverId },
            replace: true,
          },
          permissions: [Permissions.VIEW_SECURITY_SERVER_DETAILS],
        },
      ];
    },
  },
  created() {
    this.fetchDetails();
  },
  methods: {
    ...mapActions(useNotifications, ['showError']),
    fetchDetails: async function () {
      try {
        await this.securityServerStore.loadById(this.serverId);
      } catch (error: unknown) {
        this.showError(error);
      }
    },
  },
});
</script>
