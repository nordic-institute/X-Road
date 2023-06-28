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
<template>
  <div data-test="member-classes-list">
    <!-- Table -->
    <v-data-table
      :headers="headers"
      :items="memberClasses"
      :must-sort="true"
      sort-by="code"
      :items-per-page="itemsPerPage"
      class="elevation-0 data-table"
      item-key="code"
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
              data-test="system-settings-add-member-class-button"
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
            data-test="system-settings-edit-member-class-button"
            @click="openMemberClassDialog(item)"
          >
            {{ $t('action.edit') }}
          </xrd-button>

          <xrd-button
            text
            :outlined="false"
            data-test="system-settings-delete-member-class-button"
            @click="confirmDelete(item)"
          >
            {{ $t('action.delete') }}
          </xrd-button>
        </div>
      </template>
    </v-data-table>
    <xrd-confirm-dialog
      v-if="activeItem !== undefined"
      data-test="system-settings-member-class-delete-confirm-dialog"
      :dialog="confirmDeleteDialog"
      :loading="deletingMemberClass"
      title="action.confirm"
      text="systemSettings.deleteMemberClass"
      @cancel="cancelDelete"
      @accept="acceptDelete"
    />
    <xrd-simple-dialog
      :title="
        adding
          ? 'systemSettings.addMemberClassTitle'
          : 'systemSettings.editMemberClassTitle'
      "
      data-test="system-settings-member-class-edit-dialog"
      :dialog="memberClassDialog"
      :scrollable="false"
      :show-close="true"
      :loading="savingMemberClass"
      save-button-text="action.save"
      :disable-save="!valid"
      @save="onSaveMemberClass"
      @cancel="memberClassDialog = false"
    >
      <div slot="content">
        <div class="pt-4 dlg-input-width">
          <v-form
            v-if="activeItem !== undefined"
            ref="form"
            v-model="valid"
            lazy-validation
            ><ValidationProvider
              v-slot="{ errors }"
              ref="code"
              rules="required"
              name="code"
              class="validation-provider"
            >
              <v-text-field
                v-model="activeItem.code"
                data-test="system-settings-member-class-code-edit-field"
                :disabled="!adding"
                :label="$t('systemSettings.code')"
                :rules="fieldRules"
                :autofocus="adding"
                outlined
                class="dlg-row-input"
                name="code"
                :error-messages="errors"
              ></v-text-field>
            </ValidationProvider>
            <v-text-field
              v-model="activeItem.description"
              data-test="system-settings-member-class-description-edit-field"
              :label="$t('systemSettings.description')"
              :rules="fieldRules"
              :autofocus="!adding"
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
import Vue, { VueConstructor } from 'vue';
import { ErrorInfo, MemberClass } from '@/openapi-types';
import { mapStores } from 'pinia';
import { notificationsStore } from '@/store/modules/notifications';
import { useMemberClassStore } from '@/store/modules/member-class';
import { DataTableHeader } from 'vuetify';
import {
  getErrorInfo,
  getTranslatedFieldErrors,
  isFieldError,
} from '@/util/helpers';
import { AxiosError } from 'axios';
import { ValidationProvider } from 'vee-validate';

export default (
  Vue as VueConstructor<
    Vue & {
      $refs: {
        code: InstanceType<typeof ValidationProvider>;
      };
    }
  >
).extend({
  components: { ValidationProvider },
  data: () => ({
    deletingMemberClass: false,
    savingMemberClass: false,
    confirmDeleteDialog: false,
    memberClassDialog: false,
    activeItem: undefined as undefined | MemberClass,
    adding: false,
    valid: true,
  }),
  computed: {
    ...mapStores(useMemberClassStore, notificationsStore),
    memberClasses() {
      return this.memberClassStore.memberClasses;
    },
    fieldRules() {
      return [
        (v: string) =>
          (v.length <= 255 && v.length > 0) || this.$t('validationError.Size'),
      ];
    },
    itemsPerPage() {
      return this.memberClassStore.memberClasses.length > 5 ? 5 : -1;
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
      this.deletingMemberClass = true;
      if (this.activeItem !== undefined) {
        try {
          await this.memberClassStore.delete(this.activeItem);
          this.notificationsStoreStore.showSuccess(
            this.$t('systemSettings.memberClassDeleted'),
          );
        } catch (error: unknown) {
          this.notificationsStoreStore.showError(error);
        }
        this.activeItem = undefined;
      }
      this.confirmDeleteDialog = false;
      this.deletingMemberClass = false;
    },
    cancelDelete() {
      this.confirmDeleteDialog = false;
      this.activeItem = undefined;
    },
    async onSaveMemberClass() {
      if (this.activeItem !== undefined) {
        this.savingMemberClass = true;
        try {
          await (this.adding
            ? this.memberClassStore.add(this.activeItem)
            : this.memberClassStore.update(
                this.activeItem.code,
                this.activeItem.description,
              ));
          this.notificationsStoreStore.showSuccess(
            this.$t('systemSettings.memberClassSaved'),
          );
        } catch (error: unknown) {
          const errorInfo: ErrorInfo = getErrorInfo(error as AxiosError);
          if (isFieldError(errorInfo)) {
            let fieldErrors = errorInfo.error?.validation_errors;
            if (fieldErrors && this.$refs?.code) {
              this.$refs.code.setErrors(
                getTranslatedFieldErrors('memberClassDto.code', fieldErrors),
              );
              return;
            }
          } else {
            this.notificationsStoreStore.showError(error);
          }
        } finally {
          this.savingMemberClass = false;
        }
      }
      this.activeItem = undefined;
      this.memberClassDialog = false;
    },
    openMemberClassDialog(item: MemberClass | undefined) {
      if (item === undefined) {
        this.activeItem = {
          code: '',
          description: '',
        };
        this.adding = true;
      } else {
        this.activeItem = { ...item };
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
