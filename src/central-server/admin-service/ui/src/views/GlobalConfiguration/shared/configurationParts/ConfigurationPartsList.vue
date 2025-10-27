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
  <XrdCard id="configuration-parts" title="globalConf.cfgParts.title">
    <v-data-table
      class="xrd"
      item-value="content_identifier"
      hide-default-footer
      :loading="loading"
      :headers="headers"
      :items="configurationParts"
      :search="search"
      :sort-by="sortBy"
      :must-sort="true"
      :items-per-page="-1"
      :loader-height="2"
    >
      <template #[`item.file_name`]="{ item }">
        <XrdLabelWithIcon icon="lock_open" :label="item.file_name" />
      </template>
      <template #[`item.file_updated_at`]="{ item }">
        <XrdDateTime
          :data-test="`configuration-part-${item.content_identifier}-updated-at`"
          :value="item.file_updated_at"
          with-seconds
        />
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
        <ConfigurationPartDownloadButton
          :configuration-type="configurationType"
          :configuration-part="item"
        />
        <ConfigurationPartUploadButton
          :configuration-type="configurationType"
          :configuration-part="item"
          @save="fetchConfigurationParts"
        />
      </template>
    </v-data-table>
  </XrdCard>
</template>

<script lang="ts">
/**
 * View for 'backup and restore' tab
 */
import { defineComponent, PropType } from 'vue';

import { SortItem } from 'vuetify/lib/components/VDataTable/composables/sort';
import { DataTableHeader } from 'vuetify/lib/components/VDataTable/types';
import { mapState, mapStores } from 'pinia';

import { XrdCard, XrdDateTime, XrdLabelWithIcon } from '@niis/shared-ui';

import { ConfigurationPart, ConfigurationType } from '@/openapi-types';
import { useConfigurationSource } from '@/store/modules/configuration-sources';
import { useUser } from '@/store/modules/user';

import ConfigurationPartDownloadButton from './ConfigurationPartDownloadButton.vue';
import ConfigurationPartUploadButton from './ConfigurationPartUploadButton.vue';

export default defineComponent({
  components: {
    XrdCard,
    XrdDateTime,
    ConfigurationPartUploadButton,
    ConfigurationPartDownloadButton,
    XrdLabelWithIcon,
  },
  props: {
    configurationType: {
      type: String as PropType<ConfigurationType>,
      required: true,
    },
  },
  data() {
    return {
      sortBy: [{ key: 'file_name' }] as SortItem[],
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
