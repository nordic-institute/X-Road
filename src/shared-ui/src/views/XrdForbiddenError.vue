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
  <div class="xrd-view-common" data-test="forbidden-view">
    <slot name="top" />
    <v-container>
      <div class="xrd-view-title pt-6">{{ $t('403.topTitle') }}</div>
      <v-card flat class="xrd-card custom-card">
        <v-card-text>
          <div class="content-wrap">
            <div class="main-title">{{ $t('403.mainTitle') }}</div>
            <div class="permission-text">
              {{ $t('403.text') }}
            </div>
            <div class="buttons-wrap my-13">
              <xrd-button test-data="go-back-button" color="primary" large rounded @click="routing?.goBack(-2)">
                {{ $t('403.goBack') }}
              </xrd-button>

              <xrd-button
                test-data="go-to-front-page-button"
                color="primary"
                outlined
                large
                class="ml-4"
                rounded
                @click="routing?.toHome()"
              >
                {{ $t('action.goToFront') }}
              </xrd-button>
            </div>
          </div>
        </v-card-text>
      </v-card>
    </v-container>
  </div>
</template>

<script lang="ts" setup>
import { key, XrdButton } from '@niis/shared-ui';
import { inject } from 'vue';

const routing = inject(key.routing);
</script>

<style lang="scss" scoped>
@use '../assets/colors';

.xrd-view-common {
  width: 100%;
}

.permission-text {
  width: 620px;
  text-align: center;
}

.main-title {
  font-family: 'Open Sans', sans-serif;
  font-style: normal;
  font-weight: bold;
  font-size: 40px;
  line-height: 56px;
  color: colors.$Black100;
  margin-top: 50px;
  margin-bottom: 60px;
}

.custom-card {
  width: 100%;
  margin-top: 40px;
  margin-bottom: 30px;
}

.content-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.buttons-wrap {
  display: row;
  flex-direction: column;
  align-items: center;
}
</style>
