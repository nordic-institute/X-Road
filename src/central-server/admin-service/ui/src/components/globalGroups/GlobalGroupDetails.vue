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
  <section>
    <header class="header-row">
      <div class="title-search">
        <div class="xrd-view-title">{{ globalGroup.code }}</div>
      </div>
      <xrd-button
        v-if="allowGroupDelete"
        data-test="remove-group-button"
        outlined
        @click="showDeleteGroupDialog = true"
      >
        <v-icon class="xrd-large-button-icon">mdi-close-circle</v-icon>
        {{ $t('globalGroup.deleteGroup') }}
      </xrd-button>
    </header>

    <xrd-empty-placeholder
      :loading="loading"
      :data="globalGroup"
      :no-items-text="$t('noData.noData')"
      skeleton-type="table-heading"
    />

    <info-card
      v-if="!loading"
      data-test="global-group-description"
      :title-text="$t('globalGroup.description')"
      :info-text="globalGroup.description || ''"
      :action-text="$t('action.edit')"
      :show-action="allowDescriptionEdit"
      @actionClicked="showEditDescriptionDialog = true"
    />

    <!-- Edit Description Dialog -->
    <global-group-edit-description-dialog
      v-if="showEditDescriptionDialog"
      :show-dialog="showEditDescriptionDialog"
      :group-code="globalGroup.code"
      :group-description="globalGroup.description"
      @edit="editDescription"
      @cancel="cancelEdit"
    />

    <!-- Delete Group Dialog -->
    <global-group-delete-dialog
      v-if="showDeleteGroupDialog"
      :show-dialog="showDeleteGroupDialog"
      :group-code="globalGroup.code"
      @delete="deleteGlobalGroup"
      @cancel="cancelDelete"
    />
  </section>
</template>

<script lang="ts">
import Vue from 'vue';

import { Colors, Permissions, RouteName } from '@/global';
import { DataOptions } from 'vuetify';
import InfoCard from '@/components/ui/InfoCard.vue';
import { useGlobalGroupsStore } from '@/store/modules/global-groups';
import { mapActions, mapState, mapStores } from 'pinia';
import { GlobalGroupResource } from '@/openapi-types';
import { notificationsStore } from '@/store/modules/notifications';
import { userStore } from '@/store/modules/user';
import GlobalGroupDeleteDialog from './GlobalGroupDeleteDialog.vue';
import GlobalGroupEditDescriptionDialog from './GlobalGroupEditDescriptionDialog.vue';

/**
 * Global group view
 */
export default Vue.extend({
  components: {
    GlobalGroupEditDescriptionDialog,
    GlobalGroupDeleteDialog,
    InfoCard,
  },
  props: {
    groupCode: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      colors: Colors,
      globalGroup: {} as GlobalGroupResource,
      pagingSortingOptions: {} as DataOptions,
      loading: false,
      showAddDialog: false,
      showDeleteGroupDialog: false,
      showEditDescriptionDialog: false,
    };
  },
  computed: {
    ...mapStores(useGlobalGroupsStore),
    ...mapState(userStore, ['hasPermission']),
    allowDescriptionEdit(): boolean {
      return this.hasPermission(Permissions.EDIT_GROUP_DESCRIPTION);
    },
    allowGroupDelete(): boolean {
      return this.hasPermission(Permissions.DELETE_GROUP);
    },
    allowAddAndRemoveGroupMembers(): boolean {
      return this.hasPermission(Permissions.ADD_AND_REMOVE_GROUP_MEMBERS);
    },
  },
  created() {
    this.loading = true;
    this.globalGroupStore
      .getByCode(this.groupCode)
      .then((resp) => {
        this.globalGroup = resp;
      })
      .catch((error) => {
        this.showError(error);
      })
      .finally(() => {
        this.loading = false;
      });
  },
  methods: {
    ...mapActions(notificationsStore, ['showError', 'showSuccess']),
    cancelDelete(): void {
      this.showDeleteGroupDialog = false;
    },
    cancelEdit(): void {
      this.showEditDescriptionDialog = false;
    },
    deleteGlobalGroup(): void {
      this.globalGroupStore
        .deleteByCode(this.groupCode)
        .then(() => {
          this.$router.replace({ name: RouteName.GlobalResources });
          this.showSuccess(this.$t('globalGroup.groupDeletedSuccessfully'));
        })
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => {
          this.showDeleteGroupDialog = false;
        });
    },
    editDescription(newDescription: string): void {
      this.globalGroupStore
        .editGroupDescription(this.groupCode, { description: newDescription })
        .then((resp) => {
          this.globalGroup = resp.data;
          this.showSuccess(this.$t('globalGroup.descriptionSaved'));
        })
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => {
          this.showEditDescriptionDialog = false;
        });
    },
  },
});
</script>

<style lang="scss" scoped>
@import '~styles/colors';
@import '~styles/tables';
</style>
