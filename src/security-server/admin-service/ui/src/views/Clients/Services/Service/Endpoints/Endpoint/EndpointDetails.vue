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
  <XrdElevatedViewFixedWidth
    title="endpoints.details"
    data-test="endpoint-details-dialog"
    go-back-on-close
    :breadcrumbs="breadcrumbs"
    @close="close"
  >
    <XrdFormBlock>
      <XrdFormBlockRow full-length>
        <v-select
          v-model="methodMdl"
          v-bind="methodAttr"
          data-test="endpoint-method"
          class="xrd"
          autofocus
          :label="$t('endpoints.httpRequestMethod')"
          :items="methods"
        />
      </XrdFormBlockRow>
      <XrdFormBlockRow full-length>
        <v-text-field v-model="pathMdl" v-bind="pathAttr" data-test="endpoint-path" class="xrd" name="path" :label="$t('endpoints.path')" />
      </XrdFormBlockRow>
    </XrdFormBlock>
    <div class="font-weight-regular mt-6 on-surface">
      <div>{{ $t('endpoints.endpointHelp1') }}</div>
      <div>{{ $t('endpoints.endpointHelp2') }}</div>
      <div>{{ $t('endpoints.endpointHelp3') }}</div>
      <div>{{ $t('endpoints.endpointHelp4') }}</div>
    </div>
    <template #footer>
      <v-spacer />
      <XrdBtn
        v-if="showDelete"
        data-test="delete-endpoint"
        variant="outlined"
        text="action.delete"
        prepend-icon="delete_forever"
        @click="showDeletePopup()"
      />

      <XrdBtn
        class="save-button ml-2"
        text="action.save"
        prepend-icon="check_circle"
        :loading="saving"
        :disabled="!meta.dirty || !meta.valid"
        @click="save()"
      />
      <!-- Confirm dialog delete REST -->
      <XrdConfirmDialog
        v-if="confirmDelete"
        title="endpoints.deleteTitle"
        text="endpoints.deleteEndpointText"
        :loading="deleting"
        @cancel="confirmDelete = false"
        @accept="remove(id)"
      />
    </template>
  </XrdElevatedViewFixedWidth>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { Permissions, RouteName } from '@/global';
import { mapActions, mapState } from 'pinia';
import { useUser } from '@/store/modules/user';
import { useForm } from 'vee-validate';
import { useServices } from '@/store/modules/services';
import {
  XrdElevatedViewFixedWidth,
  XrdBtn,
  XrdFormBlock,
  XrdFormBlockRow,
  useNotifications,
  XrdConfirmDialog,
  veeDefaultFieldConfig,
} from '@niis/shared-ui';
import { BreadcrumbItem } from 'vuetify/lib/components/VBreadcrumbs/VBreadcrumbs';
import { clientTitle } from '@/util/ClientUtil';
import { Endpoint } from '@/openapi-types';
import { createFullServiceId } from '@/util/helpers';
import { useServiceDescriptions } from '@/store/modules/service-descriptions';
import { useClient } from '@/store/modules/client';

