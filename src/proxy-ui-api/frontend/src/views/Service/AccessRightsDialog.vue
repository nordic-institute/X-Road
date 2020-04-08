<template>
  <v-dialog :value="dialog" width="850" scrollable persistent>
    <v-card class="xrd-card">
      <v-card-title>
        <span class="headline">{{$t('accessRights.addSubjectsTitle')}}</span>
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
                <div class="exp-title">{{$t('localGroup.searchOptions')}}</div>
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
                      label="Member group code"
                      single-line
                      hide-details
                      class="flex-input"
                    ></v-text-field>
                  </div>

                  <div class="input-row">
                    <v-text-field
                      v-model="subsystemCode"
                      :label="$t('subsystem_code')"
                      single-line
                      hide-details
                      class="flex-input"
                    ></v-text-field>

                    <v-select
                      v-model="subjectType"
                      :items="subjectTypeItems"
                      label="Subject type"
                      class="flex-input"
                      clearable
                    ></v-select>
                  </div>
                </div>

                <div class="search-wrap">
                  <large-button @click="search()">{{$t('action.search')}}</large-button>
                </div>
              </div>
            </v-expansion-panel-content>
          </v-expansion-panel>
        </v-expansion-panels>

        <!-- Table -->
        <table class="xrd-table members-table fixed_header">
          <thead>
            <tr>
              <th class="first-column"></th>
              <th>{{$t('services.memberNameGroupDesc')}}</th>
              <th>{{$t('services.idGroupCode')}}</th>
              <th>{{$t('type')}}</th>
            </tr>
          </thead>
          <tbody v-if="subjects && subjects.length > 0">
            <tr v-for="subject in subjects" v-bind:key="subject.id">
              <td class="first-column">
                <div class="checkbox-wrap">
                  <v-checkbox @change="checkboxChange(subject, $event)" color="primary"></v-checkbox>
                </div>
              </td>

              <td>{{subject.member_name_group_description}}</td>
              <td
                v-if="subject.subject_type === subjectTypes.LOCALGROUP"
              >{{subject.local_group_code}}</td>
              <td v-else>{{subject.id}}</td>
              <td>{{subject.subject_type}}</td>
            </tr>
          </tbody>
        </table>
        <div v-if="subjects.length < 1 && !noResults" class="empty-row"></div>

        <div v-if="noResults" class="empty-row">
          <p>{{$t('localGroup.noResults')}}</p>
        </div>
      </v-card-text>
      <v-card-actions class="xrd-card-actions">
        <v-spacer></v-spacer>

        <large-button class="button-margin" outlined @click="cancel()">{{$t('action.cancel')}}</large-button>

        <large-button :disabled="!canSave" @click="save()">{{$t('localGroup.addSelected')}}</large-button>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script lang="ts">
import Vue from 'vue';
import * as api from '@/util/api';
import { mapGetters } from 'vuex';
import LargeButton from '@/components/ui/LargeButton.vue';

enum SubjectTypes {
  GLOBALGROUP = 'GLOBALGROUP',
  LOCALGROUP = 'LOCALGROUP',
  SUBSYSTEM = 'SUBSYSTEM',
}

function initialState() {
  return {
    name: '',
    subjectType: '',
    instance: '',
    memberClass: '',
    memberCode: '',
    subsystemCode: '',
    subjectTypes: SubjectTypes,
    expandPanel: [0],
    subjects: [],
    selectedIds: [] as string[],
    noResults: false,
    checkbox1: true,
  };
}

export default Vue.extend({
  components: {
    LargeButton,
  },
  props: {
    dialog: {
      type: Boolean,
      required: true,
    },
    clientId: {
      type: String,
      required: true,
    },
    filtered: {
      type: Array,
    },
  },

  data() {
    return initialState();
  },
  computed: {
    ...mapGetters(['xroadInstances', 'memberClasses', 'accessRightsSubjects']),
    canSave(): boolean {
      if (this.selectedIds.length > 0) {
        return true;
      }
      return false;
    },
    subjectTypeItems(): object[] {
      // Returns items for subject type select with translated texts
      return [
        {
          text: this.$t('subjectType.globalGroup'),
          value: SubjectTypes.GLOBALGROUP,
        },
        {
          text: this.$t('subjectType.localGroup'),
          value: SubjectTypes.LOCALGROUP,
        },
        {
          text: this.$t('subjectType.subsystem'),
          value: SubjectTypes.SUBSYSTEM,
        },
      ];
    },
  },
  methods: {
    checkboxChange(subject: any, event: any): void {
      if (event === true) {
        this.selectedIds.push(subject);
      } else {
        const index = this.selectedIds.indexOf(subject);
        if (index > -1) {
          this.selectedIds.splice(index, 1);
        }
      }
    },
    search(): void {
      this.noResults = false;
      let query = `/clients/${this.clientId}/subjects?member_name_group_description=${this.name}&member_group_code=${this.memberCode}&subsystem_code=${this.subsystemCode}`;

      // These checks are needed because instance, subject type and member class (dropdowns) return undefined if they are first selected and then cleared
      if (this.instance) {
        query = query + `&instance=${this.instance}`;
      }

      if (this.memberClass) {
        query = query + `&member_class=${this.memberClass}`;
      }

      if (this.subjectType) {
        query = query + `&subject_type=${this.subjectType}`;
      }

      api
        .get(query)
        .then((res) => {
          if (this.filtered && this.filtered.length > 0) {
            // Filter out subjects that are already added
            this.subjects = res.data.filter((subject: any) => {
              return !this.filtered.find((filterItem: any) => {
                return (
                  filterItem.subject.id === subject.id &&
                  filterItem.subject.subject_type === subject.subject_type
                );
              });
            });
          } else {
            // Show results straight if there is nothing to filter
            this.subjects = res.data;
          }

          if (this.subjects.length < 1) {
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
      this.$emit('subjectsAdded', this.selectedIds);
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

.first-column {
  width: 40px;
}
</style>

