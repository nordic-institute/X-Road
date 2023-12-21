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
  <tr data-test="system-parameters-timestamping-service-row">
    <td :class="{ disabled: !messageLogEnabled }">
      {{ timestampingService.name }}
    </td>
    <td :class="{ disabled: !messageLogEnabled }">
      {{ timestampingService.url }}
    </td>
    <td class="pr-4">
      <xrd-button
        v-if="showDeleteTsp"
        data-test="system-parameters-timestamping-service-delete-button"
        :outlined="false"
        text
        @click="confirmDeleteDialog = true"
      >
        {{
          $t('systemParameters.timestampingServices.table.action.delete.button')
        }}
      </xrd-button>
      <xrd-confirm-dialog
        v-if="confirmDeleteDialog"
        data-test="system-parameters-timestamping-service-delete-confirm-dialog"
        :loading="deleting"
        title="systemParameters.timestampingServices.table.action.delete.confirmation.title"
        text="systemParameters.timestampingServices.table.action.delete.confirmation.text"
        @cancel="confirmDeleteDialog = false"
        @accept="deleteTimestampingService"
      />
    </td>
  </tr>
</template>

<script lang="ts">
import { defineComponent, PropType } from 'vue';
import { TimestampingService } from '@/openapi-types';
import { Permissions } from '@/global';
import * as api from '@/util/api';
import { mapActions, mapState } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';
import { useUser } from '@/store/modules/user';

export default defineComponent({
  name: 'TimestampingServiceRow',
  props: {
    timestampingService: {
      type: Object as PropType<TimestampingService>,
      required: true,
    },
    messageLogEnabled: Boolean,
  },
  emits: ['deleted'],
  data() {
    return {
      confirmDeleteDialog: false,
      deleting: false,
      permissions: Permissions,
    };
  },
  computed: {
    ...mapState(useUser, ['hasPermission']),
    showDeleteTsp(): boolean {
      return (
        this.hasPermission(Permissions.DELETE_TSP) && this.messageLogEnabled
      );
    },
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),

    deleteTimestampingService(): void {
      this.deleting = true;
      api
        .post('/system/timestamping-services/delete', this.timestampingService)
        .then(() => {
          this.deleting = false;
          this.confirmDeleteDialog = false;
          this.$emit('deleted');
          this.showSuccess(
            this.$t(
              'systemParameters.timestampingServices.table.action.delete.success',
            ),
          );
        })
        .catch((error) => this.showError(error));
    },
  },
});
</script>

<style lang="scss" scoped>
@import '@/assets/colors';
@import '@/assets/tables';

.disabled {
  color: $XRoad-WarmGrey100;
}

tr td {
  color: $XRoad-Black100;
  font-weight: normal !important;
}

tr td:last-child {
  width: 1%;
  white-space: nowrap;
}
</style>
