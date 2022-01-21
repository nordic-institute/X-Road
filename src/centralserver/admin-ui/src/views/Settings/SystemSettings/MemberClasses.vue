<template>
  <div>
    <!-- Table -->
    <v-data-table
      :headers="headers"
      :items="memberClassStore.memberClasses"
      :must-sort="true"
      :items-per-page="5"
      class="elevation-0 data-table"
      item-key="id"
      :loader-height="2"
      :no-data-text="$t('noData.noMemberClasses')"
    >
      <template #top>
        <div class="card-top">
          <div class="card-main-title">
            {{ $t('systemSettings.memberClasses') }}
          </div>
          <div class="card-corner-button">
            <xrd-button
              outlined
              class="mr-4"
              @click="openMemberClassDialog(undefined)"
            >
              <xrd-icon-base class="xrd-large-button-icon">
                <XrdIconAdd />
              </xrd-icon-base>
              {{ $t('action.add') }}
            </xrd-button>
          </div>
        </div>
      </template>

      <template #[`item.button`]="{ item }">
        <div class="cs-table-actions-wrap">
          <xrd-button
            text
            :outlined="false"
            @click="openMemberClassDialog(item)"
          >
            {{ $t('action.edit') }}
          </xrd-button>

          <xrd-button text :outlined="false" @click="confirmDelete(item)">
            {{ $t('action.delete') }}
          </xrd-button>
        </div>
      </template>
    </v-data-table>
    <xrd-confirm-dialog
      data-test="system-parameters-member-class-delete-confirm-dialog"
      :dialog="confirmDeleteDialog"
      :loading="activeItem === undefined"
      :title="$t('action.confirm')"
      text="Delete member class?"
      @cancel="cancelDelete"
      @accept="acceptDelete"
    />
    <xrd-simple-dialog
      title="systemSettings.editMemberClassTitle"
      data-test="system-settings-member-class-dialog"
      :dialog="memberClassDialog"
      :scrollable=false
      :show-close=true
      save-button-text="action.save"
      :disableSave="!valid"
      @save="onSaveMemberClass"
      @cancel="memberClassDialog = false"
    >
      <div slot="content">
        <div class="pt-4 dlg-input-width">
          <v-form
            v-if="activeItem !== undefined"
            ref="form"
            v-model="valid"
            lazy-validation>
            <v-text-field
              v-model="activeItem.code"
              data-test="system-settings-member-class-code-edit-field"
              :disabled="!adding"
              :label="$t('systemSettings.code')"
              :rules="fieldRules"
              autofocus
              outlined
              class="dlg-row-input"
              name="code"
            ></v-text-field>
            <v-text-field
              v-model="activeItem.description"
              data-test="system-settings-member-class-description-edit-field"
              :label="$t('systemSettings.description')"
              :rules="fieldRules"
              outlined
              class="dlg-row-input"
              name="memberClass"
            ></v-text-field>
          </v-form>
        </div>
      </div>
    </xrd-simple-dialog>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { MemberClass } from '@/openapi-types';
import { mapStores } from 'pinia';
import { notificationsStore } from '@/store/modules/notifications';
import { memberClassStore } from '@/store/modules/member-class';
import { DataTableHeader } from 'vuetify';

export default Vue.extend({
  data: () => ({
    confirmDeleteDialog: false,
    memberClassDialog: false,
    activeItem: undefined as undefined | MemberClass,
    adding : false,
    valid: true,
  }),
  computed: {
    ...mapStores(memberClassStore, notificationsStore),
    fieldRules() {
      return [(v : string) => v.length <= 255 && v.length > 0];
    },
    headers(): DataTableHeader[] {
      return [
        {
          text: this.$t('systemSettings.code') as string,
          align: 'start',
          value: 'code',
          class: 'xrd-table-header member-classes-table-header-code',
        },
        {
          text: this.$t('systemSettings.description') as string,
          align: 'start',
          value: 'description',
          class: 'xrd-table-header member-classes-table-header-description',
        },
        {
          text: '',
          value: 'button',
          sortable: false,
          class: 'xrd-table-header member-classes-table-header-buttons',
        },
      ];
    },
  },
  created() {
    this.memberClassStore.fetchAll();
  },
  methods: {
    confirmDelete(item: MemberClass) {
      this.confirmDeleteDialog = true;
      this.activeItem = item;
    },
    async acceptDelete() {
      if (this.activeItem !== undefined) {
        await this.memberClassStore.delete(this.activeItem).catch((error) => {
          this.notificationsStoreStore.showError(error);
        });
        this.activeItem = undefined;
        await this.memberClassStore.fetchAll();
      }
      this.confirmDeleteDialog = false;
    },
    cancelDelete() {
      this.confirmDeleteDialog = false;
      this.activeItem = undefined;
    },
    async onSaveMemberClass() {
      if (this.activeItem !== undefined) {
        const action = this.adding ?
          this.memberClassStore.add(this.activeItem) :
          this.memberClassStore.update(this.activeItem);
        await action.catch((error) => {
          this.notificationsStoreStore.showError(error);
        });
      }
      this.activeItem = undefined;
      this.memberClassDialog = false;
      return this.memberClassStore.fetchAll();
    },
    openMemberClassDialog(item: MemberClass | undefined ) {
      if (item === undefined) {
        this.activeItem = { code : "", description : "" }
        this.adding = true;
      } else {
        this.activeItem = {...item};
        this.adding = false;
      }
      this.memberClassDialog = true;
    },
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/tables';

.server-code {
  color: $XRoad-Purple100;
  font-weight: 600;
  font-size: 14px;
}

.align-fix {
  align-items: center;
}

.custom-footer {
  border-top: thin solid rgba(0, 0, 0, 0.12); /* Matches the color of the Vuetify table line */
  height: 16px;
}

.card-corner-button {
  display: flex;
}

.card-top {
  padding-top: 15px;
  margin-bottom: 10px;
  width: 100%;
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: space-between;
}

.title-cell {
  max-width: 40%;
  width: 40%;
}

.action-cell {
  text-align: right;
  width: 100px;
}

.card-main-title {
  color: $XRoad-Black100;
  font-style: normal;
  font-weight: bold;
  font-size: 18px;
  line-height: 24px;
  margin-left: 16px;
}
</style>
