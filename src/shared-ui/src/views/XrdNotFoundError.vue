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
  <XrdErrorPage data-test="404-view" title="404.topTitle">
    <v-container class="mx-auto" max-width="800">
      <v-row class="xrd-error-view" no-gutters align="center">
        <!-- Fixed width column -->
        <v-col class="xrd-error-info">
          <div class="xrd-error-info-text">
            <p class="title-page font-weight-bold mb-8">{{ $t('404.title') }}</p>
            <v-img class="xrd-404-cover-image" width="336px" height="124px" :src="image404Url" />
          </div>
        </v-col>

        <!-- Flexible column that takes remaining space -->
        <v-col class="xrd-error-info">
          <div class="xrd-error-info-text">
            <p class="title-container font-weight-medium mb-4">{{ $t('404.text1') }}</p>
            <p class="body-large font-weight-regular mb-12">{{ $t('404.text2') }}</p>
            <v-btn
              data-test="error-404-button"
              class="xrd go-home bg-special"
              variant="flat"
              rounded="pill"
              size="large"
              width="360"
              @click="emit('go-home')"
            >
              {{ $t('404.returnToFront') }}
            </v-btn>
          </div>
        </v-col>
      </v-row>
    </v-container>
  </XrdErrorPage>
</template>

<script lang="ts" setup>
import { ref, useTemplateRef, watchEffect } from 'vue';

import { VContainer } from 'vuetify/components';
import { useDisplay } from 'vuetify/framework';

import image404 from '../assets/404.svg';

import XrdErrorPage from './XrdErrorPage.vue';

const image404Url = image404;

const emit = defineEmits(['go-home']);

const errorContainer = useTemplateRef<VContainer>('errorContainer');
const display = useDisplay();

const COVER_WIDTH = 480;

const coverWidth = ref<number | undefined>(COVER_WIDTH);
const coverCols = ref<string | undefined>('auto');

watchEffect(() => {
  const width = display.width.value;
  if (errorContainer.value && errorContainer.value.$el.offsetWidth) {
    coverWidth.value = errorContainer.value.$el.offsetWidth / 2 > COVER_WIDTH ? COVER_WIDTH : undefined;
    coverCols.value = errorContainer.value.$el.offsetWidth / 2 > COVER_WIDTH ? 'auto' : undefined;
  }
});
</script>

<style lang="scss" scoped>
.xrd-error-view {
  min-height: 508px;
}

.xrd-error-info {
  display: flex;
  justify-content: center;
  align-items: center;
}

.xrd-error-info-text {
  text-align: center;
}
</style>
