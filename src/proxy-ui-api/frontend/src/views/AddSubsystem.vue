<template>
  <v-container>
    <v-layout text-xs-center wrap column>
      <v-flex mb-4>
        <h1 class="display-1 font-weight-bold mb-3">Add a Subsystem</h1>
        <p class="subheading font-weight-regular">Placeholder</p>
      </v-flex>
      <v-layout mb-4 row>
        <v-flex mb-4>Member Name</v-flex>
        <v-flex mb-4>{{member.name}}</v-flex>
      </v-layout>
      <v-layout mb-4 row>
        <v-flex mb-4>Member Class</v-flex>
        <v-flex mb-4>{{member.class}}</v-flex>
      </v-layout>
      <v-layout mb-4 row>
        <v-flex mb-4>Member Code</v-flex>
        <v-flex mb-4>{{member.code}}</v-flex>
      </v-layout>

      <v-layout mb-4 row>
        <v-flex mb-4>Subsystem Name</v-flex>
        <v-flex mb-4>
          <v-text-field label="Regular"></v-text-field>
        </v-flex>
      </v-layout>

      <v-flex mb-4>
        <v-btn color="primary" @click="cancel" round outline class="mb-2 rounded-button">Cancel</v-btn>

        <v-btn
          color="primary"
          @click="addSubsystem"
          round
          dark
          class="mb-2 rounded-button"
        >Add subsystem</v-btn>
      </v-flex>
    </v-layout>
  </v-container>
</template>

<script>
export default {
  data: () => ({
    member: {
      name: 'Kuikka',
      class: 'Org',
      code: '111',
    },
  }),
  methods: {
    addSubsystem() {},
    cancel() {
      this.$router.replace('/');
    },
  },
};
</script>

<style lang="scss" scoped>
</style>

