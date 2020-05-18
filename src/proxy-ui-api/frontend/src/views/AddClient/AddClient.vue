
<template>
  <div class="view-wrap">
    <subViewTitle
      class="view-title"
      :title="$t('wizard.addClientTitle')"
      :showClose="false"
      data-test="wizard-title"
    />
    <v-stepper :alt-labels="true" v-model="currentStep" class="stepper noshadow">
      <v-stepper-header class="noshadow">
        <v-stepper-step :complete="currentStep > 1" step="1">{{$t('wizard.clientDetails')}}</v-stepper-step>
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

      <v-stepper-items class="stepper-content">
        <!-- Step 1 -->
        <v-stepper-content step="1">
          <ClientDetailsPage @cancel="cancel" @done="clientDetailsReady" />
        </v-stepper-content>
        <!-- Step 2 -->
        <v-stepper-content step="2">
          <TokenPage @cancel="cancel" @previous="tokenPrevious" @done="tokenReady" />
        </v-stepper-content>
        <!-- Step 3 -->
        <v-stepper-content step="3">
          <SignKeyPage @cancel="cancel" @previous="signKeyPrevious" @done="signKeyReady" />
        </v-stepper-content>
        <!-- Step 4 -->
        <v-stepper-content step="4">
          <CsrDetailsPageLocked
            @cancel="cancel"
            @previous="csrDetailsPrevious"
            @done="csrDetailsReady"
            saveButtonText="action.next"
          />
        </v-stepper-content>
        <!-- Step 5 -->
        <v-stepper-content step="5">
          <GenerateCsrPage
            @cancel="cancel"
            @previous="generateCsrPrevious"
            @done="generateCsrReady"
            saveButtonText="action.next"
          />
        </v-stepper-content>
        <!-- Step 6 -->
        <v-stepper-content step="6">
          <FinishPage @cancel="cancel" @previous="finishPrevious" @done="done" />
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
import ClientDetailsPage from './ClientDetailsPage.vue';
import TokenPage from './TokenPage.vue';
import SignKeyPage from './SignKeyPage.vue';
import FinishPage from './FinishPage.vue';
import CsrDetailsPageLocked from '@/components/wizard/CsrDetailsPageLocked.vue';
import GenerateCsrPage from './GenerateCsrPage.vue';
import { Key, Token } from '@/types';
import { RouteName, UsageTypes } from '@/global';
import * as api from '@/util/api';

export default Vue.extend({
  components: {
    HelpIcon,
    LargeButton,
    SubViewTitle,
    ClientDetailsPage,
    TokenPage,
    SignKeyPage,
    FinishPage,
    CsrDetailsPageLocked,
    GenerateCsrPage,
  },
  props: {},
  data() {
    return {
      currentStep: 1,
    };
  },
  methods: {
    cancel(): void {
      this.$store.dispatch('resetCsrState');
      this.$store.dispatch('resetAddClientState');
      this.$router.replace({ name: RouteName.Clients });
    },

    clientDetailsReady(): void {
      this.currentStep = 2;
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

    signKeyReady(): void {
      this.currentStep = 4;
    },
    csrDetailsReady(): void {
      // Add the selected client id in the CSR store
      const idString = this.$store.getters.selectedMemberId;
      this.$store.commit('storeCsrClient', idString);

      this.$store.dispatch('fetchCsrForm').then(
        () => {
          this.currentStep = 5;
        },
        (error) => {
          this.$store.dispatch('showError', error);
        },
      );
    },
    generateCsrReady(): void {
      this.currentStep = 6;
    },

    tokenPrevious(): void {
      this.currentStep = 1;
    },

    signKeyPrevious(): void {
      this.currentStep = 2;
    },

    csrDetailsPrevious(): void {
      this.currentStep = 3;
    },
    generateCsrPrevious(): void {
      this.currentStep = 4;
    },

    finishPrevious(): void {
      this.currentStep = 5;
    },

    done(): void {
      this.$store.dispatch('resetCsrState');
      this.$store.dispatch('resetAddClientState');
      this.$router.replace({ name: RouteName.Clients });
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
