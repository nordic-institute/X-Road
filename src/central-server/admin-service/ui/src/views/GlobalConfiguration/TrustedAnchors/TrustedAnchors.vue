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
  <xrd-titled-view title-key="tab.globalConf.trustedAnchors">
    <template #header-buttons>
      <upload-trusted-anchor-button @uploaded="fetchTrustedAnchors" />
    </template>

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
  </xrd-titled-view>
</template>

<script lang="ts">
/**
 * View for 'backup and restore' tab
 */
import { defineComponent } from 'vue';
import ConfigurationAnchorItem, {
  Anchor,
} from '@/views/GlobalConfiguration/shared/ConfigurationAnchorItem.vue';
import { TrustedAnchor } from '@/openapi-types';
import { mapActions, mapState, mapStores } from 'pinia';
import { useUser } from '@/store/modules/user';
import { useTrustedAnchor } from '@/store/modules/trusted-anchors';
import { useNotifications } from '@/store/modules/notifications';
import UploadTrustedAnchorButton from '@/components/trustedAnchors/UploadTrustedAnchorButton.vue';
import DownloadTrustedAnchorButton from '@/components/trustedAnchors/DownloadTrustedAnchorButton.vue';
import DeleteTrustedAnchorButton from '@/components/trustedAnchors/DeleteTrustedAnchorButton.vue';
import { XrdTitledView } from '@niis/shared-ui';

function convert(source: TrustedAnchor): Anchor {
  return {
    hash: source.hash,
    createdAt: source.generated_at,
    title: source.instance_identifier,
  };
}

export default defineComponent({
  components: {
    XrdTitledView,
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
    ...mapStores(useTrustedAnchor),
    ...mapState(useUser, ['hasPermission']),
  },
  created() {
    this.fetchTrustedAnchors();
  },
  methods: {
    ...mapActions(useNotifications, ['showSuccess', 'showError']),
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
@use '@niis/shared-ui/src/assets/colors';
@use '@niis/shared-ui/src/assets/tables' as *;
</style>
