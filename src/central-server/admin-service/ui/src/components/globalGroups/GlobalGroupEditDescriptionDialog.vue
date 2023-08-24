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
  <xrd-simple-dialog
    :loading="loading"
    cancel-button-text="action.cancel"
    save-button-text="action.save"
    title="globalGroup.editDescription"
    :disable-save="!meta.valid || !meta.dirty"
    @save="saveDescription"
    @cancel="cancelEdit"
  >
    <template #content>
      <v-text-field
        v-bind="newDescription"
        variant="outlined"
        :label="$t('globalGroup.description')"
        :error-messages="errors.description"
        persistent-hint
      />
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { useForm } from 'vee-validate';
import { Event } from '@/ui-types';
import { mapActions, mapStores } from 'pinia';
import { useGlobalGroups } from '@/store/modules/global-groups';
import { useNotifications } from '@/store/modules/notifications';

export default defineComponent({
  props: {
    groupCode: {
      type: String,
      required: true,
    },
    groupDescription: {
      type: String,
      required: true,
    },
  },
  emits: [Event.Cancel, Event.Edit],
  setup(props) {
    const { values, errors, meta, defineComponentBinds } = useForm({
      validationSchema: { description: 'required' },
      initialValues: { description: props.groupDescription },
    });
    const newDescription = defineComponentBinds('description');
    return { values, errors, meta, newDescription };
  },
  data() {
    return {
      loading: false,
    };
  },
  computed: {
    ...mapStores(useGlobalGroups),
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    cancelEdit(): void {
      this.$emit(Event.Cancel);
    },
    saveDescription(): void {
      this.loading = true;
      this.globalGroupStore
        .editGroupDescription(this.groupCode, {
          description: this.values.description,
        })
        .then((resp) => {
          this.showSuccess(this.$t('globalGroup.descriptionSaved'));
          this.$emit(Event.Edit, resp.data);
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
