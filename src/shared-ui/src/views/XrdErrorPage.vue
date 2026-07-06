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
  <XrdView :title="title">
    <div class="position-relative">
      <v-img class="xrd-image-trail2" width="480px" height="480px" :src="imageTrail2Url" />
      <v-img class="xrd-image-rocket-trail" width="560" height="480px" :src="imageRocketTrailUrl" />

      <v-container fluid class="xrd-error-view pa-0 bg-surface-bright rounded-xl border">
        <v-img class="xrd-image-trail1" width="480px" height="480px" :src="imageTrail1Url" />
        <v-container fluid class="xrd-error-view-inner overflow-hidden position-relative">
          <slot />
        </v-container>
      </v-container>
    </div>
  </XrdView>
</template>

<script lang="ts" setup>
import { VContainer } from 'vuetify/components';

import imageRocketTrail from '../assets/Rocket-trail.png';
import imageTrail1 from '../assets/Trail-1.png';
import imageTrail2 from '../assets/Trail-2.png';
import { XrdView } from '../layouts';

defineProps({
  title: {
    type: String,
    required: true,
  },
});

const imageTrail1Url = imageTrail1;
const imageTrail2Url = imageTrail2;
const imageRocketTrailUrl = imageRocketTrail;
</script>

<style lang="scss" scoped>
.xrd-error-view {
  position: relative;
  z-index: 20;
}

.xrd-error-view-inner {
  position: relative;
  padding: 20px 80px;
  z-index: 30;
}

.xrd-image-trail1 {
  position: absolute;
  left: -80px;
  bottom: -160px;
  z-index: 15;
}

.xrd-image-trail2 {
  position: absolute;
  right: -180px;
  bottom: -220px;
  transform: scaleY(-1) rotate(-120deg);
  z-index: 5;
}

.xrd-image-rocket-trail {
  position: absolute;
  right: -350px;
  top: -240px;
  transform: rotate(-150deg) scaleY(-1);
  z-index: 25;
}
</style>
