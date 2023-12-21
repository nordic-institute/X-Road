<!--
   The MIT License

   Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
   Copyright (c) 2018 Estonian Information System Authority (RIA),
   Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
   Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)

   Permission is hereby granted, free of charge, to any person obtaining a copy
   of this software and associated documentation files (the "Software"), to deal
   in the Software without restriction, including without limitation the rights
   to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
   copies of the Software, and to permit persons to whom the Software is
   furnished to do so, subject to the following conditions:

   The above copyright notice and this permission notice shall be included in
   all copies or substantial portions of the Software.

   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
   FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
   AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
   LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
   OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
   THE SOFTWARE.
 -->
<template>
  <div style="display: inline-block">
    <xrd-button
      v-if="showUploadButton"
      :data-test="`configuration-part-${configurationPart.content_identifier}-upload`"
      text
      @click="showUploadDialog = true"
    >
      {{ $t('action.upload') }}
    </xrd-button>
    <upload-configuration-part-dialog
      v-if="showUploadDialog"
      :configuration-type="configurationType"
      :content-identifier="configurationPart.content_identifier"
      @cancel="showUploadDialog = false"
      @save="
        showUploadDialog = false;
        $emit('save');
      "
    />
  </div>
</template>

<script lang="ts">
import Vue, { defineComponent, PropType } from 'vue';
import { mapState } from 'pinia';
import { ConfigurationPart, ConfigurationType } from '@/openapi-types';
import { useUser } from '@/store/modules/user';
import { Permissions } from '@/global';
import UploadConfigurationPartDialog from '@/components/configurationParts/UploadConfigurationPartDialog.vue';

export default defineComponent({
  components: { UploadConfigurationPartDialog },
  props: {
    configurationType: {
      type: String as PropType<ConfigurationType>,
      required: true,
    },
    configurationPart: {
      type: Object as PropType<ConfigurationPart>,
      required: true,
    },
  },
  emits: ['save'],
  data() {
    return {
      showUploadDialog: false,
    };
  },
  computed: {
    ...mapState(useUser, ['hasPermission']),

    showUploadButton(): boolean {
      return (
        this.hasPermission(Permissions.UPLOAD_CONFIGURATION_PART) &&
        this.configurationPart.optional
      );
    },
  },
  methods: {},
});
</script>
