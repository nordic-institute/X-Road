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
  <div style="display: inline-block">
    <xrd-button
      v-if="showDownloadButton"
      :data-test="`configuration-part-${configurationPart.content_identifier}-download`"
      :outlined="false"
      :loading="loading"
      text
      @click="download"
    >
      {{ $t('action.download') }}
    </xrd-button>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapState, mapStores } from 'pinia';
import { useConfigurationSourceStore } from '@/store/modules/configuration-sources';
import { ConfigurationPart, ConfigurationType } from '@/openapi-types';
import { Prop } from 'vue/types/options';
import { userStore } from '@/store/modules/user';
import { Permissions } from '@/global';

export default Vue.extend({
  props: {
    configurationType: {
      type: String as Prop<ConfigurationType>,
      required: true,
    },
    configurationPart: {
      type: Object as Prop<ConfigurationPart>,
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
    ...mapState(userStore, ['hasPermission']),

    showDownloadButton(): boolean {
      return (
        this.hasPermission(Permissions.DOWNLOAD_CONFIGURATION_PART) &&
        (this.configurationPart.file_updated_at?.length || 0) > 0
      );
    },
  },
  methods: {
    download() {
      this.loading = true;
      this.configurationSourceStore
        .downloadConfigurationPartDownloadUrl(
          this.configurationType,
          this.configurationPart.content_identifier,
          this.configurationPart.version,
        )
        .finally(() => (this.loading = false));
    },
  },
});
</script>
