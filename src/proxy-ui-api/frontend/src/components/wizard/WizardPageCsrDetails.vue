<template>
  <div>
    <ValidationObserver ref="form1" v-slot="{ validate, invalid }">
      <div class="row-wrap">
        <div class="label">
          {{$t('csr.usage')}}
          <helpIcon :text="$t('csr.helpUsage')" />
        </div>

        <ValidationProvider name="crs.usage" rules="required" v-slot="{ errors }">
          <v-select
            :items="usageList"
            class="form-input"
            v-model="usage"
            :disabled="isUsageReadOnly"
            data-test="csr-usage-select"
          ></v-select>
        </ValidationProvider>
      </div>

      <div class="row-wrap" v-if="usage === usageTypes.SIGNING">
        <div class="label">
          {{$t('csr.client')}}
          <helpIcon :text="$t('csr.helpClient')" />
        </div>

        <ValidationProvider name="crs.client" rules="required" v-slot="{ errors }">
          <v-select
            :items="localMembersIds"
            item-text="id"
            item-value="id"
            class="form-input"
            v-model="client"
            data-test="csr-client-select"
          ></v-select>
        </ValidationProvider>
      </div>

      <div class="row-wrap">
        <div class="label">
          {{$t('csr.certificationService')}}
          <helpIcon :text="$t('csr.helpCertificationService')" />
        </div>

        <ValidationProvider name="crs.certService" rules="required" v-slot="{ errors }">
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
          {{$t('csr.csrFormat')}}
          <helpIcon :text="$t('csr.helpCsrFormat')" />
        </div>

        <ValidationProvider name="crs.crsFormat" rules="required" v-slot="{ errors }">
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
        <large-button outlined @click="cancel" data-test="cancel-button">{{$t('action.cancel')}}</large-button>

        <div>
          <large-button
            v-if="showPreviousButton"
            @click="previous"
            outlined
            class="previous-button"
            data-test="previous-button"
          >{{$t('action.previous')}}</large-button>
          <large-button
            :disabled="invalid"
            @click="done"
            data-test="save-button"
          >{{$t(saveButtonText)}}</large-button>
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
import SubViewTitle from '@/components/ui/SubViewTitle.vue';
import { Key, Token } from '@/openapi-types';
import { UsageTypes, CsrFormatTypes } from '@/global';
import * as api from '@/util/api';

export default Vue.extend({
  components: {
    HelpIcon,
    LargeButton,
    SubViewTitle,
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
      usageTypes: UsageTypes,
      usageList: Object.values(UsageTypes),
      csrFormatList: Object.values(CsrFormatTypes),
    };
  },
  computed: {
    ...mapGetters([
      'localMembersIds',
      'filteredServiceList',
      'isUsageReadOnly',
    ]),

    usage: {
      get(): string {
        return this.$store.getters.usage;
      },
      set(value: string) {
        this.$store.commit('storeUsage', value);
      },
    },
    csrFormat: {
      get(): string {
        return this.$store.getters.csrFormat;
      },
      set(value: string) {
        this.$store.commit('storeCsrFormat', value);
      },
    },
    client: {
      get(): string {
        return this.$store.getters.csrClient;
      },
      set(value: string) {
        this.$store.commit('storeCsrClient', value);
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
      if (val && val.length === 1) {
        this.certificationService = val[0].name;
      }
    },
    localMembersIds(val) {
      // Set first client selected as default when the list is updated
      if (val && val.length === 1) {
        this.client = val[0].id;
      }
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/wizards';
</style>

