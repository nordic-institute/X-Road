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
    :disable-save="!formReady"
    :dialog="showDialog"
    cancel-button-text="action.cancel"
    title="members.addMember"
    @cancel="cancel"
    @save="add"
  >
    <template #content>
      <div class="dlg-input-width">
        <v-text-field
          v-model="memberName"
          :label="$t('global.memberName')"
          outlined
          autofocus
          data-test="add-member-name-input"
        ></v-text-field>
      </div>

      <v-select
        v-model="memberClass"
        :items="memberClasses"
        :label="$t('global.memberClass')"
        item-text="code"
        outlined
      ></v-select>

      <div class="dlg-input-width">
        <v-text-field
          v-model="memberCode"
          :label="$t('global.memberCode')"
          outlined
          data-test="add-member-code-input"
        ></v-text-field>
      </div>
    </template>
  </xrd-simple-dialog>
</template>

<script lang="ts">
import Vue from 'vue';
import {mapActions, mapState, mapStores} from 'pinia';
import {MemberClass, XRoadId} from "@/openapi-types";
import {clientStore} from "@/store/modules/clients";
import {systemStore} from "@/store/modules/system";
import {notificationsStore} from "@/store/modules/notifications";
import {useMemberClassStore} from "@/store/modules/member-class";

export default Vue.extend({
  name: 'AddMemberDialog',
  props: {
    showDialog: {
      type: Boolean,
      required: true,
    },
  },

  data() {
    return {
      memberName: '',
      memberClass: '',
      memberCode: '',
    };
  },
  computed: {
    ...mapStores(clientStore, useMemberClassStore),
    ...mapState(systemStore, ['getSystemStatus']),
    memberClasses(): MemberClass[] {
      return this.memberClassStore.memberClasses;
    },
    formReady(): boolean {
      return !!(
        this.memberName &&
        this.memberClass &&
        this.memberCode
      );
    },
  },
  created() {
    this.memberClassStore.fetchAll();
  },
  methods: {
    ...mapActions(notificationsStore, ['showError', 'showSuccess']),
    cancel(): void {
      this.$emit('cancel');
      this.clearForm();
    },
    clearForm(): void {
      this.memberName = '';
      this.memberClass = '';
      this.memberCode = '';
    },
    add(): void {
      const instanceId: string = this.getSystemStatus?.initialization_status?.instance_identifier as string;
      this.clientStore.add({
        member_name: this.memberName,
        id: `${instanceId}:${this.memberClass}:${this.memberName}`,
        xroad_id: {
          member_class: this.memberClass,
          member_code: this.memberCode,
          type: XRoadId.type.MEMBER,
          instance_id: instanceId,
        },
      })
      .then(() => {
        this.showSuccess(
          this.$t('members.memberSuccessfullyAdded', { memberName: this.memberName }),
        );
        this.$emit('save');
        this.clearForm();
      })
      .catch((error) => {
        this.showError(error);
      });
    }
  }
});
</script>
