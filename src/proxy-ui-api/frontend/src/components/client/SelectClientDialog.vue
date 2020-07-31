<template>
  <v-dialog :value="dialog" width="750" scrollable persistent>
    <v-card class="xrd-card">
      <v-card-title>
        <span class="headline">{{ $t(title) }}</span>
        <v-spacer />
        <i @click="cancel()" data-test="x-close-button"></i>
      </v-card-title>

      <v-card-text style="height: 500px;" class="elevation-0">
        <v-text-field
          v-model="search"
          :label="$t('wizard.client.member')"
          single-line
          hide-details
          class="search-input"
          data-test="client-search-input"
        >
          <v-icon slot="append">mdi-magnify</v-icon>
        </v-text-field>

        <!-- Table -->
        <v-radio-group v-model="selectedMember">
          <table class="xrd-table members-table fixed_header">
            <thead>
              <tr>
                <th class="checkbox-column"></th>
                <th>{{ $t('name') }}</th>
                <th>{{ $t('localGroup.id') }}</th>
              </tr>
            </thead>
            <tbody v-if="selectableClients && selectableClients.length > 0">
              <tr v-for="member in filteredMembers()" v-bind:key="member.id">
                <td class="checkbox-column">
                  <div class="checkbox-wrap">
                    <v-radio :key="member.id" :value="member"></v-radio>
                  </div>
                </td>

                <td>{{ member.member_name }}</td>
                <td>{{ member.id }}</td>
              </tr>
            </tbody>
          </table>
        </v-radio-group>

        <div v-if="filteredMembers().length < 1" class="empty-row">
          <p>{{ $t('localGroup.noResults') }}</p>
        </div>
      </v-card-text>
      <v-card-actions class="xrd-card-actions">
        <v-spacer></v-spacer>

        <large-button
          class="button-margin"
          outlined
          @click="cancel()"
          data-test="cancel-button"
          >{{ $t('action.cancel') }}</large-button
        >

        <large-button
          :disabled="!selectedMember"
          @click="save()"
          data-test="save-button"
          >{{ $t('localGroup.addSelected') }}</large-button
        >
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script lang="ts">
import Vue, { PropType } from 'vue';
import LargeButton from '@/components/ui/LargeButton.vue';
import { Client } from '@/openapi-types';

export default Vue.extend({
  components: {
    LargeButton,
  },
  props: {
    dialog: {
      type: Boolean,
      required: true,
    },
    title: {
      type: String,
      default: 'wizard.client.addClient',
    },
    selectableClients: {
      type: Array as PropType<Client[]>,
      default() {
        return [];
      },
    },
  },

  data() {
    return {
      search: '',
      selectedMember: undefined,
    };
  },
  methods: {
    filteredMembers() {
      if (!this.search) {
        return this.selectableClients;
      }

      const tempSearch = this.search
        .toString()
        .toLowerCase()
        .trim();
      if (tempSearch === '') {
        return this.selectableClients;
      }

      return this.selectableClients.filter((member) => {
        if (member.member_name?.toLowerCase().includes(tempSearch)) {
          return true;
        } else if (member.id?.toLowerCase().includes(tempSearch)) {
          return true;
        }

        return false;
      });
    },
    cancel(): void {
      this.clearForm();
      this.$emit('cancel');
    },
    save(): void {
      this.$emit('save', this.selectedMember);
      this.clearForm();
    },

    clearForm(): void {
      // Reset initial state
      this.selectedMember = undefined;
      this.search = '';
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/tables';
@import '../../assets/add-dialogs';

.checkbox-column {
  width: 50px;
}

.search-input {
  width: 300px;
}
</style>
