<template>
  <v-dialog v-model="dialog" persistent fullscreen lazy class="dialog-full">
    <div class="wrapper">
      <div class="new-content">
        <div class="cert-dialog-header">
          <span class="cert-headline">Certificate</span>
          <v-spacer></v-spacer>
          <i @click="close()" id="close-x"></i>
        </div>

        <template v-if="certificate">
          <div class="cert-hash">
            {{certificate.hash}}
            <v-btn
              outline
              round
              color="primary"
              class="text-capitalize table-button rounded-button"
              type="file"
              @click="deleteCertificate()"
            >Delete</v-btn>
          </div>

          <div>{{certificate.details}}</div>
        </template>
      </div>
    </div>

    <v-dialog v-model="confirm" persistent max-width="290">
      <template v-slot:activator="{ on }">
        <v-btn color="primary" dark v-on="on">Open Dialog</v-btn>
      </template>
      <v-card>
        <v-card-title class="headline">Delete certificate?</v-card-title>
        <v-card-text>Are you sure that you want to delete this certificate?</v-card-text>
        <v-card-actions>
          <v-spacer></v-spacer>
          <v-btn color="darken-1" flat @click="confirm = false">Cancel</v-btn>
          <v-btn color="darken-1" flat @click="doDeleteCertificate()">Yes</v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-dialog>
</template>

<script lang="ts">
import Vue from 'vue';

export default Vue.extend({
  props: {
    certificate: {
      type: Object,
    },
    dialog: {
      type: Boolean,
      required: true,
    },
  },

  data() {
    return {
      confirm: false,
    };
  },
  methods: {
    close() {
      this.$emit('close');
    },
    deleteCertificate() {
      this.confirm = true;
    },
    doDeleteCertificate() {
      console.log('delete cert');
      this.confirm = false;

      const clientId = this.$store.getters.client.id;
      this.$store
        .dispatch('deleteTlsCertificate', {
          clientId,
          hash: this.certificate.hash,
        })
        .then(
          (response) => {},
          (error) => {
            this.$bus.$emit('show-error', error.message);
          },
        );
    },
  },
});
</script>

<style lang="scss">
.v-dialog--fullscreen {
  top: 65px;
}
</style>

<style lang="scss" scoped>
.dialog-full {
  display: flex;
  justify-content: center;
}

.wrapper {
  display: flex;
  justify-content: center;
  padding-top: 60px;
  background-color: white;
  height: 100%;
}

.content {
  max-width: 850px;
  width: 400px;
  border: 1px black solid;
}

.cert-dialog-header {
  display: flex;
  justify-content: center;
  border-bottom: 1px solid #9b9b9b;
  color: #4a4a4a;
  font-family: Roboto;
  font-size: 34px;
  font-weight: 300;
  letter-spacing: 0.5px;
  line-height: 51px;

  .cert-headline {
  }
}

#close-x {
  cursor: pointer;
  font-style: normal;
  font-size: 50px;
  color: #979797;
}

#close-x:before {
  content: '\00d7';
}

.cert-hash {
  margin-top: 30px;
  display: flex;
  justify-content: space-between;
  color: #202020;
  font-family: Roboto;
  font-size: 20px;
  font-weight: 500;
  letter-spacing: 0.5px;
  line-height: 30px;
}

.new-content {
  max-width: 850px;
  width: 100%;
}
</style>

