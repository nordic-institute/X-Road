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
  <div id="download-url" class="mt-5">
    <v-card flat>
      <div class="card-top">
        <div class="card-main-title">
          {{ $t('globalConf.downloadUrl.title') }}
        </div>
      </div>
      <v-card-title class="card-title">
        {{ $t('globalConf.downloadUrl.urlAddress') }}
      </v-card-title>
      <v-divider></v-divider>
      <v-card-text>
        <v-icon class="internal-conf-icon">mdi-link</v-icon>
        {{ downloadUrl.url }}
      </v-card-text>
      <v-divider class="pb-4"></v-divider>
    </v-card>
  </div>
</template>

<script lang="ts">
/**
 * View for 'backup and restore' tab
 */
import Vue from 'vue';
import { mapStores } from 'pinia';
import { ConfigurationType, GlobalConfDownloadUrl } from '@/openapi-types';
import { Prop } from 'vue/types/options';
import { useConfigurationSourceStore } from '@/store/modules/configuration-sources';

export default Vue.extend({
  props: {
    configurationType: {
      type: String as Prop<ConfigurationType>,
      required: true,
    },
  },
  computed: {
    ...mapStores(useConfigurationSourceStore),
    downloadUrl(): GlobalConfDownloadUrl {
      return this.configurationSourceStore.getDownloadUrl(
        this.configurationType,
      );
    },
  },
  created() {
    this.fetchDownloadUrl();
  },
  methods: {
    fetchDownloadUrl() {
      this.configurationSourceStore.fetchDownloadUrl(this.configurationType);
    },
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/colors';

.card-title {
  font-size: 12px;
  text-transform: uppercase;
  color: $XRoad-Black70;
  font-weight: bold;
  padding-top: 5px;
  padding-bottom: 5px;
}

.card-main-title {
  color: $XRoad-Black100;
  font-style: normal;
  font-weight: bold;
  font-size: 18px;
  line-height: 24px;
  margin-left: 16px;
}

.card-top {
  padding-top: 15px;
  margin-bottom: 10px;
  width: 100%;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}

.internal-conf-icon {
  margin-right: 15px;
  color: $XRoad-Purple100;
}
</style>
