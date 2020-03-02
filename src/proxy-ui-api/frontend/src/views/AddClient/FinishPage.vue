<template>
  <div class="content">
    <p>
      {{$t('wizard.finish.infoLine1')}}
      <br />
      {{$t('wizard.finish.infoLine2')}}
    </p>
    <br />
    <p>{{$t('wizard.finish.todo1')}}</p>
    <p>{{$t('wizard.finish.todo2')}}</p>
    <p>{{$t('wizard.finish.todo3')}}</p>
    <br />
    <br />
    <p>{{$t('wizard.finish.note')}}</p>
    <p></p>

    <div class="button-footer">
      <div class="button-group">
        <large-button outlined @click="cancel" :disabled="!disableDone">{{$t('action.cancel')}}</large-button>
      </div>
      <large-button @click="done" :disabled="disableDone">{{$t('action.submit')}}</large-button>
    </div>
  </div>
</template>

<script lang="ts">
import Vue from 'vue';
import { mapGetters } from 'vuex';
import LargeButton from '@/components/ui/LargeButton.vue';
import { ValidationProvider, ValidationObserver } from 'vee-validate';

export default Vue.extend({
  components: {
    LargeButton,
    ValidationObserver,
    ValidationProvider,
  },
  computed: {
    ...mapGetters(['csrForm']),
  },
  data() {
    return {
      disableDone: false,
    };
  },
  methods: {
    cancel(): void {
      this.$emit('cancel');
    },
    done(): void {
      this.$store.dispatch('createClient').then(
        (response) => {
          this.disableDone = false;
          this.$emit('done');
        },
        (error) => {
          this.$bus.$emit('show-error', error.message);
        },
      );
    },
    generateCsr(): void {
      this.$store.dispatch('generateCsr').then(
        (response) => {
          this.disableDone = false;
        },
        (error) => {
          this.$bus.$emit('show-error', error.message);
        },
      );
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../../assets/wizards';
</style>

