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
