<template>
  <div class="xrd-tab-max-width xrd-view-common">
    <v-flex mb-4 class="title-action">
      <h1 v-if="client" class="display-1 mb-3">{{client.member_name}} ({{ $t("client.owner") }})</h1>

      <div>
        <LargeButton
          v-if="showUnregister"
          @click="confirmUnregisterClient = true"
        >{{$t('action.unregister')}}</LargeButton>
      </div>
    </v-flex>
    <v-tabs v-model="tab" class="xrd-tabs" color="secondary" grow slider-size="4">
      <v-tabs-slider color="secondary"></v-tabs-slider>
      <v-tab v-for="tab in tabs" v-bind:key="tab.key" :to="tab.to">{{ $t(tab.name) }}</v-tab>
    </v-tabs>

    <router-view />

    <!-- Confirm dialog for unregister client -->
    <ConfirmDialog
      :dialog="confirmUnregisterClient"
      :loading="unregisterLoading"
      title="client.action.unregister.confirmTitle"
      text="client.action.unregister.confirmText"
      @cancel="confirmUnregisterClient = false"
      @accept="unregisterClient()"
    />
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';
import { Permissions, RouteName } from '@/global';
import { Tab } from '@/ui-types';
import LargeButton from '@/components/ui/LargeButton.vue';
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue';

export default Vue.extend({
  components: {
    LargeButton,
    ConfirmDialog,
  },
  props: {
    id: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      tab: null,
      confirmUnregisterClient: false,
      unregisterLoading: false,
    };
  },
  computed: {
    ...mapGetters(['client']),
    showUnregister(): boolean {
      return this.$store.getters.hasPermission(Permissions.SEND_CLIENT_DEL_REQ);
    },
    tabs(): Tab[] {
      const allTabs = [
        {
          key: 'details',
          name: 'tab.client.details',
          to: {
            name: RouteName.MemberDetails,
            params: { id: this.id },
          },
        },
        {
          key: 'internalServers',
          name: 'tab.client.internalServers',
          to: {
            name: RouteName.MemberServers,
            params: { id: this.id },
          },
          permission: Permissions.VIEW_CLIENT_INTERNAL_CERTS,
        },
      ];

      return this.$store.getters.getAllowedTabs(allTabs);
    },
    localGroupsRoute(): object {
      return {
        name: RouteName.SubsystemLocalGroups,
        params: { id: this.id },
      };
    },
  },
  created() {
    this.fetchClient(this.id);
  },
  methods: {
    fetchClient(id: string): void {
      this.$store.dispatch('fetchClient', id).catch((error) => {
        this.$store.dispatch('showError', error);
      });
    },

    unregisterClient(): void {
      this.unregisterLoading = true;
      this.$store
        .dispatch('unregisterClient', this.client)
        .then(
          () => {
            this.$store.dispatch(
              'showSuccess',
              'client.action.unregister.success',
            );
          },
          (error) => {
            this.$store.dispatch('showError', error);
          },
        )
        .finally(() => {
          this.fetchClient(this.id);
          this.confirmUnregisterClient = false;
          this.unregisterLoading = false;
        });
    },
  },
});
</script>

<style lang="scss" scoped>
.title-action {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
}
</style>