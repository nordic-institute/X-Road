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
  <article id="global-groups" class="mt-5">
    <v-data-table
      :loading="loading"
      :headers="headers"
      :items="configurationParts"
      :search="search"
      :sort-by="['file_name']"
      :must-sort="true"
      :items-per-page="-1"
      item-value="content_identifier"
      :loader-height="2"
      class="elevation-0 data-table"
    >
      <template #top>
        <data-table-toolbar title-key="globalConf.cfgParts.title" />
      </template>
      <template #[`item.file_updated_at`]="{ item }">
        <span
          :data-test="`configuration-part-${item.content_identifier}-updated-at`"
        >
          <date-time :value="item.file_updated_at" with-seconds />
        </span>
      </template>
      <template #[`item.content_identifier`]="{ item }">
        <span :data-test="`configuration-part-${item.content_identifier}`">
          {{ item.content_identifier }}
        </span>
      </template>
      <template #[`item.version`]="{ item }">
        <template v-if="item.version === 0">
          {{ $t('globalConf.cfgParts.allVersions') }}
        </template>
        <template v-else>
          {{ item.version }}
        </template>
      </template>
      <template #[`item.actions`]="{ item }">
        <configuration-part-download-button
          :configuration-type="configurationType"
          :configuration-part="item"
        />
        <configuration-part-upload-button
          :configuration-type="configurationType"
          :configuration-part="item"
          @save="fetchConfigurationParts"
        />
      </template>
      <template #bottom>
        <custom-data-table-footer />
      </template>
    </v-data-table>
  </article>
</template>

<script lang="ts">
/**
 * View for 'backup and restore' tab
 */
import { defineComponent, PropType } from 'vue';
import { mapState, mapStores } from 'pinia';
import { useConfigurationSource } from '@/store/modules/configuration-sources';
import { ConfigurationPart, ConfigurationType } from '@/openapi-types';
import { DataTableHeader } from '@/ui-types';
import { VDataTable } from 'vuetify/labs/VDataTable';
import { useUser } from '@/store/modules/user';
import ConfigurationPartDownloadButton from './ConfigurationPartDownloadButton.vue';
import ConfigurationPartUploadButton from './ConfigurationPartUploadButton.vue';
import CustomDataTableFooter from '@/components/ui/CustomDataTableFooter.vue';
import DateTime from '@/components/ui/DateTime.vue';
import DataTableToolbar from '@/components/ui/DataTableToolbar.vue';

export default defineComponent({
  components: {
    DataTableToolbar,
    DateTime,
    CustomDataTableFooter,
    VDataTable,
    ConfigurationPartUploadButton,
    ConfigurationPartDownloadButton,
  },
  props: {
    configurationType: {
      type: String as PropType<ConfigurationType>,
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
    ...mapStores(useConfigurationSource),
    ...mapState(useUser, ['hasPermission']),
    configurationParts(): ConfigurationPart[] {
      return this.configurationSourceStore.getConfigurationParts(
        this.configurationType,
      );
    },

    headers(): DataTableHeader[] {
      return [
        {
          title: this.$t('globalConf.cfgParts.file') as string,
          key: 'file_name',
        },
        {
          title: this.$t('globalConf.cfgParts.contentIdentifier') as string,
          key: 'content_identifier',
        },
        {
          title: this.$t('globalConf.cfgParts.version') as string,
          key: 'version',
        },
        {
          title: this.$t('globalConf.cfgParts.updated') as string,
          key: 'file_updated_at',
        },
        {
          title: '',
          key: 'actions',
          align: 'end',
        },
      ];
    },
  },
  created() {
    this.fetchConfigurationParts();
  },
  methods: {
    fetchConfigurationParts() {
      this.loading = true;
      this.configurationSourceStore
        .fetchConfigurationParts(this.configurationType)
        .finally(() => (this.loading = false));
    },
  },
});
</script>

<style lang="scss" scoped>
@import '@/assets/tables';

.internal-conf-icon {
  margin-right: 15px;
  color: $XRoad-Purple100;
}
</style>
