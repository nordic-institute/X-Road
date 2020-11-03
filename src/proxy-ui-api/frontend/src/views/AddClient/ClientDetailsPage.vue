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
  <div>
    <div class="info-block">
      <div>
        {{ $t('wizard.clientInfo1') }}
        <br />
        <br />
        {{ $t('wizard.clientInfo2') }}
      </div>
      <div class="action-block">
        <large-button
          @click="showSelectClient = true"
          outlined
          data-test="select-client-button"
          >{{ $t('wizard.selectClient') }}</large-button
        >
      </div>
    </div>

    <ValidationObserver ref="form2" v-slot="{ invalid }">
      <div class="row-wrap">
        <FormLabel
          labelText="wizard.memberName"
          helpText="wizard.client.memberNameTooltip"
        />
        <div data-test="selected-member-name">{{ selectedMemberName }}</div>
      </div>

      <div class="row-wrap">
        <FormLabel
          labelText="wizard.memberClass"
          helpText="wizard.client.memberClassTooltip"
        />

        <ValidationProvider
          name="addClient.memberClass"
          rules="required"
          v-slot="{ errors }"
        >
          <v-select
            :items="memberClassesCurrentInstance"
            :error-messages="errors"
            class="form-input"
            v-model="memberClass"
            data-test="member-class-input"
          ></v-select>
        </ValidationProvider>
      </div>
      <div class="row-wrap">
        <FormLabel
          labelText="wizard.memberCode"
          helpText="wizard.client.memberCodeTooltip"
        />

        <ValidationProvider
          name="addClient.memberCode"
          rules="required|xrdIdentifier"
          v-slot="{ errors }"
        >
          <v-text-field
            class="form-input"
            type="text"
            :error-messages="errors"
            v-model="memberCode"
            autofocus
            data-test="member-code-input"
          ></v-text-field>
        </ValidationProvider>
      </div>

      <div class="row-wrap">
        <FormLabel
          labelText="wizard.subsystemCode"
          helpText="wizard.client.subsystemCodeTooltip"
        />

        <ValidationProvider
          name="addClient.subsystemCode"
          rules="required|xrdIdentifier"
          v-slot="{ errors }"
        >
          <v-text-field
            class="form-input"
            type="text"
            :error-messages="errors"
            v-model="subsystemCode"
            data-test="subsystem-code-input"
          ></v-text-field>
        </ValidationProvider>
      </div>
      <div v-if="duplicateClient" class="duplicate-warning">
        {{ $t('wizard.client.memberExists') }}
      </div>
      <div class="button-footer">
        <div class="button-group">
          <large-button outlined @click="cancel" data-test="cancel-button">{{
            $t('action.cancel')
          }}</large-button>
        </div>
        <large-button
          @click="done"
          :disabled="invalid || duplicateClient"
          data-test="next-button"
          >{{ $t('action.next') }}</large-button
        >
      </div>
    </ValidationObserver>

    <SelectClientDialog
      :dialog="showSelectClient"
      :selectableClients="selectableClients"
      @cancel="showSelectClient = false"
      @save="saveSelectedClient"
    />
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';
import FormLabel from '@/components/ui/FormLabel.vue';
import LargeButton from '@/components/ui/LargeButton.vue';
import SelectClientDialog from '@/components/client/SelectClientDialog.vue';
import { debounce, isEmpty, containsClient } from '@/util/helpers';
import { Client } from '@/openapi-types';
import { ValidationProvider, ValidationObserver } from 'vee-validate';
import { AddMemberWizardModes } from '@/global';
import { validate } from 'vee-validate';

// To provide the Vue instance to debounce
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let that: any;

