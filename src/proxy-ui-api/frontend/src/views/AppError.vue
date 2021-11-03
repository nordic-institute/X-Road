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
  <div class="xrd-view-common" data-test="404-view">
    <TabsBase />
    <AlertsContainer />
    <v-container>
      <div class="xrd-view-title pt-6">{{ $t('404') }}</div>
      <v-card flat class="xrd-card custom-card">
        <v-card-text>
          <div class="content-wrap">
            <v-img
              :src="require('../assets/404.png')"
              width="100%"
              height="auto"
              max-height="259"
              max-width="665"
            ></v-img>

            <div class="unicorn-text">
              {{ $t('404text') }}
              <span class="unicorn-gradient">{{ $t('404textUnicorn') }}</span>
            </div>
            <xrd-button
              test-data="error-404-button"
              color="primary"
              gradient
              large
              :min_width="401"
              :width="401"
              class="button"
              rounded
              @click="home"
              >{{ $t('action.goToFront') }}</xrd-button
            >
          </div>
        </v-card-text>
      </v-card>
    </v-container>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import TabsBase from '@/components/layout/TabsBase.vue';
import AlertsContainer from '@/components/ui/AlertsContainer.vue';

export default Vue.extend({
  components: {
    TabsBase,
    AlertsContainer,
  },
  methods: {
    home(): void {
      this.$router.replace({
        name: this.$store.getters.firstAllowedTab.to.name,
      });
    },
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/shared';
@import '~styles/colors';

.xrd-view-common {
  width: 100%;
}

.unicorn-text {
  text-transform: uppercase;
  color: $XRoad-Purple100;
  width: 450px;
  font-style: normal;
  font-weight: 800;
  font-size: 34.6667px;
  line-height: 150%;
  text-align: center;
  letter-spacing: 0.02em;
  text-transform: uppercase;
  margin-top: 60px;
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

.button {
  width: 200px;
  margin: 70px;
}

.unicorn-gradient {
  background-color: #f3ec78;
  background-image: linear-gradient(108deg, #e29ae6 5.28%, #663bdb 94.72%);
  background-size: 100%;
  background-clip: text;
  -webkit-background-clip: text;
  -moz-background-clip: text;
  -webkit-text-fill-color: transparent;
  -moz-text-fill-color: transparent;
}
</style>
