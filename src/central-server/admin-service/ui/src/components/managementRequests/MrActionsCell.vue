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
  <div class="cs-table-actions-wrap management-requests-table">
    <div
      v-if="managementRequest.status === 'WAITING'"
      :data-test="`actions-for-MR-${managementRequest.id}`"
    >
      <xrd-button
        v-if="showApproveButton"
        :outlined="false"
        data-test="approve-button"
        text
        @click="showApproveDialog = true"
      >
        {{ $t('action.approve') }}
      </xrd-button>

      <xrd-button
        v-if="showDeclineButton"
        :outlined="false"
        data-test="decline-button"
        text
        @click="showDeclineDialog = true"
      >
        {{ $t('action.decline') }}
      </xrd-button>
    </div>
    <mr-confirm-dialog
      v-if="
        showApproveDialog &&
        managementRequest.id &&
        managementRequest.security_server_id.encoded_id
      "
      :request-id="managementRequest.id"
      :security-server-id="managementRequest.security_server_id.encoded_id"
      :new-member="newMember"
      @approve="approve"
      @cancel="showApproveDialog = false"
    />
    <mr-decline-dialog
      v-if="
        showDeclineDialog &&
        managementRequest.id &&
        managementRequest.security_server_id.encoded_id
      "
      :request-id="managementRequest.id"
      :security-server-id="managementRequest.security_server_id.encoded_id"
      @decline="decline"
      @cancel="showDeclineDialog = false"
    />
  </div>
</template>

<script lang="ts">
import { defineComponent, PropType } from 'vue';
import { ManagementRequestListView, ManagementRequestType } from '@/openapi-types';
import { mapState } from 'pinia';
import { useUser } from '@/store/modules/user';
import { Permissions } from '@/global';
import MrConfirmDialog from '@/components/managementRequests/MrConfirmDialog.vue';
import MrDeclineDialog from '@/components/managementRequests/MrDeclineDialog.vue';

/**
 * General component for Management request actions
 */
export default defineComponent({
  components: {
    MrDeclineDialog,
    MrConfirmDialog,
  },
  props: {
    managementRequest: {
      type: Object as PropType<ManagementRequestListView>,
      required: true,
    },
  },
  emits: ['approve', 'decline'],
  data() {
    return {
      showApproveDialog: false,
      showDeclineDialog: false,
    };
  },
  computed: {
    ...mapState(useUser, ['hasPermission']),
    showApproveButton(): boolean {
      return this.hasPermission(Permissions.VIEW_MANAGEMENT_REQUEST_DETAILS);
    },
    showDeclineButton(): boolean {
      return this.hasPermission(Permissions.VIEW_MANAGEMENT_REQUEST_DETAILS);
    },
    newClientOwner(): boolean {
      const req = this.managementRequest;
      return (
        !!req &&
        req.type === ManagementRequestType.CLIENT_REGISTRATION_REQUEST &&
        !req.client_owner_name
      );
    },
    newServerOwner(): boolean {
      const req = this.managementRequest;
      return (
        !!req &&
        req.type === ManagementRequestType.AUTH_CERT_REGISTRATION_REQUEST &&
        !req.security_server_owner
      );
    },
    newMember(): boolean {
      return this.newClientOwner || this.newServerOwner;
    },
  },
  methods: {
    approve() {
      this.showApproveDialog = false;
      this.$emit('approve');
    },
    decline() {
      this.showDeclineDialog = false;
      this.$emit('decline');
    },
  },
});
</script>

<style lang="scss" scoped>
@import '@/assets/tables';

.management-requests-table {
  min-width: 182px;
}
</style>