export default defineComponent({
  components: {
    XrdFormBlock,
    XrdFormBlockRow,
    XrdElevatedViewFixedWidth,
    XrdBtn,
    XrdConfirmDialog,
  },
  props: {
    id: {
      type: String,
      required: true,
    },
  },
  setup() {
    const { addError, addSuccessMessage } = useNotifications();
    const { meta, resetForm, values, defineField } = useForm({
      validationSchema: {
        method: 'required',
        path: 'required|xrdEndpoint',
      },
      initialValues: {
        method: '',
        path: '',
      },
    });
    const componentConfig = veeDefaultFieldConfig();
    const [methodMdl, methodAttr] = defineField('method', componentConfig);
    const [pathMdl, pathAttr] = defineField('path', componentConfig);
    return {
      meta,
      resetForm,
      values,
      methodMdl,
      methodAttr,
      pathMdl,
      pathAttr,
      addError,
      addSuccessMessage,
    };
  },
  data() {
    return {
      endpoint: undefined as Endpoint | undefined,
      confirmDelete: false,
      saving: false,
      deleting: false,
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
    ...mapState(useServices, ['service']),
    ...mapState(useServiceDescriptions, ['serviceDescription']),
    ...mapState(useClient, ['client', 'clientLoading']),
    showDelete(): boolean {
      return this.hasPermission(Permissions.DELETE_ENDPOINT);
    },
    title() {
      return `${this.endpoint?.method}${this.endpoint?.path}`;
    },
    breadcrumbs() {
      const breadcrumbs = [
        {
          title: this.$t('tab.main.clients'),
          to: { name: RouteName.Clients },
        },
      ] as BreadcrumbItem[];

      if (this.client) {
        breadcrumbs.push(
          {
            title: clientTitle(this.client, this.clientLoading),
            to: {
              name: RouteName.SubsystemDetails,
              params: { id: this.client.id },
            },
          },
          {
            title: this.$t('tab.client.services'),
            to: {
              name: RouteName.SubsystemServices,
              params: { id: this.client.id },
            },
          },
        );
      }
      if (this.client && this.serviceDescription?.type) {
        breadcrumbs.push({
          title: this.serviceDescription?.type,
          to: {
            name: RouteName.SubsystemServices,
            params: { id: this.client.id },
            query: { expand: this.serviceDescription?.id },
          },
        });
      }
      if (this.service) {
        breadcrumbs.push(
          {
            title: this.service?.full_service_code || '',
            to: {
              name: RouteName.ServiceParameters,
              params: { serviceId: this.service?.id },
            },
          },
          {
            title: this.$t('tab.services.endpoints'),
            to: {
              name: RouteName.Endpoints,
              params: { serviceId: this.service?.id },
            },
          },
        );
      }
      breadcrumbs.push({
        title: this.title,
      });
      return breadcrumbs;
    },
  },
  watch: {
    id: {
      immediate: true,
      handler(id: string) {
        this.fetchEndpoint(id)
          .then((endpoint) => {
            this.endpoint = endpoint;
            this.resetForm({
              values: {
                method: endpoint.method,
                path: endpoint.path,
              },
            });
            return endpoint;
          })
          .then((endpoint) => this.fetchService(createFullServiceId(endpoint.client_id || '', endpoint.service_code)))
          .then((service) => this.fetchServiceDescription(service.service_description_id))
          .then((description) => this.fetchClient(description.client_id))
          .catch((error) => this.addError(error, { navigate: true }));
      },
    },
  },
  methods: {
    ...mapActions(useClient, ['fetchClient']),
    ...mapActions(useServiceDescriptions, ['fetchServiceDescription']),
    ...mapActions(useServices, ['deleteEndpoint', 'updateEndpoint', 'fetchEndpoint', 'fetchService']),
    close(): void {
      this.$router.back();
    },
    showDeletePopup(): void {
      this.confirmDelete = true;
    },
    remove(id: string): void {
      this.deleting = true;
      this.deleteEndpoint(id)
        .then(() => {
          this.addSuccessMessage('endpoints.deleteSuccess', {}, true);
          this.$router.back();
        })
        .catch((error) => this.addError(error))
        .finally(() => (this.confirmDelete = false))
        .finally(() => (this.deleting = false));
    },
    save(): void {
      if (!this.endpoint) {
        return;
      }
      this.saving = true;
      this.updateEndpoint({
        ...this.endpoint,
        method: this.values.method as Endpoint.method,
        path: this.values.path,
      })
        .then(async () => {
          this.addSuccessMessage('endpoints.editSuccess', {}, true);
          this.$router.back();
        })
        .catch((error) => this.addError(error))
        .finally(() => {
          this.saving = false;
        });
    },
  },
});
</script>

<style lang="scss" scoped></style>
