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
        @click="$refs.approveDialog.openDialog()"
      >
        {{ $t('action.approve') }}
      </xrd-button>

      <xrd-button
        v-if="showDeclineButton"
        :outlined="false"
        data-test="decline-button"
        text
        @click="$refs.declineDialog.openDialog()"
      >
        {{ $t('action.decline') }}
      </xrd-button>
    </div>
    <management-request-confirm-dialog
      ref="approveDialog"
      :management-request="managementRequest"
      @approve="$emit('approve')"
    />
    <management-request-decline-dialog
      ref="declineDialog"
      :management-request="managementRequest"
      @decline="$emit('decline')"
    />
  </div>
</template>

<script lang="ts">
import Vue, { PropType } from 'vue';
import { ManagementRequestListView } from '@/openapi-types';
import { mapState } from 'pinia';
import { userStore } from '@/store/modules/user';
import { Permissions } from '@/global';
import ManagementRequestConfirmDialog from './ManagementRequestConfirmDialog.vue';
import ManagementRequestDeclineDialog from './ManagementRequestDeclineDialog.vue';

/**
 * General component for Management request actions
 */
export default Vue.extend({
  components: {
    ManagementRequestDeclineDialog,
    ManagementRequestConfirmDialog,
  },
  props: {
    managementRequest: {
      type: Object as PropType<ManagementRequestListView>,
      required: true,
    },
  },
  data() {
    return {
      showApproveDialog: false,
    };
  },
  computed: {
    ...mapState(userStore, ['hasPermission']),
    showApproveButton(): boolean {
      return this.hasPermission(Permissions.VIEW_MANAGEMENT_REQUEST_DETAILS);
    },
    showDeclineButton(): boolean {
      return this.hasPermission(Permissions.VIEW_MANAGEMENT_REQUEST_DETAILS);
    },
  },

  methods: {},
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
