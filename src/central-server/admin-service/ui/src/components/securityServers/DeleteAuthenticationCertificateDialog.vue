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
  <ValidationObserver v-slot="{ invalid }">
    <xrd-simple-dialog
      :disable-save="invalid"
      :dialog="true"
      :loading="loading"
      title="securityServers.securityServer.dialog.deleteAuthCertificate.title"
      save-button-text="action.delete"
      cancel-button-text="action.cancel"
      @cancel="cancel"
      @save="deleteCert"
    >
      <template #content>
        <p>
          {{
            $t(
              'securityServers.securityServer.dialog.deleteAuthCertificate.content',
            )
          }}
        </p>
        <ValidationProvider
          v-slot="{ errors }"
          :rules="`required|is:${securityServer.server_id.server_code}`"
          name="securityServerCode"
        >
          <v-text-field
            v-model="securityServerCode"
            data-test="verify-server-code"
            outlined
            autofocus
            :placeholder="
              $t(
                'securityServers.securityServer.dialog.deleteAuthCertificate.securityServerCode',
              )
            "
            :label="$t('fields.securityServerCode')"
            :error-messages="errors"
          >
          </v-text-field>
        </ValidationProvider>
      </template>
    </xrd-simple-dialog>
  </ValidationObserver>
</template>

<script lang="ts">
import { SecurityServer } from '@/openapi-types';
import { notificationsStore } from '@/store/modules/notifications';
import { useSecurityServerStore } from '@/store/modules/security-servers';
import { securityServerAuthCertStore } from '@/store/modules/security-servers-authentication-certificates';
import { mapActions, mapStores } from 'pinia';
import { ValidationObserver, ValidationProvider } from 'vee-validate';
import Vue from 'vue';
export default Vue.extend({
  name: 'DeleteAuthenticationCertificateDialog',
  components: {
    ValidationProvider,
    ValidationObserver,
  },
  props: {
    authenticationCertificateId: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      securityServerCode: '',
      loading: false,
    };
  },
  computed: {
    ...mapStores(securityServerAuthCertStore, useSecurityServerStore),
    securityServer(): SecurityServer | null {
      return this.securityServerStore.currentSecurityServer;
    },
  },
  methods: {
    ...mapActions(notificationsStore, ['showSuccess', 'showError']),
    cancel(): void {
      this.$emit('cancel');
    },
    deleteCert(): void {
      this.loading = true;
      this.securityServerAuthCertStore
        .delete(
          this.securityServer?.server_id.encoded_id as string,
          this.authenticationCertificateId,
        )
        .then(() => {
          this.showSuccess(
            this.$t(
              'securityServers.securityServer.dialog.deleteAuthCertificate.success',
            ),
          );
          this.$emit('delete');
        })
        .catch((error) => {
          this.showError(error);
          this.$emit('cancel');
        })
        .finally(() => (this.loading = false));
    },
  },
});
</script>

<style lang="scss" scoped></style>
