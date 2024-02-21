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
  <!-- Success -->
  <v-snackbar
    v-for="notification in successNotifications"
    :key="notification.timeAdded"
    v-model="notification.show"
    data-test="success-snackbar"
    :timeout="snackbarTimeout(notification)"
    :color="colors.Success10"
    :transition="transitionName"
    multi-line
    class="success-snackbar"
    :min-width="760"
    :close-on-back="false"
    @update:model-value="closeSuccess(notification.timeAdded)"
  >
    <div class="row-wrapper-top scrollable identifier-wrap">
      <xrd-icon-base :color="colors.Success100">
        <xrd-icon-checker />
      </xrd-icon-base>
      <div class="row-wrapper">
        <div v-if="notification.successMessage">
          {{ notification.successMessage }}
        </div>
      </div>
    </div>
    <template #actions>
      <v-btn
        icon
        variant="text"
        rounded
        :color="colors.Black100"
        data-test="close-snackbar"
        @click="closeSuccess(notification.timeAdded)"
      >
        <xrd-icon-base>
          <xrd-icon-close />
        </xrd-icon-base>
      </v-btn>
    </template>
  </v-snackbar>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { Colors } from '@/global';
import { Notification } from '@/ui-types';

import { mapActions, mapWritableState } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';

declare global {
  interface Window {
    e2eTestingMode?: boolean;
  }
}

export default defineComponent({
  // Component for snackbar notifications
  data() {
    return {
      colors: Colors,
    };
  },
  computed: {
    ...mapWritableState(useNotifications, ['successNotifications']),
    transitionName(): string {
      // Check global window value to see if e2e testing mode should be enabled
      if (window.e2eTestingMode === true) {
        return 'no-transition'; // Transition class name that doesn't exist
      }
      return 'fade-transition'; // Proper transition class name
    },
  },
  methods: {
    ...mapActions(useNotifications, ['deleteSuccessNotification']),
    closeSuccess(timeAdded: number): void {
      this.deleteSuccessNotification(timeAdded);
    },
    snackbarTimeout(notification: Notification): number {
      // Check global window value to see if e2e testing mode should be enabled
      if (window.e2eTestingMode === true) {
        return -1;
      }
      return notification.timeout;
    },
  },
});
</script>

<style lang="scss">
.success-snackbar > .v-snack__wrapper {
  // Customised size for snackbar
  min-height: 88px;
  min-width: 760px;
}
</style>

<style lang="scss" scoped>
.row-wrapper-top {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: center;
  padding-left: 14px;
}

.row-wrapper {
  display: flex;
  flex-direction: column;
  overflow: auto;
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

.scrollable {
  overflow-y: auto;
  max-height: 300px;
}
</style>
