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
  <div>
    <!-- Internal configuration -->
    <div class="header-row">
      <div class="title-search">
        <div class="xrd-view-title">Trusted anchors</div>
      </div>

      <upload-trusted-anchor-button @uploaded="fetchTrustedAnchors" />
    </div>

    <!-- Anchor -->
    <div id="anchors" class="mt-4">
      <XrdEmptyPlaceholder
        :data="trustedAnchors"
        :loading="loading"
        :no-items-text="$t('noData.noData')"
        skeleton-type="table-heading"
      />
      <configuration-anchor-item
        v-for="anchor in trustedAnchors"
        :key="anchor.title"
        :anchor="anchor"
      >
        <download-trusted-anchor-button :hash="anchor.hash" class="mr-4" />
        <delete-trusted-anchor-button
          :hash="anchor.hash"
          :identifier="anchor.title"
          @deleted="fetchTrustedAnchors"
        />
      </configuration-anchor-item>
    </div>
  </div>
</template>

<script lang="ts">
/**
 * View for 'backup and restore' tab
 */
import Vue from 'vue';
import { DataTableHeader } from 'vuetify';
import ConfigurationAnchorItem, {
  Anchor,
} from '@/views/GlobalConfiguration/shared/ConfigurationAnchorItem.vue';
import { TrustedAnchor } from '@/openapi-types';
import { mapActions, mapState, mapStores } from 'pinia';
import { userStore } from '@/store/modules/user';
import { trustedAnchorStore } from '@/store/modules/trusted-anchors';
import { notificationsStore } from '@/store/modules/notifications';
import UploadTrustedAnchorButton from '@/components/trustedAnchors/UploadTrustedAnchorButton.vue';
import DownloadTrustedAnchorButton from '@/components/trustedAnchors/DownloadTrustedAnchorButton.vue';
import DeleteTrustedAnchorButton from '@/components/trustedAnchors/DeleteTrustedAnchorButton.vue';

function convert(source: TrustedAnchor): Anchor {
  return {
    hash: source.hash,
    createdAt: source.generated_at,
    title: source.instance_identifier,
  };
}

export default Vue.extend({
  components: {
    DeleteTrustedAnchorButton,
    DownloadTrustedAnchorButton,
    UploadTrustedAnchorButton,
    ConfigurationAnchorItem,
  },
  data() {
    return {
      loading: false,
      trustedAnchors: [] as Anchor[],
    };
  },
  computed: {
    ...mapStores(trustedAnchorStore),
    ...mapState(userStore, ['hasPermission']),
    headers(): DataTableHeader[] {
      return [
        {
          text: 'Certificate HASH (SHA-224)',
          align: 'start',
          value: 'hash',
          class: 'xrd-table-header tra-table-header-hash',
        },
        {
          text: this.$t('global.created') as string,
          align: 'start',
          value: 'created',
          class: 'xrd-table-header tra-table-header-created',
        },

        {
          text: '',
          value: 'button',
          sortable: false,
          class: 'xrd-table-header tra-table-header-buttons',
        },
      ];
    },
  },
  created() {
    this.fetchTrustedAnchors();
  },
  methods: {
    ...mapActions(notificationsStore, ['showSuccess', 'showError']),
    fetchTrustedAnchors() {
      this.loading = true;
      this.trustedAnchorStore
        .fetchTrustedAnchors()
        .then((resp) => (this.trustedAnchors = resp.data.map(convert)))
        .catch((error) => this.showError(error))
        .finally(() => (this.loading = false));
    },
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/colors';
@import '~styles/tables';

.card-title {
  font-size: 12px;
  text-transform: uppercase;
  color: $XRoad-Black70;
  font-weight: bold;
  padding-top: 5px;
  padding-bottom: 5px;
}

.card-corner-button {
  display: flex;
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

.card-main-title {
  color: $XRoad-Black100;
  font-style: normal;
  font-weight: bold;
  font-size: 18px;
  line-height: 24px;
  margin-left: 16px;
}

.hash-cell {
  display: flex;
  flex-direction: row;
  align-items: center;
}

.cert-icon {
  margin-right: 10px;
  color: $XRoad-Purple100;
}

.icon-column-wrap {
  display: flex;
  flex-direction: row;
  align-items: center;
}
</style>
