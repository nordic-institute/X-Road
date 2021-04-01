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
  <div id="memberview">
    <div class="navigation-back" data-test="navigation-back">
      <router-link to="/members">
        <v-icon :color="colors.Purple100">mdi-chevron-left</v-icon>
        {{ $t('global.navigation.back') }}
      </router-link>
    </div>
    <div class="header-row">
      <div class="title-search">
        <div class="xrd-view-title">NETUM</div>
      </div>
      <xrd-button data-test="remove-member-button"
        ><v-icon class="xrd-large-button-icon">mdi-close-circle</v-icon>
        {{ $t('members.deleteMember') }}</xrd-button
      >
    </div>
    <PageNavigation :items="memberNavigationItems"></PageNavigation>
    <router-view />
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import PageNavigation, {
  NavigationItem,
} from '@/components/layout/PageNavigation';
import { Colors } from '@/global';

/**
 * Wrapper component for a member view
 */
export default Vue.extend({
  name: 'Member',
  components: { PageNavigation },
  props: {
    memberid: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      colors: Colors,
    };
  },
  computed: {
    memberNavigationItems(): NavigationItem[] {
      return [
        {
          url: `/members/${this.memberid}/details`,
          label: this.$t('members.member.pagenavigation.details'),
        },
        {
          url: `/members/${this.memberid}/managementrequests`,
          label: this.$t('members.member.pagenavigation.managementRequests'),
        },
        {
          url: `/members/${this.memberid}/subsystems`,
          label: this.$t('members.member.pagenavigation.subsystems'),
        },
      ];
    },
  },
});
</script>

<style scoped lang="scss">
@import '../../../assets/colors';

.navigation-back {
  color: $XRoad-Link;
  cursor: pointer;
  margin-bottom: 20px;

  a {
    text-decoration: none;
  }
}
</style>
