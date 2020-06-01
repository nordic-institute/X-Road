<template>
  <div>
    <ValidationObserver ref="form1" v-slot="{ validate, invalid }">
      {{$t('initialConfiguration.pin.info1')}}
      <div class="row-wrap">
        <div class="label">{{$t('initialConfiguration.pin.pin')}}</div>
        <ValidationProvider name="pin" rules="required|password:@confirmPin" v-slot="{ errors }">
          <v-text-field
            class="form-input"
            name="pin"
            type="password"
            v-model="pin"
            :error-messages="errors"
            data-test="pin-input"
          ></v-text-field>
        </ValidationProvider>
      </div>

      <div class="row-wrap">
        <div class="label">{{$t('initialConfiguration.pin.confirmPin')}}</div>
        <ValidationProvider name="confirmPin" rules="required" v-slot="{ errors }">
          <v-text-field
            class="form-input"
            name="confirmPin"
            type="password"
            v-model="pinConfirm"
            :error-messages="errors"
            data-test="confirm-pin-input"
          ></v-text-field>
        </ValidationProvider>
      </div>
      {{$t('initialConfiguration.pin.info2')}}
      <br />
      <br />
      {{$t('initialConfiguration.pin.info3')}}
      <div class="button-footer">
        <v-spacer></v-spacer>
        <div>
          <large-button
            @click="previous"
            outlined
            class="previous-button"
            data-test="previous-button"
          >{{$t('action.previous')}}</large-button>
          <large-button
            :disabled="invalid"
            :loading="saveBusy"
            @click="done"
            data-test="save-button"
          >{{$t('action.submit')}}</large-button>
        </div>
      </div>
    </ValidationObserver>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { ValidationProvider, ValidationObserver } from 'vee-validate';
import LargeButton from '@/components/ui/LargeButton.vue';
import * as api from '@/util/api';
import { extend } from 'vee-validate';
import i18n from '@/i18n';

const PASSWORD_MATCH_ERROR: string = i18n.t('initialConfiguration.pin.pinMatchError') as string;

extend('password', {
  params: ['target'],
  validate(value, { target }: Record<string, any>) {
    return value === target;
  },
  message: PASSWORD_MATCH_ERROR,
});

export default Vue.extend({
  components: {
    LargeButton,
    ValidationObserver,
    ValidationProvider,
  },
  props: {
    saveBusy: {
      type: Boolean,
    },
  },
  data() {
    return {
      pin: '' as string,
      pinConfirm: '' as string,
    };
  },

  methods: {
    done(): void {
      this.$emit('done', this.pin);
    },
    previous(): void {
      this.$emit('previous');
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

