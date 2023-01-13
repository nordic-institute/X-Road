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
import Vue, { PropType } from 'vue';
import { ManagementRequest } from '@/openapi-types';
import { mapActions, mapStores } from 'pinia';
import { managementRequestsStore } from '@/store/modules/managementRequestStore';
import { notificationsStore } from '@/store/modules/notifications';

/**
 * General component for Management request actions
 */
export default Vue.extend({
  props: {
    managementRequest: {
      type: Object as PropType<ManagementRequest>,
      required: true,
    },
  },
  data() {
    return {
      loading: false,
      showDialog: false,
      messageData: {
        id: this.managementRequest.id,
        serverId: this.managementRequest.security_server_id,
      },
    };
  },
  computed: {
    ...mapStores(managementRequestsStore),
  },
  methods: {
    ...mapActions(notificationsStore, ['showError', 'showSuccess']),
    approve() {
      if (!this.managementRequest.id) {
        return;
      }
      this.loading = true;
      this.managementRequestsStore
        .approve(this.managementRequest.id)
        .then((res) => {
          this.showSuccess(
            this.$t(
              'managementRequests.dialog.approve.successMessage',
              this.messageData,
            ),
          );
          this.showDialog = false;
          this.$emit('approve');
        })
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => {
          this.loading = false;
        });
    },
    openDialog() {
      this.showDialog = true;
    },
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/tables';

#management-request-filters {
  display: flex;
  justify-content: space-between;
}

.request-id {
  color: $XRoad-Purple100;
  font-weight: 600;
  font-size: 14px;
}

.align-fix {
  align-items: center;
}

.margin-fix {
  margin-top: -10px;
}

.custom-checkbox {
  .v-label {
    font-size: 14px;
  }
}

.only-pending {
  display: flex;
  justify-content: flex-end;
}

.management-requests-table {
  min-width: 182px;
}
</style>
