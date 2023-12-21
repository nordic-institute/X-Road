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
  <xrd-simple-dialog
    :disable-save="!meta.valid"
    :loading="loading"
    title="securityServers.securityServer.dialog.deleteAuthCertificate.title"
    save-button-text="action.delete"
    cancel-button-text="action.cancel"
    @cancel="cancel"
    @save="deleteCert"
  >
    <template #text>
      {{
        $t(
          'securityServers.securityServer.dialog.deleteAuthCertificate.content',
        )
      }}
    </template>
    <template #content>
      <v-text-field
        v-model="value"
        data-test="verify-server-code"
        variant="outlined"
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
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts">
import { SecurityServer, SecurityServerId } from '@/openapi-types';
import { useNotifications } from '@/store/modules/notifications';
import { useSecurityServer } from '@/store/modules/security-servers';
import { useSecurityServerAuthCert } from '@/store/modules/security-servers-authentication-certificates';
import { defineComponent, PropType } from 'vue';
import { useField } from 'vee-validate';
import { mapActions, mapStores } from 'pinia';

export default defineComponent({
  props: {
    authenticationCertificateId: {
      type: String,
      required: true,
    },
    securityServerId: {
      type: Object as PropType<SecurityServerId>,
      required: true,
    },
  },
  emits: ['cancel', 'delete'],
  setup(props) {
    const { value, meta, errors } = useField(
      'securityServerCode',
      {
        required: true,
        is: props.securityServerId.server_code,
      },
      { initialValue: '' },
    );
    return { value, meta, errors };
  },
  data() {
    return {
      loading: false,
    };
  },
  computed: {
    ...mapStores(useSecurityServerAuthCert, useSecurityServer),
    securityServer(): SecurityServer | null {
      return this.securityServerStore.currentSecurityServer;
    },
  },
  methods: {
    ...mapActions(useNotifications, ['showSuccess', 'showError']),
    cancel(): void {
      this.$emit('cancel');
    },
    deleteCert(): void {
      this.loading = true;
      this.securityServerAuthCertStore
        .deleteAuthenticationCertificate(
          this.securityServerId.encoded_id as string,
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
