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
  <v-slide-y-transition>
    <v-banner
      v-if="continueInitialisation"
      data-test="continue-init-notification"
      class="my-4 border border-s-xl"
      variant="outlined"
      icon="check_circle"
      color="success"
      border="success"
      bg-color="background"
      rounded
    >
      <v-banner-text>
        {{ $t('init.notification') }}
      </v-banner-text>
      <v-banner-actions class="ma-0 ml-auto">
        <XrdBtn data-test="notification-button" color="primary" variant="text" text="action.continue" @click="cancelContinueInit" />
      </v-banner-actions>
    </v-banner>
  </v-slide-y-transition>
</template>

<script lang="ts" setup>
import { computed } from 'vue';

import { useNotifications, XrdBtn } from '@niis/shared-ui';

import { KEY_CONTINUE_INIT } from '@/global';

const { getFlag, clearFlag } = useNotifications();

const continueInitialisation = computed(() => getFlag(KEY_CONTINUE_INIT));

function cancelContinueInit() {
  clearFlag(KEY_CONTINUE_INIT);
}
</script>
