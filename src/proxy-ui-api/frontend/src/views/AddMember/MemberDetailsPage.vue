<template>
  <div>
    <div class="info-block">
      <div>
        {{ $t('wizard.member.info1') }}
        <br />
        <br />
        {{ $t('wizard.member.info2') }}
      </div>
      <div class="action-block">
        <large-button
          @click="showSelectClient = true"
          outlined
          data-test="select-client-button"
          >{{ $t('wizard.member.select') }}</large-button
        >
      </div>
    </div>

    <ValidationObserver ref="form2" v-slot="{ validate, invalid }">
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
          v-slot="{}"
        >
          <v-select
            :items="memberClasses"
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
          ref="memberCodeVP"
        >
          <v-text-field
            class="form-input"
            type="text"
            :error-messages="errors"
            v-model="memberCode"
            data-test="member-code-input"
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
          :disabled="invalid || duplicateClient || checkRunning"
          data-test="next-button"
          >{{ $t('action.next') }}</large-button
        >
      </div>
    </ValidationObserver>

    <SelectClientDialog
      title="wizard.addMemberTitle"
      :dialog="showSelectClient"
      :selectableClients="selectableMembers"
      @cancel="showSelectClient = false"
      @save="saveSelectedClient"
    />
  </div>
</template>

<script lang="ts">
import Vue, { VueConstructor } from 'vue';
import { mapGetters } from 'vuex';
import FormLabel from '@/components/ui/FormLabel.vue';
import LargeButton from '@/components/ui/LargeButton.vue';
import SelectClientDialog from '@/components/client/SelectClientDialog.vue';
import { Client } from '@/openapi-types';
import { debounce, isEmpty } from '@/util/helpers';
import { ValidationObserver, ValidationProvider } from 'vee-validate';
import { AddMemberWizardModes } from '@/global';

// To provide the Vue instance to debounce
// eslint-disable-next-line @typescript-eslint/no-explicit-any
let that: any;

export default (Vue as VueConstructor<
  Vue & {
    $refs: {
      memberCodeVP: InstanceType<typeof ValidationProvider>;
    };
  }
>).extend({
  components: {
    FormLabel,
    LargeButton,
    ValidationObserver,
    ValidationProvider,
    SelectClientDialog,
  },
  computed: {
    ...mapGetters(['reservedMember', 'memberClasses', 'selectedMemberName']),

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

    selectableMembers(): Client[] {
      // Filter out the owner member
      const filtered = this.$store.getters.selectableMembers.filter(
        (client: Client) => {
          return !(
            client.member_class === this.reservedMember.memberClass &&
            client.member_code === this.reservedMember.memberCode
          );
        },
      );
      return filtered;
    },

    duplicateClient(): boolean {
      if (!this.memberClass || !this.memberCode) {
        return false;
      }

      // Check that the info doesn't match the reserved member (owner member)
      return !(
        this.reservedMember.memberClass.toLowerCase() !==
          this.memberClass.toLowerCase() ||
        this.reservedMember.memberCode.toLowerCase() !==
          this.memberCode.toLowerCase()
      );
    },
  },
  data() {
    return {
      showSelectClient: false as boolean,
      checkRunning: false as boolean,
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
      this.$store.dispatch('setSelectedMember', selectedMember);
      this.showSelectClient = false;
    },
    checkClient(): void {
      // check if the identifier is valid
      if (!this.isMemberCodeValid) {
        return;
      }
      this.checkRunning = true;

      // Find if the selectable clients array has a match
      const tempClient = this.selectableMembers.find((client: Client) => {
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
          instanceId: that.reservedMember.instanceId,
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
    this.$store.dispatch('fetchSelectableMembers');
  },

  watch: {
    async memberCode(val) {
      // Set wizard mode to default (full)
      this.$store.commit('setAddMemberWizardMode', AddMemberWizardModes.FULL);

      // Needs to be done here, because the watcher runs before the setter
      this.isMemberCodeValid = (await this.$refs.memberCodeVP.validate()).valid;
      if (isEmpty(val) || isEmpty(this.memberClass)) {
        return;
      }
      this.checkClient();
    },
    memberClass(val): void {
      // Set wizard mode to default (full)
      this.$store.commit('setAddMemberWizardMode', AddMemberWizardModes.FULL);
      if (isEmpty(val) || isEmpty(this.memberCode)) {
        return;
      }
      this.checkClient();
    },

    memberClasses(val): void {
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
