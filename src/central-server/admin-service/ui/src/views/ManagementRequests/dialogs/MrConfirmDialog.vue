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
  <XrdConfirmDialog
    title="managementRequests.dialog.approve.title"
    focus-on-accept
    :data="messageData"
    :loading="loading"
    @cancel="$emit('cancel')"
    @accept="approve()"
  >
    <template #text>
      <span class="font-weight-regular body-regular">
        {{ $t('managementRequests.dialog.approve.bodyMessage', messageData) }}
      </span>
      <v-alert
        v-if="newMember"
        data-test="new-member-warning"
        class="mt-2 font-weight-regular body-regular"
        color="warning"
        icon="warning"
        density="compact"
        variant="outlined"
        :text="$t('managementRequests.dialog.approve.newMemberWarning')"
      />
    </template>
  </XrdConfirmDialog>
</template>

<script lang="ts" setup>
import { ref, computed } from 'vue';
import { useManagementRequests } from '@/store/modules/management-requests';
import { useNotifications, XrdConfirmDialog } from '@niis/shared-ui';

/**
 * General component for Management request actions
 */

const props = defineProps({
  requestId: {
    type: Number,
    required: true,
  },
  securityServerId: {
    type: String,
    required: true,
  },
  newMember: {
    type: Boolean,
    default: false,
  },
});
const emits = defineEmits(['approve', 'cancel']);

const { approve: approveManagementRequest } = useManagementRequests();
const { addSuccessMessage, addError } = useNotifications();

const messageData = computed(() => ({
  id: props.requestId,
  serverId: props.securityServerId,
}));

const loading = ref(false);

function approve() {
  loading.value = true;
  approveManagementRequest(props.requestId)
    .then(() => {
      addSuccessMessage(
        'managementRequests.dialog.approve.successMessage',
        messageData.value,
      );
      emits('approve');
    })
    .catch((error) => addError(error))
    .finally(() => (loading.value = false));
}
</script>
