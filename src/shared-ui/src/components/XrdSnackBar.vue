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
  <div>
    <!-- Success -->
    <v-snackbar
      v-for="notification in successNotifications"
      :key="notification.timeAdded"
      v-model="notification.show"
      :transition="transitionName"
      :timeout="snackbarTimeout(notification.timeout)"
      :color="Colors.Success10"
      :min-width="760"
      :close-on-back="false"
      data-test="success-snackbar"
      class="success-snackbar"
      multi-line
      @update:model-value="emit('close', notification)"
    >
      <div class="row-wrapper-top scrollable identifier-wrap">
        <xrd-icon-base :color="Colors.Success100">
          <xrd-icon-checker />
        </xrd-icon-base>

        <div v-if="notification.successMessage" class="row-wrapper">
          {{ notification.successMessage }}
        </div>
      </div>
      <template #actions>
        <v-btn icon variant="text" rounded :color="Colors.Black100" data-test="close-snackbar" @click="emit('close', notification)">
          <xrd-icon-base>
            <xrd-icon-close />
          </xrd-icon-base>
        </v-btn>
      </template>
    </v-snackbar>
  </div>
</template>

<script lang="ts" setup>
import { computed, PropType } from 'vue';
import { Colors } from '../utils';

declare global {
  interface Window {
    e2eTestingMode?: boolean;
  }
}

type Notification = {
  timeAdded: number;
  show: boolean;
  timeout: number;
  successMessage?: string;
};

defineProps({
  successNotifications: {
    type: Array as PropType<Notification[]>,
    required: true,
  },
});

const emit = defineEmits<{ (e: 'close', value: Notification): void }>();

const transitionName = computed(() => (window.e2eTestingMode === true ? 'no-transition' : 'fade-transition'));

// Check global window value to see if e2e testing mode should be enabled
function snackbarTimeout(timeout: number) {
  return window.e2eTestingMode === true ? -1 : timeout;
}
</script>

<style lang="scss" scoped>
.row-wrapper-top {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: center;
  padding-left: 14px;

  .row-wrapper {
    display: flex;
    flex-direction: column;
    width: 100%;
    overflow-wrap: break-word;
    justify-content: flex-start;
    margin-right: 30px;
    margin-left: 26px;
    color: #211e1e;
    font-style: normal;
    font-weight: bold;
    font-size: 18px;
    line-height: 24px;
  }
}

.scrollable {
  overflow-y: auto;
  max-height: 300px;
}
</style>
