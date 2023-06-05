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
  <details-view id="memberview" :back-to="savedBackTo">
    <div class="header-row">
      <div class="title-search">
        <div class="xrd-view-title">
          {{ memberStore.currentMember.member_name }}
        </div>
      </div>
    </div>
    <PageNavigation :tabs="memberNavigationTabs"></PageNavigation>
    <router-view />
  </details-view>
</template>

<script lang="ts">
import Vue from 'vue';
import PageNavigation, {
  PageNavigationTab,
} from '@/components/layout/PageNavigation.vue';
import { Colors, Permissions, RouteName } from '@/global';
import { mapStores } from 'pinia';
import { memberStore } from '@/store/modules/members';
import DetailsView from '@/components/ui/DetailsView.vue';

/**
 * Wrapper component for a member view
 */
export default Vue.extend({
  name: 'Member',
  components: { DetailsView, PageNavigation },
  props: {
    memberid: {
      type: String,
      required: true,
    },
    backTo: {
      type: String,
      required: false,
      default: '',
    },
  },
  data() {
    return {
      colors: Colors,
      savedBackTo: this.backTo,
    };
  },
  computed: {
    ...mapStores(memberStore),
    memberNavigationTabs(): PageNavigationTab[] {
      return [
        {
          key: 'member-details-tab-button',
          name: 'members.member.pagenavigation.details',
          to: {
            name: RouteName.MemberDetails,
          },
          permissions: [Permissions.VIEW_MEMBER_DETAILS],
        },

        {
          key: 'member-subsystems-tab-button',
          name: 'members.member.pagenavigation.subsystems',
          to: {
            name: RouteName.MemberSubsystems,
          },
          permissions: [Permissions.VIEW_MEMBER_DETAILS],
        },
      ];
    },
  },
  created() {
    this.memberStore.loadById(this.memberid);
  },
});
</script>
