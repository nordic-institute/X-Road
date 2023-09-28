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
  <titled-view :title="globalGroup.code" :loading="loading" :data="globalGroup">
    <template #header-buttons>
      <xrd-button
        v-if="allowGroupDelete"
        data-test="remove-group-button"
        outlined
        @click="showDeleteGroupDialog = true"
      >
        <v-icon
          class="xrd-large-button-icon"
          size="x-large"
          icon="mdi-close-circle"
        />
        {{ $t('globalGroup.deleteGroup') }}
      </xrd-button>
    </template>

    <info-card
      v-if="!loading"
      data-test="global-group-description"
      :title-text="$t('globalGroup.description')"
      :info-text="globalGroup.description || ''"
      :action-text="$t('action.edit')"
      :show-action="allowDescriptionEdit"
      @action-clicked="showEditDescriptionDialog = true"
    />

    <!-- Edit Description Dialog -->
    <global-group-edit-description-dialog
      v-if="showEditDescriptionDialog"
      :group-code="globalGroup.code"
      :group-description="globalGroup.description"
      @edit="
        globalGroup = $event;
        showEditDescriptionDialog = false;
      "
      @cancel="showEditDescriptionDialog = false"
    />

    <!-- Delete Group Dialog -->
    <global-group-delete-dialog
      v-if="showDeleteGroupDialog"
      :group-code="globalGroup.code"
      @cancel="showDeleteGroupDialog = false"
    />
  </titled-view>
</template>

<script lang="ts">
import { defineComponent } from 'vue';

import { Colors, Permissions } from '@/global';
import InfoCard from '@/components/ui/InfoCard.vue';
import { useGlobalGroups } from '@/store/modules/global-groups';
import { mapActions, mapState, mapStores } from 'pinia';
import { GlobalGroupResource } from '@/openapi-types';
import { useNotifications } from '@/store/modules/notifications';
import { useUser } from '@/store/modules/user';
import GlobalGroupDeleteDialog from './GlobalGroupDeleteDialog.vue';
import GlobalGroupEditDescriptionDialog from './GlobalGroupEditDescriptionDialog.vue';
import TitledView from '@/components/ui/TitledView.vue';

/**
 * Global group view
 */
export default defineComponent({
  components: {
    TitledView,
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
      loading: false,
      showDeleteGroupDialog: false,
      showEditDescriptionDialog: false,
    };
  },
  computed: {
    ...mapStores(useGlobalGroups),
    ...mapState(useUser, ['hasPermission']),
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
    ...mapActions(useNotifications, ['showError']),
  },
});
</script>

<style lang="scss" scoped>
@import '@/assets/colors';
@import '@/assets/tables';
</style>
