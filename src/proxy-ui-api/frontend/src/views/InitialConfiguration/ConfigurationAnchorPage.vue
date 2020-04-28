<template>
  <div>
    <ValidationObserver ref="form1" v-slot="{ validate, invalid }">
      {{$t('initialConfiguration.anchor.info')}}
      <large-button
        @click="importAnchor"
                    outlined
        data-test="save-button"
      >{{$t('initialConfiguration.anchor.import')}}</large-button>

      <div class="row-wrap">
        <div class="label">{{$t('initialConfiguration.anchor.hash')}}</div>blah 1
      </div>

      <div class="row-wrap">
        <div class="label">{{$t('initialConfiguration.anchor.generated')}}</div>blah
      </div>

      <div class="button-footer">
        <large-button outlined @click="cancel" data-test="cancel-button">{{$t('action.cancel')}}</large-button>

        <div>
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
      csrFormatList: Object.values(CsrFormatTypes),
    };
  },
  computed: {
    ...mapGetters([
      'filteredServiceList',
      'isUsageReadOnly',
      'selectedMember',
      'usage',
    ]),
  },
  methods: {
    importAnchor(): void {
      console.log('import');
    },
    done(): void {
      this.$emit('done');
    },
    cancel(): void {
      this.$emit('cancel');
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

