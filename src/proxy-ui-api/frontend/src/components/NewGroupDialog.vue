<template>
  <v-dialog :value="dialog" width="550" persistent>
    <v-card class="xroad-card">
      <v-card-title>
        <span class="headline">Add Local Group</span>
        <v-spacer/>
        <i @click="cancel()" id="close-x"></i>
      </v-card-title>
      <v-card-text>
        <div class="edit-row">
          <div class="row-title">Code</div>
          <v-text-field
            v-model="code"
            hint="insert code"
            single-line
            hide-details
            class="row-input"
          ></v-text-field>
        </div>

        <div class="edit-row">
          <div class="row-title">Description</div>
          <v-text-field v-model="description" hint single-line hide-details class="row-input"></v-text-field>
        </div>
      </v-card-text>
      <v-card-actions class="xr-card-actions">
        <v-spacer></v-spacer>
        <v-btn
          color="primary"
          round
          outline
          class="mb-2 rounded-button elevation-0 xr-big-button button-margin"
          @click="cancel()"
        >Cancel</v-btn>

        <v-btn
          color="primary"
          round
          class="mb-2 rounded-button elevation-0 xr-big-button button-margin"
          @click="save()"
        >ADD</v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script lang="ts">
import Vue from 'vue';
import axios from 'axios';

export default Vue.extend({
  props: {
    id: {
      type: String,
      required: true,
    },
    dialog: {
      type: Boolean,
      required: true,
    },
  },

  data() {
    return {
      code: undefined,
      description: undefined,
    };
  },
  methods: {
    cancel() {
      this.$emit('cancel');
    },
    save() {
      axios
        .post(`/clients/${this.id}/groups`, {
          code: this.code,
          description: this.description,
        })
        .then((res) => {
          this.$bus.$emit('show-success', 'localGroup.descSaved');
          this.$emit('groupAdded');
        })
        .catch((error) => {
          this.$bus.$emit('show-error', error.message);
        });
    },
  },
});
</script>

<style lang="scss" scoped>
@import '../assets/colors';

.edit-row {
  display: flex;
  align-content: center;
  align-items: flex-end;
  margin-top: 0px;
  margin-bottom: 20px;

  .row-title {
    min-width: 100px;
  }

  .row-input {
    margin-left: 10px;
  }
}

.button-margin {
  margin-right: 14px;
}

#close-x {
  font-family: Roboto;
  font-size: 34px;
  font-weight: 300;
  letter-spacing: 0.5px;
  line-height: 21px;

  cursor: pointer;
  font-style: normal;
  font-size: 50px;
  color: $XRoad-Grey40;
}

#close-x:before {
  content: '\00d7';
}
</style>

