<template>
  <tr data-test="system.parameters-timestamping-service-row">
    <td>{{ timestampingService.name }}</td>
    <td>{{ timestampingService.url }}</td>
    <td>
      <small-button
        data-test="system-parameters-timestamping-service-delete-button"
        outlined
        @click="confirmDeleteDialog = true"
        :requires-permission="permissions.DELETE_TSP"
      >
        {{
          $t('systemParameters.timestampingServices.table.action.delete.button')
        }}
      </small-button>
      <confirm-dialog
        data-test="system-parameters-timestamping-service-delete-confirm-dialog"
        :dialog="confirmDeleteDialog"
        @cancel="confirmDeleteDialog = false"
        @accept="deleteTimestampingService"
        :loading="deleting"
        :title="
          $t(
            'systemParameters.timestampingServices.table.action.delete.confirmation.title',
          )
        "
        :text="
          $t(
            'systemParameters.timestampingServices.table.action.delete.confirmation.text',
          )
        "
      />
    </td>
  </tr>
</template>

<script lang="ts">
import Vue from 'vue';
import ConfirmDialog from '@/components/ui/ConfirmDialog.vue';
import { TimestampingService } from '@/openapi-types';
import { Permissions } from '@/global';
import SmallButton from '@/components/ui/SmallButton.vue';
import { Prop } from 'vue/types/options';
import * as api from '@/util/api';

export default Vue.extend({
  name: 'TimestampingServiceRow',
  components: {
    ConfirmDialog,
    SmallButton,
  },
  props: {
    timestampingService: {
      type: Object as Prop<TimestampingService>,
      required: true,
    },
  },
  data() {
    return {
      confirmDeleteDialog: false,
      deleting: false,
      permissions: Permissions,
    };
  },
  methods: {
    deleteTimestampingService(): void {
      this.deleting = true;
      api
        .post('/system/timestamping-services/delete', this.timestampingService)
        .then((_) => {
          this.deleting = false;
          this.confirmDeleteDialog = false;
          this.$emit('deleted');
          this.$store.dispatch(
            'showSuccess',
            'systemParameters.timestampingServices.table.action.delete.success',
          );
        })
        .catch((error) => this.$store.dispatch('showError', error));
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/colors';
@import '../../../assets/tables';

tr td {
  color: $XRoad-Black;
  font-weight: normal !important;
}

tr td:last-child {
  width: 1%;
  white-space: nowrap;
}
</style>
