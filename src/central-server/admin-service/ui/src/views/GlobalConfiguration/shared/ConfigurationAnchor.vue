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
  <configuration-anchor-item :anchor="anchor" :loading="loading">
    <xrd-button
      v-if="showRecreateAnchorButton"
      data-test="re-create-anchor-button"
      :loading="recreating"
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
      :loading="downloading"
      outlined
      @click="downloadConfigurationAnchor()"
    >
      <xrd-icon-base class="xrd-large-button-icon">
        <XrdIconDownload />
      </xrd-icon-base>
      {{ $t('globalConf.anchor.download') }}
    </xrd-button>
  </configuration-anchor-item>
</template>

<script lang="ts">
import { Permissions } from '@/global';
import { useNotifications } from '@/store/modules/notifications';
import { useUser } from '@/store/modules/user';
import { defineComponent, PropType } from 'vue';
import { mapActions, mapState, mapStores } from 'pinia';
import { ConfigurationType } from '@/openapi-types';
import { useConfigurationSource } from '@/store/modules/configuration-sources';
import { DataTableHeader } from '@/ui-types';
import ConfigurationAnchorItem, {
  Anchor,
} from '@/views/GlobalConfiguration/shared/ConfigurationAnchorItem.vue';
import {XrdIconDownload} from '@niis/shared-ui';

export default defineComponent({
  components: { ConfigurationAnchorItem, XrdIconDownload },
  props: {
    configurationType: {
      type: String as PropType<ConfigurationType>,
      required: true,
    },
  },
  data() {
    return {
      loading: false,
      downloading: false,
      recreating: false,
    };
  },
  computed: {
    ...mapStores(useConfigurationSource),
    ...mapState(useUser, ['hasPermission']),
    anchor(): Anchor | null {
      const title = this.$t('globalConf.anchor.title').toString();
      const anchor = this.configurationSourceStore.getAnchor(
        this.configurationType,
      );
      return anchor.hash
        ? { hash: anchor.hash, createdAt: anchor.created_at, title }
        : null;
    },
    headers(): DataTableHeader[] {
      return [
        {
          title: this.$t('globalConf.anchor.certificateHash') as string,
          align: 'start',
          key: 'hash',
        },
        {
          title: this.$t('globalConf.anchor.created') as string,
          align: 'start',
          key: 'created_at',
        },
      ];
    },
    showDownloadAnchorButton(): boolean {
      return (
        this.hasPermission(Permissions.DOWNLOAD_SOURCE_ANCHOR) &&
        this.configurationSourceStore.hasAnchor(this.configurationType)
      );
    },
    showRecreateAnchorButton(): boolean {
      return this.hasPermission(Permissions.GENERATE_SOURCE_ANCHOR);
    },
    formattedConfigurationType(): string {
      return (
        this.configurationType.charAt(0).toUpperCase() +
        this.configurationType.slice(1).toLowerCase()
      );
    },
  },
  created() {
    this.fetchConfigurationAnchor();
  },
  methods: {
    ...mapActions(useNotifications, ['showSuccess', 'showError']),
    fetchConfigurationAnchor() {
      this.loading = true;
      this.configurationSourceStore
        .fetchConfigurationAnchor(this.configurationType)
        .catch(this.showError)
        .finally(() => (this.loading = false));
    },
    downloadConfigurationAnchor() {
      this.downloading = true;
      this.configurationSourceStore
        .downloadConfigurationAnchor(this.configurationType)
        .catch(this.showError)
        .finally(() => (this.downloading = false));
    },
    recreateConfigurationAnchor() {
      this.recreating = true;
      this.configurationSourceStore
        .recreateConfigurationAnchor(this.configurationType)
        .then(() =>
          this.showSuccess(
            this.$t(`globalConf.anchor.recreateSuccess`, {
              configurationType: this.formattedConfigurationType,
            }),
          ),
        )
        .catch(this.showError)
        .finally(() => (this.recreating = false));
    },
  },
});
</script>

<style lang="scss" scoped>
@import '@/assets/colors';

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
