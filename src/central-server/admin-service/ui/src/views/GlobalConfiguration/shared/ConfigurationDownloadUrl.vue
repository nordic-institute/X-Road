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
      <v-data-table
        v-if="urls"
        :headers="headers"
        :items="urls"
        :items-per-page="-1"
        :loading="loading"
        item-key="url"
        hide-default-footer
        class="anchors-table"
      >
        <template #[`item.url`]="{ item }">
          <div class="xrd-clickable" @click="openInNewTab(item.url)">
            <v-icon class="internal-conf-icon">mdi-link</v-icon>
            {{ item.url }}
          </div>
        </template>
        <template #footer>
          <div class="custom-footer"></div>
        </template>
      </v-data-table>
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
import { DataTableHeader } from 'vuetify';

export default Vue.extend({
  props: {
    configurationType: {
      type: String as Prop<ConfigurationType>,
      required: true,
    },
  },
  data() {
    return {
      loading: false,
    };
  },
  computed: {
    ...mapStores(useConfigurationSourceStore),
    urls(): GlobalConfDownloadUrl[] {
      return [
        this.configurationSourceStore.getDownloadUrl(this.configurationType),
      ];
    },
    headers(): DataTableHeader[] {
      return [
        {
          text: this.$t('globalConf.downloadUrl.urlAddress') as string,
          align: 'start',
          value: 'url',
          class: 'xrd-table-header text-uppercase',
        },
      ];
    },
  },
  created() {
    this.fetchDownloadUrl();
  },
  methods: {
    fetchDownloadUrl() {
      this.loading = true;
      this.configurationSourceStore
        .fetchDownloadUrl(this.configurationType)
        .finally(() => (this.loading = false));
    },
    openInNewTab(url: string) {
      window.open(url, '_blank', 'noreferrer');
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

.custom-footer {
  border-top: thin solid rgba(0, 0, 0, 0.12); /* Matches the color of the Vuetify table line */
  height: 16px;
}
</style>
