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
  <v-container fluid class="notification-container">
    <div
      v-if="showNotification"
      class="init-notification"
      data-test="continue-init-notification"
    >
      <div class="left-block">
        <xrd-icon-base class="xrd-large-button-icon notification-icon"
          ><xrd-icon-checked
        /></xrd-icon-base>

        <span class="alert-text">{{ $t('init.notification') }}</span>
      </div>

      <xrd-button
        color="primary"
        outlined
        data-test="notification-button"
        @click="setContinueInit(false)"
      >
        {{ $t('action.continue') }}
      </xrd-button>
    </div>
  </v-container>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapActions, mapState } from 'pinia';
import { notificationsStore } from '@/store/modules/notifications';

export default Vue.extend({
  name: 'InitNotification',
  computed: {
    ...mapState(notificationsStore, {
      showNotification: 'continueInitialisation',
    }),
  },
  methods: {
    ...mapActions(notificationsStore, ['setContinueInit']),
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/colors';

.notification-icon {
  color: $XRoad-Success100;
  margin-right: 25px;
}

.notification-container {
  width: 100%;
  padding: 0;

  .alert-text {
    color: $XRoad-Black100;
    display: block;
  }

  .init-notification {
    background-color: $XRoad-Success10;
    height: 80px;
    display: flex;
    flex-direction: row;
    justify-content: space-between;
    align-items: center;
    padding-left: 80px;
    padding-right: 40px;
    font-style: normal;
    font-weight: bold;
    font-size: 18px;

    .left-block {
      display: flex;
      flex-direction: row;
      justify-content: flex-start;
      align-items: center;
    }
  }
}
</style>
