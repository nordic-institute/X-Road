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
  <v-footer class="footer pt-6 pb-6" height="auto">
    <v-row>
      <v-col class="pt-4 text-start">
        <img :src="xroad8LogoUrl" height="40" width="150" alt="Logo" />
      </v-col>
      <v-col class="footer-col pt-5">
        <v-row>
          <v-col>
            <span class="body-regular font-weight-bold">
              {{ $t('footer.software.title') }}
            </span>
          </v-col>
        </v-row>
        <v-row>
          <v-col class="py-0">
            <span data-test="app-footer-server-version" class="body-regular font-weight-regular">
              {{ $t('footer.software.versionPrefix') }}&nbsp;{{ appVersion }}
            </span>
          </v-col>
        </v-row>
        <v-row>
          <v-col>
            <a
              rel="noopener"
              class="body-regular font-weight-medium text-accent text-decoration-none"
              target="_blank"
              href="https://x-road.global/feedback"
            >
              {{ $t('footer.software.feedback') }}
            </a>
          </v-col>
        </v-row>
      </v-col>
      <v-col class="footer-col pt-5">
        <v-row>
          <v-col>
            <span class="body-regular font-weight-bold">
              {{ $t('footer.copyright.title') }}
            </span>
          </v-col>
        </v-row>
        <v-row>
          <v-col class="py-0">
            <span data-test="app-footer-server-version" class="body-regular font-weight-regular">
              {{ $t('footer.copyright.company') }}
            </span>
          </v-col>
        </v-row>
        <v-row>
          <v-col>
            <a
              rel="noopener"
              class="body-regular font-weight-medium text-accent text-decoration-none"
              href="https://niis.org/"
              target="_blank"
            >
              {{ $t('footer.copyright.domain') }}
            </a>
          </v-col>
        </v-row>
      </v-col>
    </v-row>
  </v-footer>
</template>

<script lang="ts" setup>
import xroad8Logo from '../assets/Logo-horizontal-light.svg';

defineProps({
  appVersion: {
    type: String,
    required: true,
  },
});

const xroad8LogoUrl = xroad8Logo;
</script>

<style lang="scss" scoped></style>
