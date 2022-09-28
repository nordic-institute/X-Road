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
<!--
  Member details view
-->
<template>
  <main id="member-details-content">
    <!-- Member Details -->
    <div id="member-details">
      <info-card
        :title-text="$t('global.memberName')"
        :action-text="$t('action.edit')"
        :show-action="allowMemberRename"
        :info-text="member.member_name || ''"
        @actionClicked="editMemberName"
      />

      <info-card
        :title-text="$t('global.memberClass')"
        :info-text="member.xroad_id.member_class || ''"
      />

      <info-card
        :title-text="$t('global.memberCode')"
        :info-text="member.xroad_id.member_code || ''"
      />
    </div>

    <!-- Owned Servers -->
    <div id="owned-servers">
      <div class="xrd-title-search mb-8">
        <div class="xrd-view-title">
          {{ $t('members.member.details.ownedServers') }}
        </div>
        <xrd-search v-model="searchServers" data-test="search-owned-servers" />
      </div>

      <v-card flat>

        <!-- Table -->
        <v-data-table
          :loading="loading"
          :headers="serversHeaders"
          :items="servers"
          :search="searchServers"
          :must-sort="true"
          :items-per-page="-1"
          class="elevation-0 data-table"
          item-key="id"
          :loader-height="2"
          hide-default-footer
        >
          <template #footer>
            <div class="cs-table-custom-footer"></div>
          </template>
        </v-data-table>
      </v-card>
    </div>

    <!-- Global Groups -->
    <div id="global-groups">
      <div class="xrd-title-search mt-8 mb-8">
        <div class="xrd-view-title">
          {{ $t('members.member.details.globalGroups') }}
        </div>
        <xrd-search v-model="searchGroups" data-test="search-global-groups" />
      </div>

      <v-card flat>

        <!-- Table -->
        <v-data-table
          :loading="loadingGroups"
          :headers="groupsHeaders"
          :items="globalGroups"
          :search="searchGroups"
          :must-sort="true"
          :items-per-page="-1"
          class="elevation-0 data-table"
          item-key="group_code"
          :loader-height="2"
          hide-default-footer
        >
          <template #[`item.added_to_group`]="{ item }">
            {{ item.added_to_group | formatDateTimeWithSeconds }}
          </template>
          <template #footer>
            <div class="cs-table-custom-footer"></div>
          </template>
        </v-data-table>
      </v-card>

      <div class="delete-action" @click="showDeleteDialog = true" v-if="allowMemberDelete">
        <div>
          <v-icon class="xrd-large-button-icon" :color="colors.Purple100">mdi-close-circle</v-icon>
        </div>
        <div class="action-text">
          {{ $t('members.member.details.deleteMember') }} "{{ member.member_name || '' }}"
        </div>
      </div>
    </div>

    <!-- Edit member name dialog -->
    <xrd-simple-dialog
      :dialog="showEditNameDialog"
      title="members.member.details.editMemberName"
      save-button-text="action.save"
      cancel-button-text="action.cancel"
      :disable-save="newMemberName === '' || newMemberName === this.member.member_name"
      @cancel="cancelEditMemberName"
      @save="saveNewMemberName">

      <template #content>
        <div class="dlg-input-width">
          <v-text-field
            v-model="newMemberName"
            outlined
          ></v-text-field>
        </div>
      </template>
    </xrd-simple-dialog>

    <!-- Delete member - Check member code dialog -->
    <v-dialog v-if="showDeleteDialog" v-model="showDialog" width="500" persistent>
      <ValidationObserver ref="initializationForm" v-slot="{ invalid }">
        <v-card class="xrd-card">
          <v-card-title>
            <span class="headline">
              {{ $t('members.member.details.deleteMember') }}</span>
          </v-card-title>
          <v-card-text class="pt-4">
            {{
              $t('members.member.details.areYouSure1', {
                member: member.member_name,
              })
            }}
            <div class="dlg-input-width pt-4">
              <ValidationProvider
                v-slot="{ errors }"
                ref="initializationParamsVP"
                name="init.identifier"
                :rules="{ required: true, is: member.xroad_id.member_code }"
                data-test="instance-identifier--validation"
              >
                <v-text-field
                  v-model="offeredCode"
                  outlined
                  :label="$t('members.member.details.enterCode')"
                  autofocus
                  data-test="add-local-group-code-input"
                  :error-messages="errors"
                ></v-text-field>
              </ValidationProvider>
            </div>
          </v-card-text>
          <v-card-actions class="xrd-card-actions">
            <v-spacer></v-spacer>
            <xrd-button outlined @click="cancelDelete()">
              {{ $t('action.cancel') }}
            </xrd-button>
            <xrd-button :disabled="invalid" @click="deleteMember()">
              {{ $t('action.delete') }}
            </xrd-button>
          </v-card-actions>
        </v-card>
      </ValidationObserver>
    </v-dialog>
  </main>
</template>

