<template>
  <v-dialog :value="dialog" width="750" scrollable persistent>
    <v-card class="xroad-card">
      <v-card-title>
        <span class="headline">{{$t('localGroup.addMembers')}}</span>
        <v-spacer/>
        <i @click="cancel()" id="close-x"></i>
      </v-card-title>
      <v-card-text style="height: 500px;">
        <v-expansion-panel class="elevation-0" expand v-model="expandPanel">
          <v-expansion-panel-content value="true">
            <template v-slot:header>
              <v-spacer/>
              <div class="exp-title">{{$t('localGroup.searchOptions')}}</div>
            </template>

            <div>
              <div class="flex-wrap">
                <div class="input-row">
                  <v-text-field
                    v-model="name"
                    label="Name"
                    single-line
                    hide-details
                    class="flex-input"
                  ></v-text-field>

                  <v-select
                    v-model="instance"
                    :items="instances"
                    label="Instance"
                    class="flex-input"
                  ></v-select>
                </div>

                <div class="input-row">
                  <v-select
                    v-model="memberClass"
                    :items="instances"
                    label="Member class"
                    class="flex-input"
                  ></v-select>
                  <v-text-field
                    v-model="memberCode"
                    label="Member code"
                    single-line
                    hide-details
                    class="flex-input"
                  ></v-text-field>
                </div>
                <v-text-field
                  v-model="subsystemCode"
                  label="Subsystem code"
                  single-line
                  hide-details
                  class="flex-input"
                ></v-text-field>
              </div>

              <div class="search-wrap">
                <v-btn
                  color="primary"
                  round
                  class="mb-2 rounded-button elevation-0 xr-big-button"
                  @click="search()"
                >{{$t('action.search')}}</v-btn>
              </div>
            </div>
          </v-expansion-panel-content>
        </v-expansion-panel>

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
                  <v-checkbox
                    @change="checkboxChange(member.id, $event)"
                    color="primary"
                    class="table-checkbox"
                  ></v-checkbox>
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
      <v-card-actions class="xr-card-actions">
        <v-spacer></v-spacer>
        <v-btn
          color="primary"
          round
          outline
          class="mb-2 rounded-button elevation-0 xr-big-button button-margin"
          @click="cancel()"
        >{{$t('localGroup.cancel')}}</v-btn>

        <v-btn
          color="primary"
          round
          class="mb-2 rounded-button elevation-0 xr-big-button button-margin"
          :disabled="!canSave"
          @click="save()"
        >{{$t('localGroup.addSelected')}}</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script lang="ts">
import Vue from 'vue';
import axios from 'axios';

function initialState() {
  return {
    name: '',
    instance: '',
    memberClass: '',
    memberCode: '',
    subsystemCode: '',
    instances: [],
    expandPanel: [true],
    members: [],
    selectedIds: [] as any,
    noResults: false,
  };
}

export default Vue.extend({
  props: {
    groupId: {
      type: String,
      required: true,
    },
    dialog: {
      type: Boolean,
      required: true,
    },
  },

  data() {
    return initialState();
  },
  computed: {
    canSave() {
      if (this.selectedIds.length > 0) {
        return true;
      }
      return false;
    },
  },

  methods: {
    checkboxChange(id: any, event: any): void {
      if (event === true) {
        this.selectedIds.push(id);
      } else {
        this.selectedIds.pop(id);
      }
    },
    search(): void {
      this.noResults = false;
      axios
        .get(
          `/clients?name=${this.name}&instance=${this.instance}&member_class=${
            this.memberClass
          }&member_code=${this.memberCode}&subsystem_code=${
            this.subsystemCode
          }&show_members=false&internal_search=false`,
        )
        .then((res) => {
          this.members = res.data;
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
      axios
        .post(`/groups/${this.groupId}/members`, {
          items: this.selectedIds,
        })
        .then((res) => {
          this.clearForm();
          this.$emit('membersAdded');
        })
        .catch((error) => {
          this.$bus.$emit('show-error', error.message);
        });
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

.empty-wrap {
  width: 100%;
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
</style>

