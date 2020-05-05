<template>
  <div class="xrd-tab-max-width xrd-view-common">
    <v-flex mb-4 class="title-action">
      <h1 v-if="client" class="display-1 mb-3">{{client.subsystem_code}} ({{ $t('subsystem') }})</h1>
      <div>
        <LargeButton v-if="showDelete" @click="confirmDelete = true">{{$t('action.delete')}}</LargeButton>
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

    <!-- Confirm dialog for delete client -->
    <ConfirmDialog
      :dialog="confirmDelete"
      :loading="deleteLoading"
      title="client.action.delete.confirmTitle"
      text="client.action.delete.confirmText"
      @cancel="confirmDelete = false"
      @accept="deleteClient()"
    />

    <!-- Confirm dialog for deleting orphans -->
    <ConfirmDialog
      :dialog="confirmOrphans"
      :loading="orphansLoading"
      title="client.action.removeOrphans.confirmTitle"
      text="client.action.removeOrphans.confirmText"
      @cancel="confirmOrphans = false"
      @accept="deleteOrphans()"
    />

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
      orphansLoading: false as boolean,
      confirmDelete: false as boolean,
      deleteLoading: false as boolean,
      confirmOrphans: false as boolean,
      tab: undefined as undefined | Tab,
      confirmUnregisterClient: false as boolean,
      unregisterLoading: false as boolean,
    };
  },
  computed: {
    ...mapGetters(['client']),

    showUnregister(): boolean {
      return (
        this.client &&
        this.$store.getters.hasPermission(Permissions.SEND_CLIENT_DEL_REQ) &&
        (this.client.status === 'REGISTERED' ||
          this.client.status === 'REGISTRATION_IN_PROGRESS')
      );
    },

    showDelete(): boolean {
      if (
        !this.client ||
        this.client.status === 'REGISTERED' ||
        this.client.status === 'REGISTRATION_IN_PROGRESS'
      ) {
        return false;
      }

      return this.$store.getters.hasPermission(Permissions.SEND_CLIENT_DEL_REQ);
    },

    tabs(): Tab[] {
      const allTabs: Tab[] = [
        {
          key: 'details',
          name: 'tab.client.details',
          to: {
            name: RouteName.SubsystemDetails,
            params: { id: this.id },
          },
        },
        {
          key: 'serviceClients',
          name: 'tab.client.serviceClients',
          to: {
            name: RouteName.SubsystemServiceClients,
            params: { id: this.id },
          },
          permission: Permissions.VIEW_CLIENT_ACL_SUBJECTS,
        },
        {
          key: 'services',
          name: 'tab.client.services',
          to: {
            name: RouteName.SubsystemServices,
            params: { id: this.id },
          },
          permission: Permissions.VIEW_CLIENT_SERVICES,
        },
        {
          key: 'internalServers',
          name: 'tab.client.internalServers',
          to: {
            name: RouteName.SubsystemServers,
            params: { id: this.id },
          },
          permission: Permissions.VIEW_CLIENT_INTERNAL_CERTS,
        },
        {
          key: 'localGroups',
          name: 'tab.client.localGroups',
          to: {
            name: RouteName.SubsystemLocalGroups,
            params: { id: this.id },
          },
          permission: Permissions.VIEW_CLIENT_LOCAL_GROUPS,
        },
      ];

      return this.$store.getters.getAllowedTabs(allTabs);
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
    deleteClient(): void {
      this.deleteLoading = true;
      this.$store.dispatch('deleteClient', this.client.id).then(
        (response) => {
          this.$store.dispatch('showSuccess', 'client.action.delete.success');
          this.checkOrphans();
        },
        (error) => {
          this.$store.dispatch('showError', error);
          this.confirmDelete = false;
          this.deleteLoading = false;
        },
      );
    },

    checkOrphans(): void {
      this.$store.dispatch('getOrphans', this.client.id).then(
        (response) => {
          this.confirmDelete = false;
          this.deleteLoading = false;
          this.confirmOrphans = true;
        },
        (error) => {
          this.confirmDelete = false;
          this.deleteLoading = false;
          if (error.response.status === 404) {
            // No orphans found so exit the view
            this.$router.go(-1);
          } else {
            // There was some other error, but the client is already deleted so exit the view
            this.$store.dispatch('showError', error);
            this.$router.go(-1);
          }
        },
      );
    },

    deleteOrphans(): void {
      this.orphansLoading = true;
      this.$store
        .dispatch('deleteOrphans', this.client.id)
        .then(
          (response) => {
            this.$store.dispatch(
              'showSuccess',
              'client.action.removeOrphans.success',
            );
          },
          (error) => {
            // There was some other error, but the client is already deleted so exit the view
            this.$store.dispatch('showError', error);
          },
        )
        .finally(() => {
          this.confirmOrphans = false;
          this.orphansLoading = false;
          this.$router.go(-1);
        });
    },

    unregisterClient(): void {
      this.unregisterLoading = true;
      this.$store
        .dispatch('unregisterClient', this.client)
        .then(
          (response) => {
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