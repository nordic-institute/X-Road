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
      <v-tab
        v-for="tab in tabs"
        :key="tab.key"
        :to="tab.to"
        :data-test="tab.key"
        exact
        >{{ $t(tab.name) }}</v-tab
      >
    </sub-tabs>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { Permissions, RouteName } from '@/global';
import { Tab } from '@/ui-types';
import SubTabs from '@/components/layout/SubTabs.vue';

export default Vue.extend({
  components: {
    SubTabs,
  },
  data: () => ({
    currentTab: undefined as undefined | Tab,
    showHelp: false,
  }),

  computed: {
    tabs(): Tab[] {
      const allTabs: Tab[] = [
        {
          key: 'sign-and-auth-keys-tab-button',
          name: 'tab.keys.signAndAuthKeys',
          to: {
            name: RouteName.SignAndAuthKeys,
          },
          permissions: [Permissions.VIEW_KEYS],
        },
        {
          key: 'api-key-tab-button',
          name: 'tab.keys.apiKey',
          to: {
            name: RouteName.ApiKey,
          },
          permissions: [
            Permissions.CREATE_API_KEY,
            Permissions.VIEW_API_KEYS,
            Permissions.UPDATE_API_KEY,
            Permissions.REVOKE_API_KEY,
          ],
        },
        {
          key: 'ss-tls-certificate-tab-button',
          name: 'tab.keys.ssTlsCertificate',
          to: {
            name: RouteName.SSTlsCertificate,
          },
          permissions: [Permissions.VIEW_INTERNAL_TLS_CERT],
        },
      ];

      return this.$store.getters.getAllowedTabs(allTabs);
    },
  },
});
</script>

<style lang="scss" scoped>
.content {
  width: 1000px;
}
</style>