<script lang="ts">
import Vue from 'vue';
import { DataTableHeader } from 'vuetify';
import { Colors, Permissions, RouteName } from '@/global';
import { ValidationObserver, ValidationProvider } from 'vee-validate';
import InfoCard from "@/components/ui/InfoCard.vue";
import { mapActions, mapState, mapStores } from "pinia";
import { memberStore } from "@/store/modules/members";
import { Client, ClientId, MemberGlobalGroup, SecurityServer } from "@/openapi-types";
import { notificationsStore } from "@/store/modules/notifications";
import { userStore } from "@/store/modules/user";

let that: any;

/**
 * Component for a Members details view
 */
export default Vue.extend({
  name: 'MemberDetails',
  components: {
    InfoCard,
    ValidationObserver,
    ValidationProvider,
  },
  props: {
    memberid: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      colors: Colors,

      loading: false,
      member: {
        xroad_id: {} as ClientId
      } as Client,

      showEditNameDialog: false,
      newMemberName: '',

      showDeleteDialog: false,
      offeredCode: '',

      loadingServers: false,
      searchServers: '',
      servers: [] as SecurityServer[],

      loadingGroups: false,
      searchGroups: '',
      globalGroups: [] as MemberGlobalGroup[],
    };
  },
  computed: {
    ...mapState(userStore, ['hasPermission']),
    ...mapStores(memberStore),
    allowMemberDelete(): boolean {
      return this.hasPermission(Permissions.DELETE_MEMBER);
    },
    allowMemberRename() : boolean {
      return this.hasPermission(Permissions.EDIT_MEMBER_NAME_AND_ADMIN_CONTACT);
    },
    serversHeaders(): DataTableHeader[] {
      return [
        {
          text: this.$t('global.server') as string,
          align: 'start',
          value: 'xroad_id.server_code',
          class: 'xrd-table-header servers-table-header-server',
        },
      ];
    },
    groupsHeaders(): DataTableHeader[] {
      return [
        {
          value: 'group_code',
          text: this.$t('members.member.details.group') as string,
          align: 'start',
          class: 'xrd-table-header groups-table-header-group',
        },
        {
          value: 'subsystem',
          text: this.$t('global.subsystem') as string,
          align: 'start',
          class: 'xrd-table-header groups-table-header-subsystem',
        },
        {
          value: 'added_to_group',
          text: this.$t('members.member.details.addedToGroup') as string,
          align: 'start',
          class: 'xrd-table-header groups-table-header-added',
        },
      ];
    },
  },
  created() {
    that = this;
    this.loading = true;
    this.memberStore
      .getById(this.memberid)
      .then((resp) => {
        this.member = resp;
      })
      .catch((error) => {
        this.showError(error);
      })
      .finally(() => {
        this.loading = false;
      });

    this.loadingGroups = true;
    this.memberStore
      .getMemberGlobalGroups(this.memberid)
      .then((resp) => {
        this.globalGroups = resp;
      })
      .catch((error) => {
        this.showError(error);
      })
      .finally(() => {
        this.loadingGroups = false;
      });

    this.loadingServers= true;
    this.memberStore
      .getMemberOwnedServers(this.memberid)
      .then((resp) => {
        this.servers = resp;
      })
      .catch((error) => {
        this.showError(error);
      })
      .finally(() => {
        this.loadingServers = false;
      });
  },
  methods: {
    ...mapActions(notificationsStore, ['showError', 'showSuccess']),
    editMemberName() {
      this.newMemberName = this.member.member_name || '';
      this.showEditNameDialog = true;
    },
    cancelEditMemberName() {
      this.showEditNameDialog = false;
    },
    saveNewMemberName() {
      this.memberStore
        .editMemberName(this.memberid, { member_name: this.newMemberName})
        .then((resp) => {
          this.member = resp.data;
          this.showSuccess(this.$t('members.member.details.memberNameSaved'));
        })
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => {
          this.showEditNameDialog = false;
        });
    },
    deleteMember() {
      this.memberStore
        .deleteById(this.memberid)
        .then(() => {
          this.$router.replace({ name: RouteName.Members });
          this.showSuccess(this.$t('members.member.details.memberDeleted'));
        })
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => {
          this.showDeleteDialog = false;
          this.offeredCode = '';
        });
    },
    cancelDelete() {
      this.showDeleteDialog = false;
      this.offeredCode = '';
    },
  },

});
</script>

<style lang="scss" scoped>
@import '~styles/colors';
@import '~styles/tables';

.card-title {
  font-size: 12px;
  text-transform: uppercase;
  color: $XRoad-Black70;
  font-weight: bold;
  padding-top: 5px;
  padding-bottom: 5px;
}

.card-corner-button {
  display: flex;
  justify-content: flex-end;
}

.delete-action {
  margin-top: 34px;
  color: $XRoad-Link;
  cursor: pointer;
  display: flex;
  flex-direction: row;
  .action-text {
    margin-top: 2px;
  }
}

#member-details {
  margin-top: 24px;
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: flex-end;

  margin-bottom: 24px;

  .details-card {
    width: 100%;

    &:first-child {
      margin-right: 30px;
    }

    &:last-child {
      margin-left: 30px;
    }
  }
}

#global-groups-table {
  tbody tr td:last-child {
    width: 50px;
  }
}
</style>
