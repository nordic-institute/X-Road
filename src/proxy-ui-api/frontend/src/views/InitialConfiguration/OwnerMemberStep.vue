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
    <ValidationObserver ref="form1" v-slot="{ validate, invalid }">
      <div class="row-wrap">
        <FormLabel
          labelText="wizard.memberName"
          helpText="wizard.client.memberNameTooltip"
        />
        <div v-if="memberName" data-test="selected-member-name">
          {{ memberName }}
        </div>
      </div>

      <div class="row-wrap">
        <FormLabel
          labelText="wizard.memberClass"
          helpText="wizard.client.memberClassTooltip"
        />

        <ValidationProvider name="addClient.memberClass" rules="required">
          <v-select
            v-model="memberClass"
            :items="memberClassesCurrentInstance"
            :disabled="isServerOwnerInitialized"
            data-test="member-class-input"
            class="form-input"
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
          ref="memberCodeVP"
        >
          <v-text-field
            class="form-input"
            type="text"
            :error-messages="errors"
            :disabled="isServerOwnerInitialized"
            v-model="memberCode"
            data-test="member-code-input"
          ></v-text-field>
        </ValidationProvider>
      </div>

      <div class="row-wrap">
        <FormLabel
          labelText="fields.securityServerCode"
          helpText="initialConfiguration.member.serverCodeHelp"
        />

        <ValidationProvider
          name="securityServerCode"
          rules="required|xrdIdentifier"
          v-slot="{ errors }"
        >
          <v-text-field
            class="form-input"
            type="text"
            :error-messages="errors"
            :disabled="isServerCodeInitialized"
            v-model="securityServerCode"
            data-test="security-server-code-input"
          ></v-text-field>
        </ValidationProvider>
      </div>

      <div class="button-footer">
        <v-spacer></v-spacer>
        <div>
          <large-button
            v-if="showPreviousButton"
            @click="previous"
            outlined
            class="previous-button"
            data-test="previous-button"
            >{{ $t('action.previous') }}</large-button
          >
          <large-button
            :disabled="invalid"
            @click="done"
            data-test="save-button"
            >{{ $t(saveButtonText) }}</large-button
          >
        </div>
      </div>
    </ValidationObserver>
  </div>
</template>

<script lang="ts">
import Vue, { VueConstructor } from 'vue';
import { mapGetters } from 'vuex';
import { ValidationProvider, ValidationObserver } from 'vee-validate';
import LargeButton from '@/components/ui/LargeButton.vue';
import FormLabel from '@/components/ui/FormLabel.vue';

export default (Vue as VueConstructor<
  Vue & {
    $refs: {
      memberCodeVP: InstanceType<typeof ValidationProvider>;
    };
  }
>).extend({
  components: {
    LargeButton,
    ValidationObserver,
    ValidationProvider,
    FormLabel,
  },
  props: {
    saveButtonText: {
      type: String,
      default: 'action.continue',
    },
    showPreviousButton: {
      type: Boolean,
      default: true,
    },
  },
  data() {
    return {
      isMemberCodeValid: true,
    };
  },
  computed: {
    ...mapGetters([
      'memberClassesCurrentInstance',
      'initExistingMembers',
      'currentSecurityServer',
      'isServerCodeInitialized',
      'isServerOwnerInitialized',
      'memberName',
    ]),

    memberClass: {
      get(): string {
        if (this.currentSecurityServer?.member_class) {
          return this.currentSecurityServer.member_class;
        }
        return this.$store.getters.initServerMemberClass;
      },
      set(value: string) {
        this.$store.commit('storeInitServerMemberClass', value);
      },
    },
    memberCode: {
      get(): string {
        if (this.currentSecurityServer?.member_code) {
          return this.currentSecurityServer.member_code;
        }
        return this.$store.getters.initServerMemberCode;
      },
      set(value: string) {
        this.$store.commit('storeInitServerMemberCode', value);
      },
    },
    securityServerCode: {
      get(): string {
        if (this.currentSecurityServer?.server_code) {
          return this.currentSecurityServer.server_code;
        }
        return this.$store.getters.initServerSSCode;
      },
      set(value: string) {
        this.$store.commit('storeInitServerSSCode', value);
      },
    },
  },
  methods: {
    done(): void {
      this.$emit('done');
    },
    previous(): void {
      this.$emit('previous');
    },

    checkMember(): void {
      if (
        this.memberClass?.length > 0 &&
        this.memberCode?.length > 0 &&
        this.isMemberCodeValid
      ) {
        this.$store
          .dispatch('fetchMemberName', {
            memberClass: this.memberClass,
            memberCode: this.memberCode,
          })
          .catch((error) => {
            if (error.response.status === 404) {
              // no match found
              return;
            }
            this.$store.dispatch('showError', error);
          });
      }
    },
  },

  watch: {
    memberClassesCurrentInstance(val: string[]) {
      // Set first member class selected if there is only one
      if (val?.length === 1) {
        this.$store.commit('storeInitServerMemberClass', val[0]);
      }
    },
    memberClass(val) {
      if (val) {
        // Update member name when info changes
        this.checkMember();
      }
    },
    async memberCode(val) {
      // Needs to be done here, because the watcher runs before the setter
      this.isMemberCodeValid = (await this.$refs.memberCodeVP.validate()).valid;
      if (val) {
        // Update member name when info changes
        this.checkMember();
      }
    },
  },
  beforeMount() {
    this.$store
      .dispatch('fetchMemberClassesForCurrentInstance')
      .catch((error) => {
        if (error.response.status === 500) {
          // this can happen if anchor is not ready
          return;
        }
        this.$store.dispatch('showError', error);
      });

    this.checkMember();
  },
  mounted() {
    this.$refs.memberCodeVP;
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/wizards';

.readonly-info-field {
  max-width: 300px;
  height: 60px;
  padding-top: 12px;
}
</style>