export default Vue.extend({
  components: {
    FormLabel,
    LargeButton,
    ValidationObserver,
    ValidationProvider,
    SelectClientDialog,
  },
  computed: {
    ...mapGetters([
      'reservedClients',
      'selectableClients',
      'memberClassesCurrentInstance',
      'selectedMemberName',
      'currentSecurityServer',
    ]),

    memberClass: {
      get(): string {
        return this.$store.getters.memberClass;
      },
      set(value: string) {
        this.$store.commit('setMemberClass', value);
      },
    },

    memberCode: {
      get(): string {
        return this.$store.getters.memberCode;
      },
      set(value: string) {
        this.$store.commit('setMemberCode', value);
      },
    },

    subsystemCode: {
      get(): string {
        return this.$store.getters.subsystemCode;
      },
      set(value: string) {
        this.$store.commit('setSubsystemCode', value);
      },
    },

    duplicateClient(): boolean {
      return containsClient(
        this.reservedClients,
        this.memberClass,
        this.memberCode,
        this.subsystemCode,
      );
    },
  },
  data() {
    return {
      showSelectClient: false,
      checkRunning: false,
      isMemberCodeValid: true,
    };
  },
  methods: {
    cancel(): void {
      this.$emit('cancel');
    },
    done(): void {
      this.$emit('done');
    },
    saveSelectedClient(selectedMember: Client): void {
      this.$store.dispatch('setSelectedMember', selectedMember).then(
        () => {
          this.$store.dispatch('fetchReservedClients', selectedMember);
        },
        (error) => {
          this.$store.dispatch('showError', error);
        },
      );
      this.showSelectClient = false;
    },
    checkClient(): void {
      // don't continue if the identifier is invalid
      if (!this.isMemberCodeValid) {
        return;
      }
      this.checkRunning = true;

      // Find if the selectable clients array has a match
      const tempClient = this.selectableClients.find((client: Client) => {
        return (
          client.member_code === this.memberCode &&
          client.member_class === this.memberClass
        );
      });

      // Fill the name "field" if it's available or set it undefined
      this.$store.commit('setSelectedMemberName', tempClient?.member_name);

      // Pass the arguments so that we use the validated information instead of the state at that time
      this.checkClientDebounce(this.memberClass, this.memberCode);
    },
    checkClientDebounce: debounce((memberClass: string, memberCode: string) => {
      // Debounce is used to reduce unnecessary api calls
      // Search tokens for suitable CSR:s and certificates
      that.$store
        .dispatch('searchTokens', {
          instanceId: that.currentSecurityServer.instance_id,
          memberClass: memberClass,
          memberCode: memberCode,
        })
        .then(
          () => {
            that.checkRunning = false;
          },
          (error: Error) => {
            that.$store.dispatch('showError', error);
            that.checkRunning = true;
          },
        );
    }, 600),
  },
  created() {
    that = this;
    this.$store.commit('setAddMemberWizardMode', AddMemberWizardModes.FULL);
    this.$store.dispatch(
      'fetchSelectableClients',
      that.currentSecurityServer.instance_id,
    );
    this.$store.dispatch('fetchMemberClassesForCurrentInstance');
  },

  watch: {
    async memberCode(val) {
      // Set wizard mode to default (full)
      this.$store.commit('setAddMemberWizardMode', AddMemberWizardModes.FULL);

      // Needs to be done here, because the watcher runs before the setter
      validate(this.memberCode, 'required|xrdIdentifier', {
        // name is not used, but if it's undefined there is a warning in browser console
        name: 'addClient.memberCode',
      }).then((result) => {
        if (result.valid) {
          this.isMemberCodeValid = true;

          if (isEmpty(val) || isEmpty(this.memberClass)) {
            return;
          }
          this.checkClient();
        } else {
          this.isMemberCodeValid = false;
        }
      });
    },
    memberClass(val): void {
      // Set wizard mode to default (full)
      this.$store.commit('setAddMemberWizardMode', AddMemberWizardModes.FULL);
      if (isEmpty(val) || isEmpty(this.memberCode)) {
        return;
      }
      this.checkClient();
    },

    memberClassesCurrentInstance(val): void {
      // Set first member class selected as default when the list is updated
      if (val?.length === 1) {
        this.memberClass = val[0];
      }
    },
  },
  mounted() {
    this.$refs.memberCodeVP;
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/wizards';

.info-block {
  display: flex;
  flex-direction: row;
  margin-bottom: 40px;

  .action-block {
    margin-top: 30px;
    margin-left: auto;
    margin-right: 0px;
  }
}

.duplicate-warning {
  margin-left: 230px;
  margin-top: 10px;
  color: #ff5252;
  font-size: 12px;
}
</style>
