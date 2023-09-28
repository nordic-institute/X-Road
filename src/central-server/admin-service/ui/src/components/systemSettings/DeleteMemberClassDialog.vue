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
    data-test="system-settings-member-class-delete-confirm-dialog"
    :loading="deleting"
    title="action.confirm"
    text="systemSettings.deleteMemberClass"
    @cancel="cancelDelete"
    @accept="acceptDelete"
  />
</template>
<script lang="ts">
import { defineComponent, PropType } from 'vue';
import { useForm } from 'vee-validate';
import { Event } from '@/ui-types';
import { MemberClass } from '@/openapi-types';
import { mapStores } from 'pinia';
import { useMemberClass } from '@/store/modules/member-class';
import { useNotifications } from '@/store/modules/notifications';

export default defineComponent({
  props: {
    memberClass: {
      type: Object as PropType<MemberClass>,
      required: true,
    },
  },
  emits: [Event.Cancel, Event.Delete],
  setup(props) {
    const { meta, values, errors, setFieldError, defineComponentBinds } =
      useForm({
        validationSchema: {
          code: 'required|min:1|max:255',
          description: 'required|min:1',
        },
        initialValues: {
          code: props.memberClass?.code || '',
          description: props.memberClass?.description || '',
        },
      });
    const classCode = defineComponentBinds('code');
    const classDescription = defineComponentBinds('description');
    return { meta, values, errors, setFieldError, classCode, classDescription };
  },
  data() {
    return {
      deleting: false,
    };
  },
  computed: {
    ...mapStores(useMemberClass, useNotifications),
  },
  methods: {
    cancelDelete() {
      this.$emit(Event.Cancel);
    },
    async acceptDelete() {
      this.deleting = true;
      try {
        await this.memberClassStore.delete(this.memberClass);
        this.notificationsStore.showSuccess(
          this.$t('systemSettings.memberClassDeleted'),
        );
        this.$emit(Event.Delete);
      } catch (error: unknown) {
        this.notificationsStore.showError(error);
        this.$emit(Event.Cancel);
      }
      this.deleting = false;
    },
  },
});
</script>
