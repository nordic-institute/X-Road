<template>
  <v-dialog :value="dialog" width="750" scrollable persistent>
    <v-card class="xrd-card">
      <v-card-title>
        <span class="headline">{{$t(title)}}</span>
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
                      :items="instances"
                      label="Instance"
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
              <th></th>
              <th>{{$t('name')}}</th>
              <th>{{$t('localGroup.id')}}</th>
            </tr>
          </thead>
          <tbody v-if="members && members.length > 0">
            <tr v-for="member in members" v-bind:key="member.id">
              <td>
                <div class="checkbox-wrap">
                  <v-checkbox @change="checkboxChange(member.id, $event)" color="primary"></v-checkbox>
                </div>
              </td>

              <td>{{member.member_name}}</td>
              <td>{{member.id}}</td>
            </tr>
          </tbody>
        </table>
        <div v-if="members.length < 1 && !noResults" class="empty-row"></div>

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
import LargeButton from '@/components/LargeButton.vue';

function initialState() {
  return {
    name: '',
    instance: '',
    memberClass: '',
    memberCode: '',
    subsystemCode: '',
    expandPanel: [0],
    members: [],
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
    filtered: {
      type: Array,
    },
    title: {
      type: String,
      default: 'localGroup.addMembers',
    },
    instances: {
      type: Array,
      required: true,
    },
    memberClasses: {
      type: Array,
      required: true,
    },
  },

  data() {
    return initialState();
  },
  computed: {
    canSave(): boolean {
      if (this.selectedIds.length > 0) {
        return true;
      }
      return false;
    },
  },

  methods: {
    checkboxChange(id: string, event: any): void {
      if (event === true) {
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
      api
        .get(
          `/clients?name=${this.name}&instance=${this.instance}&member_class=${this.memberClass}&member_code=${this.memberCode}&subsystem_code=${this.subsystemCode}&show_members=false&internal_search=false`,
        )
        .then((res) => {
          if (this.filtered && this.filtered.length > 0) {
            // Filter out members that are already added
            this.members = res.data.filter((member: any) => {
              this.filtered.find((item: any) => {
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
          this.$bus.$emit('show-error', error.message);
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
});
</script>

<style lang="scss" scoped>
@import '../assets/colors';
@import '../assets/tables';

.exp-title {
  text-align: right;
  padding-right: 20px;
}

.input-row {
  display: flex;
  width: 100%;
  justify-content: space-between;
  padding-right: 10px;
}

.flex-wrap {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  justify-content: flex-start;
}

.flex-input {
  margin: 4px;
  max-width: 310px;
}

.search-wrap {
  width: 100%;
  display: flex;
  justify-content: flex-end;
}

.button-margin {
  margin-right: 14px;
}

.empty-row {
  padding: 10px;
  width: 100%;
  border-bottom: $XRoad-Grey10 solid 1px;
  height: 48px;
  text-align: center;
}

.fixed_header {
  table-layout: fixed;
  border-collapse: collapse;
}

.table-checkbox {
  height: 38px;
  padding: 0;
  margin-top: 12px;
}

#close-x {
  font-family: Roboto;
  font-size: 34px;
  font-weight: 300;
  letter-spacing: 0.5px;
  line-height: 21px;

  cursor: pointer;
  font-style: normal;
  font-size: 50px;
  color: $XRoad-Grey40;
}

#close-x:before {
  content: '\00d7';
}

// Override vuetify default box-shadow
.v-expansion-panel::before {
  box-shadow: none;
}
</style>

