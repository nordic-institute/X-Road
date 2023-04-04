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
<!--
  Member details view
-->
<template>
  <validation-observer v-slot="{ invalid }">
    <xrd-simple-dialog
      title="securityServers.dialogs.deleteAddress.title"
      data-test="security-server-delete-dialog"
      save-button-text="action.delete"
      :dialog="showDialog"
      :scrollable="false"
      :show-close="true"
      :loading="loading"
      :disable-save="invalid"
      @save="deleteSecurityServer"
      @cancel="close"
    >
      <div slot="content">
        <v-card-text class="pt-4" data-test="delete-subsystem">
          <i18n path="securityServers.dialogs.deleteAddress.areYouSure">
            <template #securityServer>
              <b>{{ serverCode }}</b>
            </template>
          </i18n>
        </v-card-text>
        <div class="pt-4 dlg-input-width">
          <validation-provider
            v-slot="{ errors }"
            ref="serverCodeInput"
            name="serverCode"
            :rules="{
              required: true,
              is: serverCode,
            }"
          >
            <v-text-field
              v-model="offeredCode"
              name="serverCode"
              outlined
              :label="$t('securityServers.dialogs.deleteAddress.enterCode')"
              autofocus
              data-test="verify-server-code"
              :error-messages="errors"
            ></v-text-field>
          </validation-provider>
        </div>
      </div>
    </xrd-simple-dialog>
  </validation-observer>
</template>

<script lang="ts">
import Vue from 'vue';
import { ValidationProvider, ValidationObserver } from 'vee-validate';
import { mapActions, mapStores } from 'pinia';
import { useSecurityServerStore } from '@/store/modules/security-servers';
import { notificationsStore } from '@/store/modules/notifications';

/**
 * Component for a Security server details view
 */
export default Vue.extend({
  components: {
    ValidationProvider,
    ValidationObserver,
  },
  props: {
    securityServerId: {
      type: String,
      default: '',
    },
    serverCode: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      loading: false,
      showDialog: false,
      offeredCode: '',
    };
  },
  computed: {
    ...mapStores(useSecurityServerStore),
  },
  methods: {
    ...mapActions(notificationsStore, ['showError']),
    open(): void {
      this.showDialog = true;
    },
    close(): void {
      this.offeredCode = '';
      this.showDialog = false;
    },
    deleteSecurityServer: async function () {
      try {
        this.loading = true;
        await this.securityServerStore.delete(this.securityServerId);
        this.close();
        this.$emit('deleted');
      } catch (error: unknown) {
        this.showError(error);
      } finally {
        this.loading = false;
      }
    },
  },
});
</script>

<style lang="scss" scoped></style>
