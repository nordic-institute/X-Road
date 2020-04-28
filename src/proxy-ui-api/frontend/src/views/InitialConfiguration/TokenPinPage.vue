<template>
  <div>
    <ValidationObserver ref="form1" v-slot="{ validate, invalid }">
      {{$t('initialConfiguration.pin.info1')}}
      <div class="row-wrap">
        <div class="label">{{$t('initialConfiguration.pin.pin')}}</div>

        <v-text-field
          class="form-input"
          name="pin"
          type="password"
          v-model="pin"
          :error-messages="errors"
          data-test="pin-input"
        ></v-text-field>
      </div>

      <div class="row-wrap">
        <div class="label">{{$t('initialConfiguration.pin.confirmPin')}}</div>

        <v-text-field
          class="form-input"
          name="confirmPin"
          type="password"
          v-model="pinConfirm"
          :error-messages="errors"
          data-test="confirm-pin-input"
        ></v-text-field>
      </div>
      {{$t('initialConfiguration.pin.info2')}}
      <br />
      <br />
      {{$t('initialConfiguration.pin.info3')}}
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
import { Key, Token } from '@/types';
import { CsrFormatTypes } from '@/global';
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
      pin: '',
      pinConfirm: '',
    };
  },
  computed: {
    ...mapGetters([
      'filteredServiceList',
      'isUsageReadOnly',
      'selectedMember',
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
      if (val && val.length === 1) {
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

