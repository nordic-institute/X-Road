<template>
  <div class="xrd-tab-max-width xrd-view-common">
    <div class="wrap-right">
      <v-btn
        color="primary"
        @click="isAddEndpointDialogVisible = true"
        outlined
        rounded
        class="rounded-button elevation-0 rest-button"
        data-test="endpoint-add"
        >{{ $t('endpoints.addEndpoint') }}</v-btn
      >
    </div>

    <table class="xrd-table">
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
          <td>{{ endpoint.path }}</td>
          <td class="wrap-right-tight">
            <v-btn
              v-if="!endpoint.generated"
              small
              outlined
              rounded
              color="primary"
              class="xrd-small-button xrd-table-button"
              data-test="endpoint-edit"
              @click="editEndpoint(endpoint)"
              >{{ $t('action.edit') }}</v-btn
            >
            <v-btn
              small
              outlined
              rounded
              color="primary"
              class="xrd-small-button xrd-table-button"
              data-test="endpoint-edit-accessrights"
              @click="editAccessRights(endpoint)"
              >{{ $t('accessRights.title') }}</v-btn
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
import { RouteName } from '@/global';

export default Vue.extend({
  components: {
    addEndpointDialog,
  },
  computed: {
    ...mapGetters(['service']),

    endpoints(): Endpoint[] {
      const filtered = this.service.endpoints.filter((endpoint: Endpoint) => {
        return endpoint.method !== '*' && endpoint.path !== '**';
      });

      return filtered;
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
        .post(`/services/${this.service.id}/endpoints`, {
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
          this.$emit('updateService', this.service.id);
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
@import '../../../assets/global-style';

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
