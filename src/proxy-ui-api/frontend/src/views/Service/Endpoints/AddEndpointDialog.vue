<template>
  <simpleDialog
    :dialog="dialog"
    :width="750"
    title="endpoints.addEndpoint"
    @save="save"
    @cancel="cancel"
    :disableSave="!isValid"
  >
    <div slot="content">
      <ValidationObserver ref="form" v-slot="{ validate, invalid }">

        <div class="dlg-edit-row">
          <div class="dlg-row-title long-row-title">{{$t('endpoints.httpRequestMethod')}}</div>
            <v-select v-model="method" :items="methods" />
        </div>

        <div class="dlg-edit-row">
          <div class="dlg-row-title long-row-title">{{$t('endpoints.path')}}</div>
          <ValidationProvider
            rules="required"
            ref="path"
            name="path"
            class="validation-provider dlg-row-input"
            v-slot="{ errors }">
            <v-text-field
              v-model="path"
              single-line
              :error-messages="errors"
              name="path"
            ></v-text-field>
          </ValidationProvider>
        </div>

        <div class="dlg-edit-row">
          <div class="dlg-row-title long-row-title"></div>
          <div>
            <div>{{$t('endpoints.endpoint_help_1')}}</div>
            <div>{{$t('endpoints.endpoint_help_2')}}</div>
            <div>{{$t('endpoints.endpoint_help_3')}}</div>
            <div>{{$t('endpoints.endpoint_help_4')}}</div>
          </div>
        </div>

      </ValidationObserver>
    </div>

  </simpleDialog>
</template>

<script lang="ts">
  import Vue from 'vue';
  import {ValidationObserver, ValidationProvider} from 'vee-validate';
  import SimpleDialog from '@/components/ui/SimpleDialog.vue';

  export default Vue.extend({
    components: {
      SimpleDialog, ValidationProvider, ValidationObserver,
    },
    props: {
      dialog: {
        type: Boolean,
        required: true,
      },
    },
    data(): any {
      return {
        methods: [
          { text: this.$t('endpoints.all'), value: '*' },
          { text: 'GET', value: 'GET' },
          { text: 'POST', value: 'POST' },
          { text: 'PUT', value: 'PUT' },
          { text: 'PATCH', value: 'PATCH' },
          { text: 'DELETE', value: 'DELETE' },
          { text: 'HEAD', value: 'HEAD' },
          { text: 'OPTIONS', value: 'OPTIONS' },
          { text: 'TRACE', value: 'TRACE' },
        ],
        method: '*',
        path: '/',
      };
    },
    computed: {
      isValid(): boolean {
        return this.path.length >= 1;
      },
    },
    methods: {
      save(): void {
        this.$emit('save', this.method, this.path);
        this.clear();
      },
      cancel(): void {
        this.$emit('cancel');
        this.clear();
      },
      clear(): void {
        this.path = '/';
        this.method = '*';
        (this.$refs.form as InstanceType<typeof ValidationObserver>).reset();
      },
    },
  });

</script>

<style lang="scss" scoped>
@import '../../../assets/dialogs';

.long-row-title {
  min-width: 170px !important;
}

.dlg-row-input {
  margin-left: 0px !important;
}

</style>
