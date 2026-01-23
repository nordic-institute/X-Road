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
  <XrdCard>
    <v-table class="xrd bg-surface-container" :loading="loading">
      <thead>
        <tr>
          <th>{{ $t('globalGroup.description') }}</th>
          <th></th>
        </tr>
      </thead>
      <tbody>
        <tr>
          <td data-test="global-group-description">
            {{ globalGroup.description || '' }}
          </td>
          <td class="align-end">
            <XrdBtn
              v-if="allowDescriptionEdit"
              class="float-right"
              color="tertiary"
              text="action.edit"
              variant="text"
              @click="showEditDescriptionDialog = true"
            />
          </td>
        </tr>
      </tbody>
    </v-table>
    <!-- Edit Description Dialog -->
    <EditGlobalGroupDescriptionDialog
      v-if="showEditDescriptionDialog"
      :group-code="globalGroup.code"
      :group-description="globalGroup.description"
      @save="
        globalGroup = $event;
        showEditDescriptionDialog = false;
      "
      @cancel="showEditDescriptionDialog = false"
    />
  </XrdCard>
</template>

<script lang="ts">
import { defineComponent } from 'vue';

import { mapActions, mapState, mapStores } from 'pinia';

import { useNotifications, XrdBtn, XrdCard } from '@niis/shared-ui';

import { Permissions } from '@/global';
import { GlobalGroupResource } from '@/openapi-types';
import { useGlobalGroups } from '@/store/modules/global-groups';
import { useUser } from '@/store/modules/user';

import EditGlobalGroupDescriptionDialog from './dialogs/EditGlobalGroupDescriptionDialog.vue';

/**
 * Global group view
 */
export default defineComponent({
  components: {
    EditGlobalGroupDescriptionDialog,
    XrdCard,
    XrdBtn,
  },
  props: {
    groupCode: {
      type: String,
      required: true,
    },
  },
  setup() {
    const { addError } = useNotifications();
    return { addError };
  },
  data() {
    return {
      globalGroup: {} as GlobalGroupResource,
      loading: false,
      showEditDescriptionDialog: false,
    };
  },
  computed: {
    ...mapStores(useGlobalGroups),
    ...mapState(useUser, ['hasPermission']),
    allowDescriptionEdit(): boolean {
      return this.hasPermission(Permissions.EDIT_GROUP_DESCRIPTION);
    },
    allowAddAndRemoveGroupMembers(): boolean {
      return this.hasPermission(Permissions.ADD_AND_REMOVE_GROUP_MEMBERS);
    },
  },
  watch: {
    groupCode: {
      immediate: true,
      handler(groupCode: string) {
        this.loading = true;
        this.globalGroupStore
          .getByCode(groupCode)
          .then((resp) => (this.globalGroup = resp))
          .catch((error) => this.addError(error))
          .finally(() => (this.loading = false));
      },
    },
  },
});
</script>
