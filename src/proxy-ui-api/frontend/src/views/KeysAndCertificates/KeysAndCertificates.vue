<template>
  <div class="xrd-view-common">
    <v-tabs
      v-model="tab"
      class="xrd-tabs"
      color="secondary"
      grow
      slider-size="4"
    >
      <v-tabs-slider color="secondary"></v-tabs-slider>
      <v-tab v-for="tab in tabs" v-bind:key="tab.key" :to="tab.to" exact>
        {{ $t(tab.name) }}
        <v-hover v-slot:default="{ hover }">
          <v-icon
            :color="hover ? '#6eecff' : getIconColor(tab.to.name)"
            dark
            class="help-icon"
            @click="helpClick(tab)"
            >mdi-help-circle</v-icon
          >
        </v-hover>
      </v-tab>
    </v-tabs>
    <div class="content">
      <router-view />
    </div>
    <helpDialog
      v-if="helpTab"
      :dialog="showHelp"
      @cancel="closeHelp"
      :imageSrc="helpTab.helpImage"
      :title="helpTab.helpTitle"
      :text="helpTab.helpText"
    />
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { Permissions, RouteName } from '@/global';
import { Tab } from '@/ui-types';
import HelpDialog from '@/components/ui/HelpDialog.vue';

interface KeysTab extends Tab {
  helpImage: string;
  helpTitle: string;
  helpText: string;
}

export default Vue.extend({
  components: {
    HelpDialog,
  },
  data: () => ({
    tab: null,
    showHelp: false,
    helpTab: null as KeysTab | null,
  }),

  computed: {
    tabs(): KeysTab[] {
      const allTabs: KeysTab[] = [
        {
          key: 'signAndAuthKeys',
          name: 'tab.keys.signAndAuthKeys',
          to: {
            name: RouteName.SignAndAuthKeys,
          },
          helpImage: 'keys_and_certificates.png',
          helpTitle: 'keys.helpTitleKeys',
          helpText: 'keys.helpTextKeys',
        },
        {
          key: 'apiKey',
          name: 'tab.keys.apiKey',
          to: {
            name: RouteName.ApiKey,
          },
          permission: Permissions.VIEW_CLIENT_ACL_SUBJECTS,
          helpImage: 'api_keys.png',
          helpTitle: 'keys.helpTitleApi',
          helpText: 'keys.helpTextApi',
        },
        {
          key: 'ssTlsCertificate',
          name: 'tab.keys.ssTlsCertificate',
          to: {
            name: RouteName.SSTlsCertificate,
          },
          permission: Permissions.VIEW_CLIENT_SERVICES,
          helpImage: 'tls_certificate.png',
          helpTitle: 'keys.helpTitleSS',
          helpText: 'keys.helpTextSS',
        },
      ];

      return this.$store.getters.getAllowedTabs(allTabs);
    },
  },

  methods: {
    helpClick(tab: KeysTab): void {
      this.helpTab = tab;
      this.showHelp = true;
    },
    closeHelp(): void {
      this.showHelp = false;
    },
    getIconColor(specTab: string): string {
      if (this.$route.name === specTab) {
        return 'secondary';
      }
      return '#9c9c9c';
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/global-style';

.help-icon {
  margin-left: 20px;
  font-size: 16px;
}

.content {
  width: 1000px;
}
</style>
