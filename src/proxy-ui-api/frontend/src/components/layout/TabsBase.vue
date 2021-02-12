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
  <v-layout class="main-content" align-left>
    <v-img
      :src="require('../../assets/xroad7_logo.svg')"
      height="35"
      width="132"
      max-height="35"
      max-width="132"
      class="xrd-logo"
      @click="home()"
    ></v-img>
    <div class="tabs-wrap">
      <v-tabs
        v-model="tab"
        class="main-tabs"
        color="black"
        height="56px"
        slider-size="2"
        slider-color="primary"
        :show-arrows="true"
      >
        <v-tabs-slider
          color="primary"
          class="xrd-main-tabs-slider"
        ></v-tabs-slider>
        <v-tab v-for="tab in allowedTabs" v-bind:key="tab.key" :to="tab.to">{{
          $t(tab.name)
        }}</v-tab>
      </v-tabs>
    </div>

    <div class="drop-menu">
      <v-menu bottom right>
        <template v-slot:activator="{ on }">
          <v-btn text v-on="on" class="no-uppercase">
            {{ username }}
            <v-icon>mdi-chevron-down</v-icon>
          </v-btn>
        </template>

        <v-list>
          <v-list-item id="logout-list-tile" @click="logout">
            <v-list-item-title id="logout-title">{{
              $t('login.logOut')
            }}</v-list-item-title>
          </v-list-item>
        </v-list>
      </v-menu>
    </div>
  </v-layout>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';
import { Tab } from '@/ui-types';
import { mainTabs, RouteName } from '@/global';

export default Vue.extend({
  data() {
    return {
      tab: undefined as undefined | Tab,
    };
  },
  computed: {
    ...mapGetters(['username']),

    allowedTabs(): Tab[] {
      return this.$store.getters.getAllowedTabs(mainTabs);
    },
  },
  methods: {
    home(): void {
      this.$router
        .replace({
          name: this.$store.getters.firstAllowedTab.to.name,
        })
        .catch((err) => {
          // Ignore the error regarding navigating to the same path
          if (err.name === 'NavigationDuplicated') {
            // eslint-disable-next-line no-console
            console.log('Duplicate navigation');
          } else {
            // Throw for any other errors
            throw err;
          }
        });
    },
    logout(): void {
      this.$store.dispatch('logout');
      this.$router.replace({ name: RouteName.Login });
    },
  },
});
</script>

<style lang="scss">
@import '../../assets/colors';

.v-tabs-slider.xrd-main-tabs-slider {
  width: 70px;
  margin-left: auto;
  margin-right: auto;
}

.v-tab {
  text-transform: none;
  font-weight: 600;
}

.v-tabs-slider.xrd-sub-tabs-slider {
  width: 40px;
  margin-left: auto;
  margin-right: auto;
}
</style>

<style lang="scss" scoped>
.main-content {
  background-color: #ffffff;
  height: 56px;
  padding-left: 92px;
  @media only screen and (max-width: 920px) {
    padding-left: 0px;
  }
}

.xrd-logo {
  margin-top: auto;
  margin-bottom: auto;
  cursor: pointer;
  @media only screen and (max-width: 920px) {
    display: none;
  }
}

.tabs-wrap {
  margin-left: 20px;
}

.main-tabs {
  max-width: 1000px;
}

.drop-menu {
  margin-left: auto;
  margin-right: 70px;
  display: flex;
  align-items: center;
}

.no-uppercase {
  text-transform: none;
  font-weight: 600;
}
</style>
