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
  <XrdCard :title="clientInfoTitle" :loading>
    <XrdCardTable>
      <XrdCardTableRow label="managementRequestDetails.ownerName" :value="managementRequest?.client_owner_name" />
      <XrdCardTableRow label="managementRequestDetails.ownerClass" :value="managementRequest?.client_id?.member_class" />
      <XrdCardTableRow label="managementRequestDetails.ownerCode" :value="managementRequest?.client_id?.member_code" />
      <XrdCardTableRow
        v-if="!isOwnerChange"
        label="managementRequestDetails.subsystemCode"
        :value="managementRequest?.client_id?.subsystem_code"
      />
      <XrdCardTableRow
        v-if="!isOwnerChange"
        label="managementRequestDetails.subsystemName"
        :value="managementRequest?.client_subsystem_name"
      />
    </XrdCardTable>
  </XrdCard>
</template>

<script lang="ts" setup>
import { PropType, computed } from 'vue';
import { ManagementRequestDetailedView, ManagementRequestType } from '@/openapi-types';
import { XrdCard, XrdCardTable, XrdCardTableRow } from '@niis/shared-ui';

const props = defineProps({
  managementRequest: {
    type: Object as PropType<ManagementRequestDetailedView>,
    default: undefined,
  },
  loading: {
    type: Boolean,
    default: false,
  },
});

const isOwnerChange = computed(() => props.managementRequest?.type === ManagementRequestType.OWNER_CHANGE_REQUEST);

const clientInfoTitle = computed(() => {
  if (isOwnerChange.value) {
    return 'managementRequestDetails.ownerChangeInformation';
  } else if (props.managementRequest?.type === ManagementRequestType.CLIENT_DISABLE_REQUEST) {
    return 'managementRequestDetails.clientDisableInformation';
  } else if (props.managementRequest?.type === ManagementRequestType.CLIENT_ENABLE_REQUEST) {
    return 'managementRequestDetails.clientEnableInformation';
  } else if (props.managementRequest?.type === ManagementRequestType.CLIENT_RENAME_REQUEST) {
    return 'managementRequestDetails.clientRenameInformation';
  }
  return 'managementRequestDetails.clientInformation';
});
</script>
