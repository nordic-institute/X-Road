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
  <subsystem-name v-if="isSubsystem" class="client-name" :name="displayName" />
  <span v-else class="client-name non-subsystem-name">{{ displayName }}</span>
</template>

<script lang="ts" setup>
import { computed, PropType } from 'vue';
import SubsystemName from '@/components/client/SubsystemName.vue';
import { Client, ServiceClient, ServiceClientType } from '@/openapi-types';

const props = defineProps({
  name: {
    type: String,
    default: undefined,
  },
  subsystem: { type: Boolean, default: false },
  serviceClient: {
    type: Object as PropType<ServiceClient>,
    default: undefined,
  },
  client: {
    type: Object as PropType<Client>,
    default: undefined,
  },
});

const withValue = [props.name, props.serviceClient, props.client].filter(
  (prop) => prop,
).length;

if (withValue > 1) {
  throw new Error(
    'Multiple sources for client name are provided. Only one of them should be provided.',
  );
}

const isSubsystem = computed(() => {
  if (props.serviceClient) {
    return (
      props.serviceClient.service_client_type === ServiceClientType.SUBSYSTEM
    );
  }
  if (props.client) {
    return props.client.subsystem_code;
  }
  return props.subsystem;
});

const displayName = computed(() => {
  if (props.serviceClient) {
    return props.serviceClient.name;
  }
  if (props.client) {
    return isSubsystem.value
      ? (props.client.subsystem_name ?? props.client.subsystem_code)
      : props.client.member_name;
  }
  return props.name;
});
</script>
<style lang="scss" scoped></style>
