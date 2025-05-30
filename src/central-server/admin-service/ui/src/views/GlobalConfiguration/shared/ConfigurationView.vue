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
  <xrd-titled-view title-key="keys.title">
    <tokens-list
      :configuration-type="configurationType"
      @update-keys="refreshAnchor"
    />

    <!-- Internal configuration -->
    <div class="header-row mt-6">
      <div class="xrd-view-title">{{ title }}</div>
    </div>

    <!-- Anchor -->
    <configuration-anchor
      ref="anchor"
      :configuration-type="configurationType"
    />

    <!-- Download URL -->
    <configuration-download-url :configuration-type="configurationType" />

    <!-- Configuration parts -->
    <configuration-parts-list :configuration-type="configurationType" />
  </xrd-titled-view>
</template>

<script lang="ts">
/**
 * View for 'backup and restore' tab
 */
import { defineComponent, PropType } from 'vue';
import ConfigurationAnchor from './ConfigurationAnchor.vue';
import ConfigurationPartsList from '@/components/configurationParts/ConfigurationPartsList.vue';
import ConfigurationDownloadUrl from './ConfigurationDownloadUrl.vue';
import { ConfigurationType } from '@/openapi-types';
import TokensList from '@/components/tokens/TokensList.vue';
import { XrdTitledView } from '@niis/shared-ui';

export default defineComponent({
  components: {
    XrdTitledView,
    TokensList,
    ConfigurationDownloadUrl,
    ConfigurationAnchor,
    ConfigurationPartsList,
  },
  props: {
    title: {
      type: String,
      required: true,
    },
    configurationType: {
      type: String as PropType<ConfigurationType>,
      required: true,
    },
  },
  methods: {
    refreshAnchor(action: string) {
      if (action === 'add' || action === 'delete') {
        (
          this.$refs.anchor as InstanceType<typeof ConfigurationAnchor>
        ).fetchConfigurationAnchor();
      }
    },
  },
});
</script>

<style lang="scss" scoped>
@use '@niis/shared-ui/src/assets/colors';
</style>
