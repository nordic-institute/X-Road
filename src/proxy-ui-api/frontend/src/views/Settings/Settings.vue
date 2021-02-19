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
  <div class="wrapper xrd-view-common">
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
      </v-tab>
    </v-tabs>
    <div class="content">
      <router-view />
    </div>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { Permissions, RouteName } from '@/global';
import { Tab } from '@/ui-types';

export default Vue.extend({
  data() {
    return {
      tab: null,
    };
  },
  computed: {
    tabs(): Tab[] {
      const allTabs: Tab[] = [
        {
          key: 'system',
          name: 'tab.settings.systemParameters',
          to: {
            name: RouteName.SystemParameters,
          },
          permissions: [Permissions.VIEW_SYS_PARAMS],
        },
        {
          key: 'backup',
          name: 'tab.settings.backupAndRestore',
          to: {
            name: RouteName.BackupAndRestore,
          },
          permissions: [Permissions.BACKUP_CONFIGURATION],
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
