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
  <div>
    <div class="wrap-right px-4">
      <xrd-large-button
        v-if="canAddEndpoint"
        color="primary"
        @click="isAddEndpointDialogVisible = true"
        data-test="endpoint-add"
        >{{ $t('endpoints.addEndpoint') }}</xrd-large-button
      >
    </div>

    <table class="xrd-table mb-4">
      <thead>
        <tr>
          <th>{{ $t('endpoints.httpRequestMethod') }}</th>
          <th>{{ $t('endpoints.path') }}</th>
          <th></th>
        </tr>
      </thead>
      <tbody v-if="service.endpoints">
        <tr
          v-for="endpoint in endpoints"
          :key="endpoint.id"
          v-bind:class="{ generated: endpoint.generated }"
        >
          <td>
            <span v-if="endpoint.method === '*'">{{
              $t('endpoints.all')
            }}</span>
            <span v-else>{{ endpoint.method }}</span>
          </td>
          <td class="identifier-wrap">{{ endpoint.path }}</td>
          <td class="wrap-right-tight">
            <xrd-large-button
              v-if="!endpoint.generated && canEditEndpoint"
              text
              class="xrd-table-button"
              data-test="endpoint-edit"
              @click="editEndpoint(endpoint)"
              >{{ $t('action.edit') }}</xrd-large-button
            >
            <xrd-large-button
              v-if="canViewAccessRights"
              text
              :outlined="false"
              class="xrd-table-button"
              data-test="endpoint-edit-accessrights"
              @click="editAccessRights(endpoint)"
              >{{ $t('accessRights.title') }}</xrd-large-button
            >
          </td>
        </tr>
      </tbody>
    </table>

    <addEndpointDialog
      :dialog="isAddEndpointDialogVisible"
      @save="addEndpoint"
      @cancel="cancelAddEndpoint"
    />
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';
import { Endpoint } from '@/openapi-types';
import * as api from '@/util/api';
import addEndpointDialog from './AddEndpointDialog.vue';
import { RouteName, Permissions } from '@/global';
import { encodePathParameter } from '@/util/api';

export default Vue.extend({
  components: {
    addEndpointDialog,
  },
  computed: {
    ...mapGetters(['service']),

    endpoints(): Endpoint[] {
      return this.service.endpoints.filter((endpoint: Endpoint) => {
        return !this.isBaseEndpoint(endpoint);
      });
    },

    canAddEndpoint(): boolean {
      return this.$store.getters.hasPermission(
        Permissions.ADD_OPENAPI3_ENDPOINT,
      );
    },

    canEditEndpoint(): boolean {
      return this.$store.getters.hasPermission(
        Permissions.EDIT_OPENAPI3_ENDPOINT,
      );
    },

    canViewAccessRights(): boolean {
      return this.$store.getters.hasPermission(Permissions.VIEW_ENDPOINT_ACL);
    },
  },
  data() {
    return {
      isAddEndpointDialogVisible: false,
    };
  },
  methods: {
    addEndpoint(method: string, path: string): void {
      api
        .post(`/services/${encodePathParameter(this.service.id)}/endpoints`, {
          method,
          path,
          service_code: this.service.service_code,
        })
        .then(() => {
          this.$store.dispatch(
            'showSuccess',
            'endpoints.saveNewEndpointSuccess',
          );
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
        })
        .finally(() => {
          this.isAddEndpointDialogVisible = false;
          this.$emit('update-service', this.service.id);
        });
    },
    isBaseEndpoint(endpoint: Endpoint): boolean {
      return endpoint.method === '*' && endpoint.path === '**';
    },
    editEndpoint(endpoint: Endpoint): void {
      if (!endpoint.id) {
        return;
      }
      this.$router.push({
        name: RouteName.EndpointDetails,
        params: { id: endpoint.id },
      });
    },
    editAccessRights(endpoint: Endpoint): void {
      if (!endpoint.id) {
        return;
      }
      this.$router.push({
        name: RouteName.EndpointAccessRights,
        params: { id: endpoint.id },
      });
    },
    cancelAddEndpoint(): void {
      this.isAddEndpointDialogVisible = false;
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/colors';
@import '../../../assets/tables';

.path-wrapper {
  white-space: nowrap;
  min-width: 100px;
}

.url-wrapper {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 400px;
}

.generated {
  color: $XRoad-Grey40;
}

.wrap-right-tight {
  display: flex;
  width: 100%;
  justify-content: flex-end;
}
</style>
