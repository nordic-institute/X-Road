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
  <xrd-simple-dialog
    title="securityServers.dialogs.deleteAddress.title"
    data-test="security-server-delete-dialog"
    save-button-text="action.delete"
    :dialog="showDialog"
    :scrollable="false"
    :show-close="true"
    :loading="loading"
    :disable-save="!meta.valid"
    @save="deleteSecurityServer"
    @cancel="close"
  >
    <template #text>
      <i18n-t
        scope="global"
        keypath="securityServers.dialogs.deleteAddress.areYouSure"
      >
        <template #securityServer>
          <b>{{ serverCode }}</b>
        </template>
      </i18n-t>
    </template>
    <template #content>
      <v-text-field
        v-model="value"
        name="serverCode"
        variant="outlined"
        :label="$t('securityServers.dialogs.deleteAddress.enterCode')"
        autofocus
        data-test="verify-server-code"
        :error-messages="errors"
      />
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { useSecurityServer } from '@/store/modules/security-servers';
import { useNotifications } from '@/store/modules/notifications';
import { useField } from 'vee-validate';
import { mapActions, mapStores } from 'pinia';
import { RouteName } from '@/global';

/**
 * Component for a Security server details view
 */
export default defineComponent({
  props: {
    securityServerId: {
      type: String,
      default: '',
    },
    serverCode: {
      type: String,
      default: '',
    },
  },
  setup(props) {
    const { value, meta, errors, resetField } = useField(
      'serverCode',
      {
        required: true,
        is: props.serverCode,
      },
      { initialValue: '' },
    );
    return { value, meta, errors, resetField };
  },
  data() {
    return {
      loading: false,
      showDialog: false,
    };
  },
  computed: {
    ...mapStores(useSecurityServer),
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    open(): void {
      this.showDialog = true;
    },
    close(): void {
      this.resetField();
      this.showDialog = false;
    },
    deleteSecurityServer: async function () {
      try {
        this.loading = true;
        await this.securityServerStore.delete(this.securityServerId);
        this.showSuccess(
          this.$t('securityServers.dialogs.deleteAddress.success'),
          true,
        );
        this.$router.replace({
          name: RouteName.SecurityServers,
        });
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
