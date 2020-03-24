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
              <small-button
                v-if="canBackup"
                :min_width="50"
                class="xrd-table-button"
                data-test="backup-download"
                @click="downloadBackup(backup.filename)"
                >{{ $t('action.download') }}
              </small-button>
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
import * as api from '@/util/api';
import { Backup } from '@/types';
import { saveResponseAsFile, selectedFilter } from '@/util/helpers';
import SmallButton from '@/components/ui/SmallButton.vue';

export default Vue.extend({
  components: {
    SmallButton,
  },
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
  },
  data: () => ({
    backups: [] as Backup[],
  }),
  methods: {
    filtered(): Backup[] {
      return selectedFilter(this.backups, this.filter, 'created_at');
    },
    fetchData(): void {
      api
        .get('/backups')
        .then((res) => {
          this.backups = res.data.sort((a: Backup, b: Backup) => {
            return b.created_at > a.created_at;
          });
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
        });
    },
    async downloadBackup(fileName: string) {
      api
        .get(`/backups/${fileName}/download`, { responseType: 'blob' })
        .then((resp) => saveResponseAsFile(resp, fileName))
        .catch((error) => this.$bus.$emit('show-error', error.message));
    },
  },
  created(): void {
    this.fetchData();
  },
});
</script>

<style lang="scss" scoped>
@import '../../../assets/colors';
@import '../../../assets/tables';
@import '../../../assets/global-style';
</style>
