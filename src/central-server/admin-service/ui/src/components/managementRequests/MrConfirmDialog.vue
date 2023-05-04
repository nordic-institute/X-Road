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
  <xrd-confirm-dialog
    v-if="showDialog"
    :dialog="true"
    title="managementRequests.dialog.approve.title"
    text="managementRequests.dialog.approve.bodyMessage"
    :data="messageData"
    :loading="loading"
    @cancel="showDialog = false"
    @accept="approve()"
  />
</template>

<script lang="ts">
import Vue from 'vue';
import { mapActions, mapStores } from 'pinia';
import { managementRequestsStore } from '@/store/modules/managementRequestStore';
import { notificationsStore } from '@/store/modules/notifications';

/**
 * General component for Management request actions
 */
export default Vue.extend({
  props: {
    requestId: {
      type: Number,
      required: true,
    },
    securityServerId: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      loading: false,
      showDialog: false,
    };
  },
  computed: {
    ...mapStores(managementRequestsStore),
    messageData(): Record<string, unknown> {
      return {
        id: this.requestId,
        serverId: this.securityServerId,
      };
    },
  },
  methods: {
    ...mapActions(notificationsStore, ['showError', 'showSuccess']),
    approve() {
      this.loading = true;
      this.managementRequestsStore
        .approve(this.requestId)
        .then(() => {
          this.showSuccess(
            this.$t(
              'managementRequests.dialog.approve.successMessage',
              this.messageData,
            ),
          );
          this.$emit('approve');
        })
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => {
          this.loading = false;
          this.showDialog = false;
        });
    },
    openDialog() {
      this.showDialog = true;
    },
  },
});
</script>
