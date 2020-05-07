
<template>
  <div class="view-wrap">
    <subViewTitle
      class="view-title"
      :title="$t('wizard.addMemberTitle')"
      :showClose="false"
      data-test="wizard-title"
    />
    <v-stepper :alt-labels="true" v-model="currentStep" class="stepper noshadow">
      <template v-if="addMemberMode === 'ALL'">
        <v-stepper-header class="noshadow">
          <v-stepper-step :complete="currentStep > 1" step="1">{{$t('wizard.member.title')}}</v-stepper-step>
          <v-divider></v-divider>
          <v-stepper-step :complete="currentStep > 2" step="2">{{$t('wizard.token.title')}}</v-stepper-step>
          <v-divider></v-divider>
          <v-stepper-step :complete="currentStep > 3" step="3">{{$t('wizard.signKey.title')}}</v-stepper-step>
          <v-divider></v-divider>
          <v-stepper-step :complete="currentStep > 4" step="4">{{$t('csr.csrDetails')}}</v-stepper-step>
          <v-divider></v-divider>
          <v-stepper-step :complete="currentStep > 5" step="5">{{$t('csr.generateCsr')}}</v-stepper-step>
          <v-divider></v-divider>
          <v-stepper-step :complete="currentStep > 6" step="6">{{$t('wizard.finish.title')}}</v-stepper-step>
        </v-stepper-header>
      </template>

      <template v-if="addMemberMode === 'CERT'">
        <v-stepper-header class="noshadow">
          <v-stepper-step :complete="currentStep > 1" step="1">{{$t('wizard.member.title')}}</v-stepper-step>
          <v-divider></v-divider>
          <v-stepper-step :complete="currentStep > 2" step="2">{{$t('csr.csrDetails')}}</v-stepper-step>
          <v-divider></v-divider>
          <v-stepper-step :complete="currentStep > 3" step="3">{{$t('csr.generateCsr')}}</v-stepper-step>
          <v-divider></v-divider>
          <v-stepper-step :complete="currentStep > 4" step="4">{{$t('wizard.finish.title')}}</v-stepper-step>
        </v-stepper-header>
      </template>

      <template v-if="addMemberMode === 'NONE'">
        
        <v-stepper-header class="noshadow">
          <v-stepper-step :complete="currentStep > 1" step="1">{{$t('wizard.member.title')}}</v-stepper-step>
        </v-stepper-header>
        
      </template>

      <v-stepper-items class="stepper-content">
        <!-- Step 1 -->
        <v-stepper-content step="1">
          <MemberDetailsPage @cancel="cancel" @done="currentStep++" />
        </v-stepper-content>
        <!-- Step 2 -->
        <v-stepper-content :step="tokenPageNumber">
          <TokenPage @cancel="cancel" @previous="previousPage" @done="tokenReady" />
        </v-stepper-content>
        <!-- Step 3 -->
        <v-stepper-content :step="keyPageNumber">
          <SignKeyPage @cancel="cancel" @previous="previousPage" @done="currentStep++" />
        </v-stepper-content>
        <!-- Step 4 -->
        <v-stepper-content :step="csrDetailsPageNumber">
          <WizardPageCsrDetails
            @cancel="cancel"
            @previous="previousPage"
            @done="csrDetailsReady"
            saveButtonText="action.next"
          />
        </v-stepper-content>
        <!-- Step 5 -->
        <v-stepper-content :step="csrGeneratePageNumber">
          <GenerateCsrPage
            @cancel="cancel"
            @previous="previousPage"
            @done="currentStep++"
            saveButtonText="action.next"
          />
        </v-stepper-content>
        <!-- Step 6 -->
        <v-stepper-content :step="finishPageNumber">
          <FinishPage @cancel="cancel" @previous="previousPage" @done="done" />
        </v-stepper-content>
      </v-stepper-items>
    </v-stepper>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';
import HelpIcon from '@/components/ui/HelpIcon.vue';
import LargeButton from '@/components/ui/LargeButton.vue';
import SubViewTitle from '@/components/ui/SubViewTitle.vue';
import MemberDetailsPage from './MemberDetailsPage.vue';
import TokenPage from './TokenPage.vue';
import SignKeyPage from './SignKeyPage.vue';
import FinishPage from './FinishPage.vue';
import WizardPageCsrDetails from '@/components/wizard/WizardPageCsrDetails.vue';
import GenerateCsrPage from './GenerateCsrPage.vue';

import { Key, Token } from '@/types';
import { RouteName, UsageTypes } from '@/global';
import * as api from '@/util/api';

export default Vue.extend({
  components: {
    HelpIcon,
    LargeButton,
    SubViewTitle,
    MemberDetailsPage,
    TokenPage,
    SignKeyPage,
    FinishPage,
    WizardPageCsrDetails,
    GenerateCsrPage,
  },
  props: {},
  data() {
    return {
      currentStep: 1,
    };
  },

  computed: {
    ...mapGetters(['addMemberMode']),

    tokenPageNumber(): number {
      if (this.addMemberMode === 'CERT') {
        return 999;
      }
      return 2;
    },

    keyPageNumber(): number {
      if (this.addMemberMode === 'CERT') {
        return 999;
      }
      return 3;
    },
    csrDetailsPageNumber(): number {
      if (this.addMemberMode === 'CERT') {
        return 2;
      }
      return 4;
    },

    csrGeneratePageNumber(): number {
      if (this.addMemberMode === 'CERT') {
        return 3;
      }
      return 5;
    },

    finishPageNumber(): number {
      if (this.addMemberMode === 'CERT') {
        return 4;
      }
      return 6;
    },
  },

  methods: {
    cancel(): void {
      this.$store.dispatch('resetState');
      this.$store.dispatch('resetAddClientState');
      this.$router.replace({ name: RouteName.Clients });
    },

    tokenReady(): void {
      this.currentStep = 3;

      this.$store.dispatch('fetchLocalMembers').catch((error) => {
        this.$store.dispatch('showError', error);
      });

      this.$store.dispatch('fetchCertificateAuthorities').catch((error) => {
        this.$store.dispatch('showError', error);
      });
    },

    csrDetailsReady(): void {
      this.$store.dispatch('fetchCsrForm').then(
        (response) => {
          this.currentStep++;
        },
        (error) => {
          this.$store.dispatch('showError', error);
        },
      );
    },

    previousPage(): void {
      this.currentStep--;
    },

    done(): void {
      this.$store.dispatch('resetState');
      this.$store.dispatch('resetAddClientState');
      this.$router.replace({ name: RouteName.Clients });
    },

    fetchKeyData(id: string): void {
      this.$store.dispatch('fetchKeyData').catch((error) => {
        this.$store.dispatch('showError', error);
      });
    },

    fetchLocalMembers(): void {
      this.$store.dispatch('fetchLocalMembers').catch((error) => {
        this.$store.dispatch('showError', error);
      });
    },

    fetchCertificateAuthorities(): void {
      this.$store.dispatch('fetchCertificateAuthorities').catch((error) => {
        this.$store.dispatch('showError', error);
      });
    },
  },
  created() {
    this.$store.dispatch('setupSignKey');
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/colors';
@import '../../assets/shared';

.view-wrap {
  width: 100%;
  max-width: 850px;
  margin: 10px;
}

.view-title {
  width: 100%;
  max-width: 100%;
  margin-bottom: 30px;
}

.stepper-content {
  width: 100%;
  max-width: 900px;
  margin-left: auto;
  margin-right: auto;
}

.stepper {
  width: 100%;
}

.noshadow {
  -webkit-box-shadow: none;
  -moz-box-shadow: none;
  box-shadow: none;
}
</style>