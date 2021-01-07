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
  <div class="xrd-view-common xrd-sub-view-wrapper">
    <v-tabs
      v-model="tab"
      background-color="#F4F3F6"
      class="xrd-tabs"
      color="primary"
      slider-size="2"
    >
      <v-tabs-slider
        color="primary"
        class="xrd-sub-tabs-slider"
      ></v-tabs-slider>
      <v-tab v-for="tab in tabs" v-bind:key="tab.key" :to="tab.to" exact>
        {{ $t(tab.name) }}
      </v-tab>
    </v-tabs>
    <alerts-container />
    <div class="content">
      <router-view />
    </div>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { RouteName } from '@/global';
import { Tab } from '@/ui-types';
import AlertsContainer from '@/components/ui/AlertsContainer.vue';

export default Vue.extend({
  components: {
    AlertsContainer,
  },
  data: () => ({
    tab: null,
    showHelp: false,
    helpTab: null as Tab | null,
  }),

  computed: {
    tabs(): Tab[] {
      const allTabs: Tab[] = [
        {
          key: 'signAndAuthKeys',
          name: 'tab.keys.signAndAuthKeys',
          to: {
            name: RouteName.SignAndAuthKeys,
          },
        },
        {
          key: 'apiKey',
          name: 'tab.keys.apiKey',
          to: {
            name: RouteName.ApiKey,
          },
        },
        {
          key: 'ssTlsCertificate',
          name: 'tab.keys.ssTlsCertificate',
          to: {
            name: RouteName.SSTlsCertificate,
          },
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
