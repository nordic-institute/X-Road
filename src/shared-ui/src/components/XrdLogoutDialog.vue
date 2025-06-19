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
  <v-dialog class="" :model-value="showDialog" width="500" persistent>
    <v-card class="pa-0" color="surfaceContainerLowest">
      <v-card-title class="pa-6">
        <span>{{ $t('logout.sessionExpired') }}</span>
      </v-card-title>
      <v-card-text class="pt-0 ps-6 pb-2">
        {{ $t('logout.idleWarning') }}
      </v-card-text>
      <v-card-actions class="pa-4">
        <xrd-button color="secondary" data-test="session-expired-ok-button" autofocus @click="logoutApp">
          {{ $t('action.ok') }}
        </xrd-button>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script lang="ts" setup>

import { inject, computed } from 'vue';
import { key } from '../utils';

const user = inject(key.user);
const routing = inject(key.routing);
const showDialog = computed(() => user?.isSessionAlive() === false);

function logoutApp(): void {
  user?.logout();
  routing?.toLogin();
}
</script>

<style lang="scss" scoped></style>
