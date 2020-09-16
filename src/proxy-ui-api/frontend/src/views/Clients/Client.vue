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
  <div class="xrd-tab-max-width xrd-view-common">
    <v-flex mb-4 class="title-action">
      <h1 v-if="client && client.owner" class="display-1 mb-3">
        {{ client.member_name }} ({{ $t('client.owner') }})
      </h1>
      <h1 v-else-if="client" class="display-1 mb-3">
        {{ client.member_name }} ({{ $t('client.member') }})
      </h1>

      <div class="action-block">
        <MakeOwnerButton
          v-if="showMakeOwner"
          :id="id"
          @done="fetchClient"
          class="first-button"
        />
        <DeleteClientButton v-if="showDelete" :id="id" />
        <UnregisterClientButton
          v-if="showUnregister"
          :id="id"
          @done="fetchClient"
        />
      </div>
    </v-flex>
    <v-tabs
      v-model="tab"
      class="xrd-tabs"
      color="secondary"
      grow
      slider-size="4"
    >
      <v-tabs-slider color="secondary"></v-tabs-slider>
      <v-tab v-for="tab in tabs" v-bind:key="tab.key" :to="tab.to">{{
        $t(tab.name)
      }}</v-tab>
    </v-tabs>

    <router-view />
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';
import { Permissions, RouteName } from '@/global';
import { Tab } from '@/ui-types';
import DeleteClientButton from '@/components/client/DeleteClientButton.vue';
import UnregisterClientButton from '@/components/client/UnregisterClientButton.vue';
import MakeOwnerButton from '@/components/client/MakeOwnerButton.vue';

export default Vue.extend({
  components: {
    UnregisterClientButton,
    DeleteClientButton,
    MakeOwnerButton,
  },
  props: {
    id: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      tab: undefined as undefined | Tab,
    };
  },
  computed: {
    ...mapGetters(['client']),

    showMakeOwner(): boolean {
      return (
        this.client &&
        this.$store.getters.hasPermission(Permissions.SEND_OWNER_CHANGE_REQ) &&
        this.client.status === 'REGISTERED' &&
        !this.client.owner
      );
    },
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

      return this.$store.getters.hasPermission(Permissions.DELETE_CLIENT);
    },

    tabs(): Tab[] {
      const allTabs: Tab[] = [
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
          permissions: [Permissions.VIEW_CLIENT_INTERNAL_CERTS],
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
  },
});
</script>

<style lang="scss" scoped>
.title-action {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
}

.action-block {
  display: flex;
  flex-direction: row;
}

.first-button {
  margin-right: 20px;
}
</style>
