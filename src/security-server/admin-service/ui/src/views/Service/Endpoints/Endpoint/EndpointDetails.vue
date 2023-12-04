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
  <div
    class="xrd-tab-max-width xrd-main-wrap"
    data-test="endpoint-details-dialog"
  >
    <div class="px-4 pt-4">
      <xrd-sub-view-title :title="$t('endpoints.details')" @close="close" />
      <div class="delete-wrap">
        <xrd-button
          v-if="showDelete"
          outlined
          data-test="delete-endpoint"
          @click="showDeletePopup()"
          >{{ $t('action.delete') }}
        </xrd-button>
      </div>
    </div>

    <div class="px-4">
      <div class="dlg-edit-row">
        <v-select
          v-bind="methodRef"
          class="dlg-row-input"
          data-test="endpoint-method"
          :label="$t('endpoints.httpRequestMethod')"
          autofocus
          variant="outlined"
          :items="methods"
        />
      </div>

      <div class="dlg-edit-row">
        <v-text-field
          v-bind="pathRef"
          variant="outlined"
          name="path"
          :label="$t('endpoints.path')"
          data-test="endpoint-path"
        ></v-text-field>
      </div>

      <div class="helper-text pl-2">
        <div>
          <div>{{ $t('endpoints.endpointHelp1') }}</div>
          <div>{{ $t('endpoints.endpointHelp2') }}</div>
          <div>{{ $t('endpoints.endpointHelp3') }}</div>
          <div>{{ $t('endpoints.endpointHelp4') }}</div>
        </div>
      </div>
    </div>
    <div class="xrd-footer-buttons-wrap">
      <xrd-button outlined @click="close()"
        >{{ $t('action.cancel') }}
      </xrd-button>
      <xrd-button
        class="save-button"
        :loading="saving"
        :disabled="!meta.touched || !meta.valid"
        @click="save()"
        >{{ $t('action.save') }}
      </xrd-button>
    </div>

    <!-- Confirm dialog delete REST -->
    <xrd-confirm-dialog
      v-if="confirmDelete"
      title="endpoints.deleteTitle"
      text="endpoints.deleteEndpointText"
      @cancel="confirmDelete = false"
      @accept="remove(id)"
    />
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { Permissions } from '@/global';
import { mapActions, mapState } from 'pinia';
import { useUser } from '@/store/modules/user';
import { useNotifications } from '@/store/modules/notifications';
import { PublicPathState, useForm } from 'vee-validate';
import { useServices } from '@/store/modules/services';

export default defineComponent({
  props: {
    id: {
      type: String,
      required: true,
    },
  },
  setup(props) {
    const { endpoints } = useServices();
    const endpoint = endpoints.find((endpoint) => endpoint.id === props.id)!;
    const { meta, resetForm, values, defineComponentBinds } = useForm({
      validationSchema: {
        method: 'required',
        path: 'required|xrdEndpoint',
      },
      initialValues: {
        method: endpoint.method,
        path: endpoint.path,
      },
    });
    const componentConfig = (state: PublicPathState) => ({
      props: {
        'error-messages': state.errors,
      },
    });
    const methodRef = defineComponentBinds('method', componentConfig);
    const pathRef = defineComponentBinds('path', componentConfig);
    return { meta, resetForm, values, methodRef, pathRef, endpoint };
  },
  data() {
    return {
      confirmDelete: false,
      saving: false,
      methods: [
        { title: this.$t('endpoints.all'), value: '*' },
        { title: 'GET', value: 'GET' },
        { title: 'POST', value: 'POST' },
        { title: 'PUT', value: 'PUT' },
        { title: 'PATCH', value: 'PATCH' },
        { title: 'DELETE', value: 'DELETE' },
        { title: 'HEAD', value: 'HEAD' },
        { title: 'OPTIONS', value: 'OPTIONS' },
        { title: 'TRACE', value: 'TRACE' },
      ],
    };
  },
  computed: {
    ...mapState(useUser, ['hasPermission']),
    ...mapState(useServices, ['endpoints']),
    showDelete(): boolean {
      return this.hasPermission(Permissions.DELETE_ENDPOINT);
    },
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    ...mapActions(useServices, ['deleteEndpoint', 'updateEndpoint']),
    close(): void {
      this.$router.back();
    },
    showDeletePopup(): void {
      this.confirmDelete = true;
    },
    remove(id: string): void {
      this.deleteEndpoint(id)
        .then(() => {
          this.showSuccess(this.$t('endpoints.deleteSuccess'));
          this.$router.back();
        })
        .catch((error) => {
          this.showError(error);
          this.confirmDelete = false;
        });
    },
    save(): void {
      this.saving = true;
      this.updateEndpoint({
        ...this.endpoint,
        method: this.values.method,
        path: this.values.path,
      })
        .then(async () => {
          this.showSuccess(this.$t('endpoints.editSuccess'));
          this.$router.back();
        })
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => {
          this.saving = false;
        });
    },
  },
});
</script>

<style lang="scss" scoped>
@import '@/assets/dialogs';
@import '@/assets/tables';

.delete-wrap {
  margin-top: 50px;
  display: flex;
  justify-content: flex-end;
}

.dlg-row-input {
  max-width: 400px;
}

.save-button {
  margin-left: 20px;
}

.helper-text {
  color: $XRoad-Black70;
}
</style>
