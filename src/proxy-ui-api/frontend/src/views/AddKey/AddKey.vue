<template>
  <div class="view-wrap">
    <subViewTitle
      class="view-title"
      :title="$t('csr.generateCsr')"
      :showClose="false"
    />
    <v-stepper
      :alt-labels="true"
      v-model="currentStep"
      class="stepper noshadow"
    >
      <v-stepper-header class="noshadow">
        <v-stepper-step :complete="currentStep > 1" step="1">{{
          $t('keys.detailsTitle')
        }}</v-stepper-step>
        <v-divider></v-divider>
        <v-stepper-step :complete="currentStep > 2" step="2">{{
          $t('csr.csrDetails')
        }}</v-stepper-step>
        <v-divider></v-divider>
        <v-stepper-step :complete="currentStep > 3" step="3">{{
          $t('csr.generateCsr')
        }}</v-stepper-step>
      </v-stepper-header>

      <v-stepper-items class="stepper-content">
        <!-- Step 1 -->
        <v-stepper-content step="1">
          <WizardPageKeyLabel @cancel="cancel" @done="currentStep = 2" />
        </v-stepper-content>
        <!-- Step 2 -->
        <v-stepper-content step="2">
          <WizardPageCsrDetails
            @cancel="cancel"
            @previous="currentStep = 1"
            @done="save"
          />
        </v-stepper-content>
        <!-- Step 3 -->
        <v-stepper-content step="3">
          <WizardPageGenerateCsr
            @cancel="cancel"
            @previous="currentStep = 2"
            @done="done"
            keyAndCsr
          />
        </v-stepper-content>
      </v-stepper-items>
    </v-stepper>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import SubViewTitle from '@/components/ui/SubViewTitle.vue';
import WizardPageKeyLabel from '@/components/wizard/WizardPageKeyLabel.vue';
import WizardPageCsrDetails from '@/components/wizard/WizardPageCsrDetails.vue';
import WizardPageGenerateCsr from '@/components/wizard/WizardPageGenerateCsr.vue';
import { RouteName } from '@/global';

export default Vue.extend({
  components: {
    SubViewTitle,
    WizardPageKeyLabel,
    WizardPageCsrDetails,
    WizardPageGenerateCsr,
  },
  props: {
    tokenId: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      currentStep: 1,
    };
  },
  methods: {
    save(): void {
      this.$store.dispatch('fetchCsrForm').then(
        () => {
          this.currentStep = 3;
        },
        (error) => {
          this.$store.dispatch('showError', error);
        },
      );
    },
    cancel(): void {
      this.$store.dispatch('resetCsrState');
      this.$router.replace({ name: RouteName.SignAndAuthKeys });
    },
    done(): void {
      this.$store.dispatch('resetCsrState');
      this.$router.replace({ name: RouteName.SignAndAuthKeys });
    },

    fetchKeyData(): void {
      this.$store.dispatch('fetchKeyData').catch((error) => {
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
    this.$store.dispatch('setCsrTokenId', this.tokenId);
    this.fetchCertificateAuthorities();
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
