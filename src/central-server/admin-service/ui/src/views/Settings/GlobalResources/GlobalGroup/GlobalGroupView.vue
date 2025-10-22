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
  <XrdView
    title-details="globalGroup.title"
    translated-title
    :title="title"
    :breadcrumbs
  >
    <template #append-header>
      <v-spacer />
      <XrdBtn
        v-if="allowGroupDelete"
        data-test="remove-group-button"
        variant="outlined"
        prepend-icon="delete_forever"
        text="globalGroup.deleteGroup"
        @click="showDeleteGroupDialog = true"
      />
    </template>

    <GlobalGroupDetails class="mb-4" :group-code="groupCode" />

    <GlobalGroupMembers :group-code="groupCode" />

    <DeleteGlobalGroupDialog
      v-if="showDeleteGroupDialog"
      :group-code="groupCode"
      @cancel="showDeleteGroupDialog = false"
    />
  </XrdView>
</template>

<script lang="ts">
import { defineComponent } from 'vue';

import { mapState } from 'pinia';

import { XrdBtn, XrdView } from '@niis/shared-ui';

import { Permissions, RouteName } from '@/global';
import { useUser } from '@/store/modules/user';

import GlobalGroupDetails from './GlobalGroupDetails.vue';
import GlobalGroupMembers from './GlobalGroupMembers.vue';
import DeleteGlobalGroupDialog from '@/views/Settings/GlobalResources/GlobalGroup/dialogs/DeleteGlobalGroupDialog.vue';

/**
 * Global group view
 */
export default defineComponent({
  components: {
    DeleteGlobalGroupDialog,
    XrdView,
    GlobalGroupMembers,
    GlobalGroupDetails,
    XrdBtn,
  },
  props: {
    groupCode: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      showDeleteGroupDialog: false,
      breadcrumbs: [
        {
          title: 'tab.main.settings',
          to: {
            name: RouteName.GlobalGroups,
          },
        },
      ],
    };
  },
  computed: {
    ...mapState(useUser, ['hasPermission']),
    allowGroupDelete(): boolean {
      return this.hasPermission(Permissions.DELETE_GROUP);
    },
    title() {
      return this.groupCode;
    },
  },
});
</script>

<style lang="scss" scoped></style>
