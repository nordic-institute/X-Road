<template>
  <v-card flat>
    <table class="xrd-table">
      <thead>
        <tr>
          <th>{{ $t('name') }}</th>
          <th></th>
        </tr>
      </thead>
      <template v-if="backups && backups.length > 0">
        <tr v-for="backup in filtered()" v-bind:key="backup.filename">
          <td>{{ backup.filename }}</td>
          <td>
            <div class="d-flex justify-end">
              <v-btn
                v-if="canBackup"
                small
                outlined
                rounded
                color="primary"
                class="xrd-small-button xrd-table-button"
                data-test="backup-download"
                >{{ $t('action.download') }}
              </v-btn>
              <v-btn
                v-if="canBackup"
                small
                outlined
                rounded
                color="primary"
                class="xrd-small-button xrd-table-button"
                data-test="backup-restore"
                >{{ $t('action.restore') }}
              </v-btn>
              <v-btn
                v-if="canBackup"
                small
                outlined
                rounded
                color="primary"
                class="xrd-small-button xrd-table-button"
                data-test="backup-delete"
                >{{ $t('action.delete') }}
              </v-btn>
            </div>
          </td>
        </tr>
      </template>
    </table>
  </v-card>
</template>

<script lang="ts">
import Vue from 'vue';
import { Backup } from '@/types';
import { selectedFilter } from '@/util/helpers';
import { Prop } from 'vue/types/options';

export default Vue.extend({
  props: {
    filter: {
      type: String,
      default: '',
    },
    canBackup: {
      type: Boolean,
      default: false,
      required: true,
    },
    backups: {
      type: Array as Prop<Backup[]>,
      required: true,
    },
  },
  methods: {
    filtered(): Backup[] {
      return selectedFilter(this.backups, this.filter, 'created_at');
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/colors';
@import '../../../assets/tables';
@import '../../../assets/global-style';
</style>
