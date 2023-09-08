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
  <xrd-confirm-dialog
    :loading="loading"
    accept-button-text="action.yes"
    title="globalGroup.deleteGroup"
    text="globalGroup.areYouSure"
    :data="{ group: groupCode }"
    @save="proceedWithDelete"
    @cancel="cancelDelete"
  />
</template>

<script lang="ts">
/** Base component for simple dialogs */

import { defineComponent } from 'vue';
import { XrdConfirmDialog } from '@niis/shared-ui';
import { Event } from '@/ui-types';
import { RouteName } from '@/global';
import { mapActions, mapStores } from 'pinia';
import { useGlobalGroups } from '@/store/modules/global-groups';
import { useNotifications } from '@/store/modules/notifications';

export default defineComponent({
  components: { XrdConfirmDialog },
  props: {
    groupCode: {
      type: String,
      required: true,
    },
  },
  emits: [Event.Delete, Event.Cancel],
  data() {
    return {
      loading: false,
    };
  },
  computed: {
    ...mapStores(useGlobalGroups, useNotifications),
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    cancelDelete(): void {
      this.$emit(Event.Cancel);
    },
    proceedWithDelete(): void {
      this.loading = true;
      this.globalGroupStore
        .deleteByCode(this.groupCode)
        .then(() => {
          this.showSuccess(
            this.$t('globalGroup.groupDeletedSuccessfully'),
            true,
          );
          this.$router.replace({ name: RouteName.GlobalResources });
        })
        .catch((error) => {
          this.showError(error);
        })
        .finally(() => {
          this.loading = false;
        });
    },
  },
});
</script>

<style lang="scss" scoped></style>
