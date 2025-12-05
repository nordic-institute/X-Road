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
  <XrdView data-test="diagnostics-view" title="tab.main.diagnostics">
    <template #tabs>
      <DiagnosticsTabs />
    </template>

    <XrdSubView>
      <ConnectionCentralServerView class="mt-0" />

      <XrdEmptyPlaceholder
        :data="xRoadInstances"
        :loading="loading"
        :no-items-text="$t('noData.noData')"
        skeleton-type="table-heading"
        :skeleton-count="2"
      />

      <template v-if="!loading">
        <ConnectionSecurityServerView class="mt-0" />
        <ConnectionManagementView class="mt-0" />
      </template>
    </XrdSubView>
  </XrdView>
</template>

<script setup lang="ts">
import { XrdSubView, XrdView } from '@niis/shared-ui';
import DiagnosticsTabs from '@/views/Diagnostics/DiagnosticsTabs.vue';
import ConnectionCentralServerView from '@/views/Diagnostics/Connection/ConnectionCentralServerView.vue';
import ConnectionSecurityServerView from "@/views/Diagnostics/Connection/ConnectionSecurityServerView.vue";
import ConnectionManagementView from "@/views/Diagnostics/Connection/ConnectionManagementView.vue";
import { mapActions, mapState } from "pinia";
import { useGeneral } from "@/store/modules/general";
import { useClients } from "@/store/modules/clients";

export default defineComponent({
  name: 'ConnectionTestingView',
  components: {
    ConnectionManagementView,
    ConnectionSecurityServerView,
    XrdTitledView,
    ConnectionCentralServerView
  },

  data() {
    return {
      loading: false
    }
  },

  computed: {
    ...mapState(useGeneral, ['xRoadInstances']),
  },

  async created() {
    this.loading = true
    try {
      await Promise.all([
        this.fetchXRoadInstances(),
        this.fetchClients(),
      ])

    } finally {
      this.loading = false
    }
  },

  methods: {
    ...mapActions(useGeneral, ['fetchXRoadInstances']),
    ...mapActions(useClients, ['fetchClients'])
  }
});
</script>
