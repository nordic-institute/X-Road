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
  <div class="xrd-view-common">
    <v-tabs
      v-model="tab"
      class="xrd-tabs"
      color="secondary"
      grow
      slider-size="4"
    >
      <v-tabs-slider color="secondary"></v-tabs-slider>
      <v-tab v-for="tab in tabs" v-bind:key="tab.key" :to="tab.to" :data-test="tab.key" exact>
        {{ $t(tab.name) }}
        <v-hover v-slot:default="{ hover }">
          <v-icon
            :color="hover ? '#6eecff' : getIconColor(tab.to.name)"
            dark
            class="help-icon"
            @click="helpClick(tab)"
            >mdi-help-circle</v-icon
          >
        </v-hover>
      </v-tab>
    </v-tabs>
    <div class="content">
      <router-view />
    </div>
    <helpDialog
      v-if="helpTab"
      :dialog="showHelp"
      @cancel="closeHelp"
      :imageSrc="helpTab.helpImage"
      :title="helpTab.helpTitle"
      :text="helpTab.helpText"
    />
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { Permissions, RouteName } from '@/global';
import { Tab } from '@/ui-types';
import HelpDialog from '@/components/ui/HelpDialog.vue';

interface KeysTab extends Tab {
  helpImage: string;
  helpTitle: string;
  helpText: string;
}

export default Vue.extend({
  components: {
    HelpDialog,
  },
  data: () => ({
    tab: null,
    showHelp: false,
    helpTab: null as KeysTab | null,
  }),

  computed: {
    tabs(): KeysTab[] {
      const allTabs: KeysTab[] = [
        {
          key: 'signAndAuthKeys',
          name: 'tab.keys.signAndAuthKeys',
          to: {
            name: RouteName.SignAndAuthKeys,
          },
          permissions: [Permissions.VIEW_KEYS],
          helpImage: 'keys_and_certificates.png',
          helpTitle: 'keys.helpTitleKeys',
          helpText: 'keys.helpTextKeys',
        },
        {
          key: 'apiKey',
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
          helpImage: 'api_keys.png',
          helpTitle: 'keys.helpTitleApi',
          helpText: 'keys.helpTextApi',
        },
        {
          key: 'ssTlsCertificate',
          name: 'tab.keys.ssTlsCertificate',
          to: {
            name: RouteName.SSTlsCertificate,
          },
          permissions: [Permissions.VIEW_INTERNAL_TLS_CERT],
          helpImage: 'tls_certificate.png',
          helpTitle: 'keys.helpTitleSS',
          helpText: 'keys.helpTextSS',
        },
      ];

      return this.$store.getters.getAllowedTabs(allTabs);
    },
  },

  methods: {
    helpClick(tab: KeysTab): void {
      this.helpTab = tab;
      this.showHelp = true;
    },
    closeHelp(): void {
      this.showHelp = false;
    },
    getIconColor(specTab: string): string {
      if (this.$route.name === specTab) {
        return 'secondary';
      }
      return '#9c9c9c';
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/global-style';

.help-icon {
  margin-left: 20px;
  font-size: 16px;
}

.content {
  width: 1000px;
}
</style>
