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
    <transition-group :name="transitionName">
      <v-snackbar
        v-for="notification in successNotifications"
        :key="notification.timeAdded"
        v-model="notification.show"
        data-test="success-snackbar"
        :timeout="snackbarTimeout(notification.timeout)"
        :color="colors.Success10"
        multi-line
        class="success-snackbar"
        :min-width="760"
        @input="closeSuccess(notification.timeAdded)"
      >
        <div class="row-wrapper-top scrollable identifier-wrap">
          <xrd-icon-base :color="colors.Success100">
            <XrdIconChecker />
          </xrd-icon-base>

          <div class="row-wrapper">
            <div v-if="notification.successMessage">
              {{ notification.successMessage }}
            </div>
          </div>
          <v-btn
            icon
            :color="colors.Black100"
            data-test="close-snackbar"
            @click="closeSuccess(notification.timeAdded)"
          >
            <xrd-icon-base>
              <XrdIconClose />
            </xrd-icon-base>
          </v-btn>
        </div>
      </v-snackbar>
    </transition-group>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { Colors } from '@/global';
import { mapActions, mapState } from 'pinia';
import { notificationsStore } from '@/store/modules/notifications';
import { Notification } from '@/ui-types';

declare global {
  interface Window {
    e2eTestingMode?: boolean;
  }
}

export default Vue.extend({
  // Component for snackbar notifications
  data() {
    return {
      colors: Colors,
    };
  },
  computed: {
    ...mapState(notificationsStore, ['successNotifications']),
    // Check global window value to see if e2e testing mode should be enabled
    transitionName: () => (window.e2eTestingMode === true ? null : 'fade'),
  },
  methods: {
    ...mapActions(notificationsStore, ['deleteSuccessNotification']),
    closeSuccess(id: number): void {
      this.deleteSuccessNotification(id);
    },
    // Check global window value to see if e2e testing mode should be enabled
    snackbarTimeout(notification: Notification) {
      return window.e2eTestingMode === true ? -1 : notification.timeout;
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
}

.scrollable {
  overflow-y: auto;
  max-height: 300px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 1s;
}

.fade-enter,
.fade-leave-to {
  opacity: 0;
}
</style>
