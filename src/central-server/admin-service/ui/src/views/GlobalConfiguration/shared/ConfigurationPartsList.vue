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
  <div id="global-groups" class="mt-5">
    <v-card flat>
      <div class="card-top">
        <div class="card-main-title">{{ $t('globalConf.cfgParts.title') }}</div>
      </div>
      <a ref="downloadRef" hidden>download</a>

      <v-card-text class="px-0">
        <v-data-table
          :loading="loading"
          :headers="headers"
          :items="configurationParts"
          :search="search"
          :must-sort="true"
          :items-per-page="-1"
          item-key="content_identifier"
          :loader-height="2"
          hide-default-footer
        >
          <template #[`item.file_updated_at`]="{ item }">
            {{ item.file_updated_at | formatDateTime }}
          </template>
          <template #[`item.actions`]="{ item }">
            <xrd-button
              v-if="showDownloadButton"
              :outlined="false"
              text
              @click="download(item)"
            >
              {{ $t('action.download') }}
            </xrd-button>
            <xrd-button :outlined="false" text>
              {{ $t('action.upload') }}
            </xrd-button>
          </template>
          <template #footer>
            <div class="custom-footer"></div>
          </template>
        </v-data-table>
      </v-card-text>
    </v-card>
  </div>
</template>

<script lang="ts">
/**
 * View for 'backup and restore' tab
 */
import Vue from 'vue';
import { mapState, mapStores } from 'pinia';
import { useConfigurationSourceStore } from '@/store/modules/configuration-sources';
import { ConfigurationPart, ConfigurationType } from '@/openapi-types';
import { Prop } from 'vue/types/options';
import { DataTableHeader } from 'vuetify';
import { userStore } from '@/store/modules/user';
import { Permissions } from '@/global';
import { AxiosResponse } from 'axios';

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
      search: '' as string,
    };
  },
  computed: {
    ...mapStores(useConfigurationSourceStore),
    ...mapState(userStore, ['hasPermission']),
    configurationParts(): ConfigurationPart[] {
      return this.configurationSourceStore.getConfigurationParts(
        this.configurationType,
      );
    },
    showDownloadButton(): boolean {
      return this.hasPermission(Permissions.DOWNLOAD_CONFIGURATION_PART);
    },
    headers(): DataTableHeader[] {
      return [
        {
          text: this.$t('globalConf.cfgParts.file') as string,
          value: 'fileName',
          class: 'xrd-table-header ts-table-header-server-code text-uppercase',
        },
        {
          text: this.$t('globalConf.cfgParts.contentIdentifier') as string,
          value: 'content_identifier',
          class: 'xrd-table-header ts-table-header-valid-from text-uppercase',
        },
        {
          text: this.$t('globalConf.cfgParts.version') as string,
          value: 'version',
          class: 'xrd-table-header ts-table-header-valid-to text-uppercase',
        },
        {
          text: this.$t('globalConf.cfgParts.updated') as string,
          value: 'file_updated_at',
          class: 'xrd-table-header ts-table-header-valid-to text-uppercase',
        },
        {
          text: '',
          value: 'actions',
          class: 'xrd-table-header ts-table-header-valid-to text-uppercase',
          align: 'end',
        },
      ];
    },
  },
  created() {
    this.fetchConfigurationParts();
  },
  methods: {
    download(item: ConfigurationPart) {
      this.configurationSourceStore
        .downloadConfigurationPartDownloadUrl(
          this.configurationType,
          item.content_identifier,
          item.version,
        )
        .then((res) => {
          const downloadRef = this.$refs.downloadRef as HTMLAnchorElement;
          downloadRef.href = window.URL.createObjectURL(new Blob([res.data]));
          downloadRef.setAttribute('download', this.buildFileName(item, res));
          downloadRef.click();
        });
    },
    fetchConfigurationParts() {
      this.loading = true;
      this.configurationSourceStore
        .fetchConfigurationParts(this.configurationType)
        .finally(() => (this.loading = false));
    },
    buildFileName(item: ConfigurationPart, response: AxiosResponse): string {
      return (
        response.headers['content-disposition']
          ?.split(';')
          .find((part) => part.includes('filename='))
          ?.replace('filename=', '')
          .replace('"', '')
          .trim() ||
        item.fileName ||
        'configuration.xml'
      );
    },
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/tables';

.card-top {
  padding-top: 15px;
  margin-bottom: 10px;
  width: 100%;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}

.card-main-title {
  color: $XRoad-Black100;
  font-style: normal;
  font-weight: bold;
  font-size: 18px;
  line-height: 24px;
  margin-left: 16px;
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
