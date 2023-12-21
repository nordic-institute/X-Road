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
  <xrd-button
    data-test="delete-client-button"
    outlined
    @click="confirmDelete = true"
  >
    <xrd-icon-base class="xrd-large-button-icon">
      <xrd-icon-declined />
    </xrd-icon-base>
    {{ $t('action.delete') }}
  </xrd-button>

  <!-- Confirm dialog for delete client -->
  <xrd-confirm-dialog
    v-if="confirmDelete"
    :loading="deleteLoading"
    title="client.action.delete.confirmTitle"
    text="client.action.delete.confirmText"
    @cancel="confirmDelete = false"
    @accept="deleteClient()"
  />

  <!-- Confirm dialog for deleting orphans -->
  <xrd-confirm-dialog
    v-if="confirmOrphans"
    :loading="orphansLoading"
    title="client.action.removeOrphans.confirmTitle"
    text="client.action.removeOrphans.confirmText"
    cancel-button-text="client.action.removeOrphans.cancelButtonText"
    @cancel="notDeleteOrphans()"
    @accept="deleteOrphans()"
  />
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { RouteName } from '@/global';
import * as api from '@/util/api';
import { encodePathParameter } from '@/util/api';
import { mapActions } from 'pinia';
import { useNotifications } from '@/store/modules/notifications';

export default defineComponent({
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
    };
  },

  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    deleteClient(): void {
      this.deleteLoading = true;
      api.remove(`/clients/${encodePathParameter(this.id)}`).then(
        () => {
          this.showSuccess(this.$t('client.action.delete.success'));
          this.checkOrphans();
        },
        (error) => {
          this.showError(error);
          this.confirmDelete = false;
          this.deleteLoading = false;
        },
      );
    },

    checkOrphans(): void {
      api.get(`/clients/${encodePathParameter(this.id)}/orphans`).then(
        () => {
          this.confirmDelete = false;
          this.deleteLoading = false;
          this.confirmOrphans = true;
        },
        (error) => {
          this.confirmDelete = false;
          this.deleteLoading = false;
          if (error.response.status === 404) {
            // No orphans found so exit the view
            this.$router.replace({ name: RouteName.Clients });
          } else {
            // There was some other error, but the client is already deleted so exit the view
            this.showError(error);
            this.$router.replace({ name: RouteName.Clients });
          }
        },
      );
    },

    deleteOrphans(): void {
      this.orphansLoading = true;
      api
        .remove(`/clients/${encodePathParameter(this.id)}/orphans`)
        .then(
          () => {
            this.showSuccess(this.$t('client.action.removeOrphans.success'));
          },
          (error) => {
            // There was some other error, but the client is already deleted so exit the view
            this.showError(error);
          },
        )
        .finally(() => {
          this.confirmOrphans = false;
          this.orphansLoading = false;
          this.$router.replace({ name: RouteName.Clients });
        });
    },

    notDeleteOrphans(): void {
      this.confirmOrphans = false;
      this.$router.replace({ name: RouteName.Clients });
    },
  },
});
</script>
