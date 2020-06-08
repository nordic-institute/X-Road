<template>
  <div>
    <ValidationObserver ref="form1" v-slot="{ validate, invalid }">
      <div class="row-wrap">
        <div class="label">
          {{ $t('csr.usage') }}
          <helpIcon :text="$t('csr.helpUsage')" />
        </div>
        <div class="readonly-info-field">{{ usage }}</div>
      </div>

      <div class="row-wrap">
        <div class="label">
          {{ $t('csr.client') }}
          <helpIcon :text="$t('csr.helpClient')" />
        </div>
        <div class="readonly-info-field">{{ selectedMemberId }}</div>
      </div>

      <div class="row-wrap">
        <div class="label">
          {{ $t('csr.certificationService') }}
          <helpIcon :text="$t('csr.helpCertificationService')" />
        </div>

        <ValidationProvider
          name="crs.certService"
          rules="required"
          v-slot="{ errors }"
        >
          <v-select
            :items="filteredServiceList"
            item-text="name"
            item-value="name"
            class="form-input"
            v-model="certificationService"
            data-test="csr-certification-service-select"
          ></v-select>
        </ValidationProvider>
      </div>

      <div class="row-wrap">
        <div class="label">
          {{ $t('csr.csrFormat') }}
          <helpIcon :text="$t('csr.helpCsrFormat')" />
        </div>

        <ValidationProvider
          name="crs.crsFormat"
          rules="required"
          v-slot="{ errors }"
        >
          <v-select
            :items="csrFormatList"
            name="crs.crsFormat"
            class="form-input"
            v-model="csrFormat"
            data-test="csr-format-select"
          ></v-select>
        </ValidationProvider>
      </div>

      <div class="button-footer">
        <large-button outlined @click="cancel" data-test="cancel-button">{{
          $t('action.cancel')
        }}</large-button>

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
import Vue from 'vue';
import { mapGetters } from 'vuex';
import { ValidationProvider, ValidationObserver } from 'vee-validate';
import HelpIcon from '@/components/ui/HelpIcon.vue';
import LargeButton from '@/components/ui/LargeButton.vue';
import { CsrFormatTypes } from '@/global';

export default Vue.extend({
  components: {
    HelpIcon,
    LargeButton,
    ValidationObserver,
    ValidationProvider,
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
      csrFormatList: Object.values(CsrFormatTypes) as string[],
    };
  },
  computed: {
    ...mapGetters([
      'filteredServiceList',
      'isUsageReadOnly',
      'selectedMemberId',
      'usage',
    ]),

    csrFormat: {
      get(): string {
        return this.$store.getters.csrFormat;
      },
      set(value: string) {
        this.$store.commit('storeCsrFormat', value);
      },
    },
    certificationService: {
      get(): string {
        return this.$store.getters.certificationService;
      },
      set(value: string) {
        this.$store.commit('storeCertificationService', value);
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
    cancel(): void {
      this.$emit('cancel');
    },
  },

  watch: {
    filteredServiceList(val) {
      // Set first certification service selected as default when the list is updated
      if (val?.length === 1) {
        this.certificationService = val[0].name;
      }
    },
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
