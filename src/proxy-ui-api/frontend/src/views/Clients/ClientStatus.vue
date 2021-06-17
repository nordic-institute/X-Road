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
  <div class="status-wrapper">
    <xrd-status-icon :status="statusIconType" />
    <div class="status-text">{{ getStatusText(status) }}</div>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';

export default Vue.extend({
  props: {
    status: {
      type: String,
      default: undefined,
    },
  },

  computed: {
    statusIconType(): string {
      if (!this.status) {
        return '';
      }
      switch (this.status.toLowerCase()) {
        case 'registered':
          return 'ok';
        case 'registration_in_progress':
          return 'progress-register';
        case 'saved':
          return 'saved';
        case 'deletion_in_progress':
          return 'progress-delete';
        case 'global_error':
          return 'error';
        default:
          return 'error';
      }
    },
  },

  methods: {
    getStatusText(status: string): string {
      if (!status) {
        return '';
      }
      switch (status.toLowerCase()) {
        case 'registered':
          return this.$t('client.statusText.registered') as string;
        case 'registration_in_progress':
          return this.$t('client.statusText.registrationInProgress') as string;
        case 'saved':
          return this.$t('client.statusText.saved') as string;
        case 'deletion_in_progress':
          return this.$t('client.statusText.deletionInProgress') as string;
        case 'global_error':
          return this.$t('client.statusText.globalError') as string;
        default:
          return '';
      }
    },
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/colors';

.status-wrapper {
  display: flex;
  flex-direction: row;
  align-items: center;
}

.status-text {
  font-style: normal;
  font-weight: bold;
  font-size: 12px;
  line-height: 16px;
  color: $XRoad-WarmGrey100;
  margin-left: 2px;
}
</style>
