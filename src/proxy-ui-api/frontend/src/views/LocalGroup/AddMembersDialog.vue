<template>
  <v-dialog :value="dialog" width="750" scrollable persistent>
    <v-card class="xrd-card">
      <v-card-title>
        <span class="headline">{{ $t(title) }}</span>
        <v-spacer />
        <i @click="cancel()" id="close-x"></i>
      </v-card-title>

      <v-card-text style="height: 500px;" class="elevation-0">
        <v-expansion-panels class="elevation-0" v-model="expandPanel" multiple>
          <v-expansion-panel class="elevation-0">
            <v-expansion-panel-header></v-expansion-panel-header>
            <v-expansion-panel-content class="elevation-0">
              <template v-slot:header>
                <v-spacer />
                <div class="exp-title">
                  {{ $t('localGroup.searchOptions') }}
                </div>
              </template>

              <div>
                <div class="flex-wrap">
                  <div class="input-row">
                    <v-text-field
                      v-model="name"
                      :label="$t('name')"
                      single-line
                      hide-details
                      class="flex-input"
                    ></v-text-field>

                    <v-select
                      v-model="instance"
                      :items="xroadInstances"
                      :label="$t('instance')"
                      class="flex-input"
                      clearable
                    ></v-select>
                  </div>

                  <div class="input-row">
                    <v-select
                      v-model="memberClass"
                      :items="memberClasses"
                      :label="$t('member_class')"
                      class="flex-input"
                      clearable
                    ></v-select>
                    <v-text-field
                      v-model="memberCode"
                      :label="$t('member_code')"
                      single-line
                      hide-details
                      class="flex-input"
                    ></v-text-field>
                  </div>
                  <v-text-field
                    v-model="subsystemCode"
                    :label="$t('subsystem_code')"
                    single-line
                    hide-details
                    class="flex-input"
                  ></v-text-field>
                </div>

                <div class="search-wrap">
                  <large-button @click="search()">{{
                    $t('action.search')
                  }}</large-button>
                </div>
              </div>
            </v-expansion-panel-content>
          </v-expansion-panel>
        </v-expansion-panels>

        <!-- Table -->

        <table class="xrd-table members-table fixed_header">
          <thead>
            <tr>
              <th></th>
              <th>{{ $t('name') }}</th>
              <th>{{ $t('localGroup.id') }}</th>
            </tr>
          </thead>
          <tbody v-if="members && members.length > 0">
            <tr v-for="member in members" v-bind:key="member.id">
              <td>
                <div class="checkbox-wrap">
                  <v-checkbox
                    @change="checkboxChange(member.id, $event)"
                    color="primary"
                  ></v-checkbox>
                </div>
              </td>

              <td>{{ member.member_name }}</td>
              <td>{{ member.id }}</td>
            </tr>
          </tbody>
        </table>
        <div v-if="members.length < 1 && !noResults" class="empty-row"></div>

        <div v-if="noResults" class="empty-row">
          <p>{{ $t('localGroup.noResults') }}</p>
        </div>
      </v-card-text>
      <v-card-actions class="xrd-card-actions">
        <v-spacer></v-spacer>

        <large-button class="button-margin" outlined @click="cancel()">{{
          $t('action.cancel')
        }}</large-button>

        <large-button :disabled="!canSave" @click="save()">{{
          $t('localGroup.addSelected')
        }}</large-button>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script lang="ts">
import Vue, { PropType } from 'vue';
import { mapGetters } from 'vuex';
import * as api from '@/util/api';
import LargeButton from '@/components/ui/LargeButton.vue';
import { Client } from '@/openapi-types';

const initialState = () => {
  return {
    name: '',
    instance: '',
    memberClass: '',
    memberCode: '',
    subsystemCode: '',
    expandPanel: [0],
    members: [] as Client[],
    selectedIds: [] as string[],
    noResults: false,
    checkbox1: true,
  };
};

export default Vue.extend({
  components: {
    LargeButton,
  },
  props: {
    dialog: {
      type: Boolean,
      required: true,
    },
    filtered: {
      type: Array as PropType<Client[]>,
    },
    title: {
      type: String,
      default: 'localGroup.addMembers',
    },
  },

  data() {
    return { ...initialState() };
  },
  computed: {
    ...mapGetters(['xroadInstances', 'memberClasses']),
    canSave(): boolean {
      return this.selectedIds.length > 0;
    },
  },

  methods: {
    checkboxChange(id: string, event: boolean): void {
      if (event) {
        this.selectedIds.push(id);
      } else {
        const index = this.selectedIds.indexOf(id);
        if (index > -1) {
          this.selectedIds.splice(index, 1);
        }
      }
    },
    search(): void {
      this.noResults = false;
      let query = `/clients?name=${this.name}&member_code=${this.memberCode}&subsystem_code=${this.subsystemCode}&show_members=false&internal_search=false`;

      // These checks are needed because instance and member class (dropdowns) return undefined if they are first selected and then cleared
      if (this.instance) {
        query = query + `&instance=${this.instance}`;
      }

      if (this.memberClass) {
        query = query + `&member_class=${this.memberClass}`;
      }

      api
        .get<Client[]>(query)
        .then((res) => {
          if (this.filtered && this.filtered.length > 0) {
            // Filter out members that are already added
            this.members = res.data.filter((member) => {
              return !this.filtered.find((item) => {
                return item.id === member.id;
              });
            });
          } else {
            // Show results straight if there is nothing to filter
            this.members = res.data;
          }

          if (this.members.length < 1) {
            this.noResults = true;
          }
        })
        .catch((error) => {
          this.$store.dispatch('showError', error);
        });
    },

    cancel(): void {
      this.clearForm();
      this.$emit('cancel');
    },
    save(): void {
      this.$emit('membersAdded', this.selectedIds);
      this.clearForm();
    },

    clearForm(): void {
      // Reset initial state
      Object.assign(this.$data, initialState());
    },
  },
  created() {
    this.$store.dispatch('fetchXroadInstances');
    this.$store.dispatch('fetchMemberClasses');
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/tables';
@import '../../assets/add-dialogs';
</style>
