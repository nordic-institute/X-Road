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
  <!-- Configuration parts list -->
  <div id="anchor" class="mt-4">
    <v-card flat>
      <div class="card-top">
        <div class="card-main-title">Anchor</div>
        <div class="card-corner-button pr-4">
          <xrd-button outlined class="mr-4">
            <xrd-icon-base class="xrd-large-button-icon">
              <XrdIconAdd />
            </xrd-icon-base>

            Re-create
          </xrd-button>
          <xrd-button outlined>
            <xrd-icon-base class="xrd-large-button-icon">
              <XrdIconDownload />
            </xrd-icon-base>
            Download
          </xrd-button>
        </div>
      </div>
      <v-card-text class="px-0">
        <xrd-table id="global-groups-table">
          <thead>
            <tr>
              <th>{{ $t('globalConf.anchor.certificateHash') }}</th>
              <th>{{ $t('globalConf.anchor.created') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>
                <xrd-icon-base class="internal-conf-icon">
                  <XrdIconCertificate />
                </xrd-icon-base>
                {{ configurationAnchor.hash }}
              </td>
              <td>{{ configurationAnchor.created_at | formatDateTime }}</td>
            </tr>
          </tbody>
        </xrd-table>
      </v-card-text>
    </v-card>
  </div>
</template>

<script lang="ts">
/**
 * View for 'backup and restore' tab
 */
import Vue from 'vue';
import { mapStores } from 'pinia';
import { ConfigurationAnchor, ConfigurationType } from '@/openapi-types';
import { useConfigurationAnchorStore } from '@/store/modules/configuration-anchors';
import { Prop } from 'vue/types/options';

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
    ...mapStores(useConfigurationAnchorStore),
    configurationAnchor(): ConfigurationAnchor | null {
      return this.configurationAnchorStore.getAnchor(this.configurationType);
    },
  },
  created() {
    this.fetchConfigurationAnchor();
  },
  methods: {
    fetchConfigurationAnchor() {
      this.loading = true;
      this.configurationAnchorStore
        .fetchConfigurationAnchor(this.configurationType)
        .finally(() => (this.loading = false));
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
