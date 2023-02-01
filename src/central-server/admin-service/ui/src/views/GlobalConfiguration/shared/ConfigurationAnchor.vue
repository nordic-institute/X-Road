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
  <div id="anchor" class="mt-4">
    <v-card flat>
      <div class="card-top">
        <div class="card-main-title">{{ $t('globalConf.anchor.title') }}</div>
        <div class="card-corner-button pr-4">
          <xrd-button
            v-if="showRecreateAnchorButton"
            data-test="re-create-anchor-button"
            :loading="loading"
            outlined
            class="mr-4"
            @click="recreateConfigurationAnchor()"
          >
            <xrd-icon-base class="xrd-large-button-icon">
              <XrdIconAdd />
            </xrd-icon-base>

            {{ $t('globalConf.anchor.recreate') }}
          </xrd-button>
          <xrd-button
            v-if="showDownloadAnchorButton"
            data-test="download-anchor-button"
            :loading="loading"
            outlined
            @click="downloadConfigurationAnchor()"
          >
            <xrd-icon-base class="xrd-large-button-icon">
              <XrdIconDownload />
            </xrd-icon-base>
            {{ $t('globalConf.anchor.download') }}
          </xrd-button>
          <a ref="downloadRef">download</a>
        </div>
      </div>
      <v-card-text class="px-0">
        <v-data-table
          v-if="anchors"
          :headers="headers"
          :items="anchors"
          :items-per-page="-1"
          :loading="loading"
          item-key="hash"
          hide-default-footer
          class="anchors-table"
        >
          <template #[`item.hash`]="{ item }">
            <xrd-icon-base class="internal-conf-icon">
              <XrdIconCertificate />
            </xrd-icon-base>
            <span data-test="anchor-hash">{{ item.hash }}</span>
          </template>
          <template #[`item.created_at`]="{ item }">
            <span data-test="anchor-created-at">{{
              item.created_at | formatDateTime
            }}</span>
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
import { Permissions } from '@/global';
import { notificationsStore } from '@/store/modules/notifications';
import { userStore } from '@/store/modules/user';
import Vue from 'vue';
import { mapActions, mapState, mapStores } from 'pinia';
import { ConfigurationAnchor, ConfigurationType } from '@/openapi-types';
import { useConfigurationSourceStore } from '@/store/modules/configuration-sources';
import { Prop } from 'vue/types/options';
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
    ...mapState(userStore, ['hasPermission']),
    anchors(): ConfigurationAnchor[] {
      const anchor = this.configurationSourceStore.getAnchor(
        this.configurationType,
      );
      return anchor.hash ? [anchor] : [];
    },
    headers(): DataTableHeader[] {
      return [
        {
          text: this.$t('globalConf.anchor.certificateHash') as string,
          align: 'start',
          value: 'hash',
          class: 'xrd-table-header text-uppercase',
        },
        {
          text: this.$t('globalConf.anchor.created') as string,
          align: 'start',
          value: 'created_at',
          class: 'xrd-table-header text-uppercase',
        },
      ];
    },
    showDownloadAnchorButton(): boolean {
      return this.hasPermission(Permissions.DOWNLOAD_SOURCE_ANCHOR);
    },
    showRecreateAnchorButton(): boolean {
      return this.hasPermission(Permissions.GENERATE_SOURCE_ANCHOR);
    },
  },
  created() {
    this.fetchConfigurationAnchor();
  },
  methods: {
    ...mapActions(notificationsStore, ['showSuccess']),
    fetchConfigurationAnchor() {
      this.loading = true;
      this.configurationSourceStore
        .fetchConfigurationAnchor(this.configurationType)
        .finally(() => (this.loading = false));
    },
    downloadConfigurationAnchor() {
      this.loading = true;
      this.configurationSourceStore
        .downloadConfigurationAnchor(this.configurationType)
        .finally(() => (this.loading = false));
    },
    recreateConfigurationAnchor() {
      this.loading = true;
      this.configurationSourceStore
        .recreateConfigurationAnchor(this.configurationType)
        .finally(() => {
          this.loading = false;
          const formattedConfigurationType =
            this.configurationType.charAt(0).toUpperCase() +
            this.configurationType.slice(1).toLowerCase();
          this.showSuccess(
            this.$t(`globalConf.anchor.recreateSuccess`, {
              configurationType: formattedConfigurationType,
            }),
          );
        });
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
