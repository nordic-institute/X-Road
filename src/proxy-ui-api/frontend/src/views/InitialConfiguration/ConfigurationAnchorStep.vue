<template>
  <div>
    <div class="action-row">
      <div>{{$t('initialConfiguration.anchor.info')}}</div>
      <upload-configuration-anchor-dialog @uploaded="fetchConfigurationAnchor" initMode />
    </div>
    <div class="row-wrap">
      <div class="label">{{$t('initialConfiguration.anchor.hash')}}</div>
      <template v-if="configuratonAnchor">{{ configuratonAnchor.hash | colonize }}</template>
    </div>

    <div class="row-wrap">
      <div class="label">{{$t('initialConfiguration.anchor.generated')}}</div>
      <template v-if="configuratonAnchor">{{ configuratonAnchor.created_at | formatDateTime }}</template>
    </div>

    <div class="button-footer">
      <v-spacer></v-spacer>
      <div>
        <large-button
          :disabled="!configuratonAnchor"
          @click="done"
          data-test="save-button"
        >{{$t(saveButtonText)}}</large-button>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';
import { ValidationProvider, ValidationObserver } from 'vee-validate';
import HelpIcon from '@/components/ui/HelpIcon.vue';
import LargeButton from '@/components/ui/LargeButton.vue';
import SubViewTitle from '@/components/ui/SubViewTitle.vue';
import { Anchor } from '@/types';
import * as api from '@/util/api';
import UploadConfigurationAnchorDialog from '@/views/Settings/SystemParameters/UploadConfigurationAnchorDialog.vue';

export default Vue.extend({
  components: {
    HelpIcon,
    LargeButton,
    SubViewTitle,
    ValidationObserver,
    ValidationProvider,
    UploadConfigurationAnchorDialog,
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
      showAnchorDialog: false as boolean,
      configuratonAnchor: {} as Anchor,
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
    async fetchConfigurationAnchor() {
      return api
        .get('/system/anchor')
        .then((resp) => (this.configuratonAnchor = resp.data))
        .catch((error) => this.$store.dispatch('showError', error));
    },
    importAnchor(): void {
      console.log('import');
      this.showAnchorDialog = true;
    },
    saveAnchor(): void {
      console.log('saveAnchor');
      this.showAnchorDialog = false;
    },
    done(): void {
      this.$emit('done');
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/wizards';

.label {
  width: 170px;
  min-width: 170px;
}

.action-row {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  width: 100%;
  margin-top: 20px;
  margin-bottom: 50px;
}

.readonly-info-field {
  max-width: 300px;
  height: 60px;
  padding-top: 12px;
}
</style>

