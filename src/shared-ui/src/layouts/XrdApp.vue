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
  <v-app>
    <slot />
    <XrdConfirmDialog v-if="appStateStore.isRestarting()" data-test="restarting-app-dialog" title="common.restartingService" persistent hide-cancel-button
                      hide-accept-button>
      <template #text>
        <div v-if="restartingMessage" class="font-weight-regular body-regular mb-4">{{ $t(restartingMessage) }}</div>
        <v-progress-linear indeterminate class="xrd" />
      </template>
    </XrdConfirmDialog>
    <XrdLogoutDialog v-else-if="!loginView && !appStateStore.isSessionAlive()" @logout="emit('logout')" />
    <XrdSnackBar />
  </v-app>
</template>

<script lang="ts" setup>
import XrdLogoutDialog from '../components/XrdLogoutDialog.vue';
import XrdSnackBar from '../components/XrdSnackBar.vue';
import { XrdConfirmDialog } from "../components";
import { computed, watch } from "vue";
import { useAppState } from "../stores";

defineProps({
  loginView: {
    type: Boolean,
    required: true,
  },
});
const emit = defineEmits(['logout']);

const appStateStore = useAppState();

const restarting = computed(() => appStateStore.restartingState);
const restartingMessage = computed(() => appStateStore.restartingMessage);

watch(restarting, (newVal, oldVal) => {
  if (newVal === 'started' && oldVal === 'restarting') {
    emit('logout');
  }
});
</script>
