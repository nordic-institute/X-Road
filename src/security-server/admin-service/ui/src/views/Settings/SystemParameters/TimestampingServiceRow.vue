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
    <td :class="{ 'opacity-60': !messageLogEnabled }">
      {{ timestampingService.name }}
    </td>
    <td :class="{ 'opacity-60': !messageLogEnabled }">
      {{ timestampingService.url }}
    </td>
    <td class="text-end">
      <XrdBtn
        v-if="showDeleteTsp"
        data-test="system-parameters-timestamping-service-delete-button"
        variant="text"
        color="tertiary"
        text="systemParameters.timestampingServices.table.action.delete.button"
        @click="confirmDeleteDialog = true"
      />
      <XrdConfirmDialog
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
import { mapState } from 'pinia';
import { useUser } from '@/store/modules/user';
import { XrdBtn, useNotifications, XrdConfirmDialog } from '@niis/shared-ui';

export default defineComponent({
  components: { XrdBtn, XrdConfirmDialog },
  props: {
    timestampingService: {
      type: Object as PropType<TimestampingService>,
      required: true,
    },
    messageLogEnabled: Boolean,
  },
  emits: ['deleted'],
  setup() {
    const { addError, addSuccessMessage } = useNotifications();
    return { addError, addSuccessMessage };
  },
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
      return this.hasPermission(Permissions.DELETE_TSP) && this.messageLogEnabled;
    },
  },
  methods: {
    deleteTimestampingService(): void {
      this.deleting = true;
      api
        .post('/system/timestamping-services/delete', this.timestampingService)
        .then(() => {
          this.deleting = false;
          this.confirmDeleteDialog = false;
          this.$emit('deleted');
          this.addSuccessMessage('systemParameters.timestampingServices.table.action.delete.success');
        })
        .catch((error) => this.addError(error));
    },
  },
});
</script>

<style lang="scss" scoped></style>
