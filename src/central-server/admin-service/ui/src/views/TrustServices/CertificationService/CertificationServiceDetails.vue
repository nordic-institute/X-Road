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
<!--
  Certification Service details view
-->
<template>
  <main id="certification-service-details" class="mt-8">
    <!-- Certification Service Details -->

    <info-card
      class="mb-6"
      :title-text="
        $t('trustServices.trustService.details.subjectDistinguishedName')
      "
      :info-text="
        certificationServiceStore.currentCertificationService
          ?.subject_distinguished_name || ''
      "
      data-test="subject-distinguished-name-card"
    />

    <info-card
      class="mb-6"
      :title-text="
        $t('trustServices.trustService.details.issuerDistinguishedName')
      "
      :info-text="
        certificationServiceStore.currentCertificationService
          ?.issuer_distinguished_name || ''
      "
      data-test="issuer-distinguished-name-card"
    />

    <div class="certification-service-info-card-group">
      <info-card
        :title-text="$t('trustServices.validFrom')"
        data-test="valid-from-card"
      >
        <date-time
          :value="
            certificationServiceStore.currentCertificationService?.not_before
          "
        />
      </info-card>
      <info-card
        :title-text="$t('trustServices.validTo')"
        data-test="valid-to-card"
      >
        <date-time
          :value="
            certificationServiceStore.currentCertificationService?.not_after
          "
        />
      </info-card>
    </div>

    <div
      v-if="showDelete"
      class="delete-action"
      data-test="delete-trust-service"
      @click="showDeleteDialog = true"
    >
      <div>
        <v-icon
          class="xrd-large-button-icon"
          :color="colors.Purple100"
          icon="mdi-close-circle"
        />
      </div>
      <div class="action-text">
        {{
          `${$t('trustServices.trustService.delete.action')} "${
            certificationServiceStore.currentCertificationService?.name
          }"`
        }}
      </div>
    </div>
    <xrd-confirm-dialog
      v-if="
        certificationServiceStore.currentCertificationService &&
        showDeleteDialog
      "
      data-test="delete-trust-service-confirm-dialog"
      :loading="deleting"
      :data="{
        name: certificationServiceStore.currentCertificationService.name,
      }"
      title="trustServices.trustService.delete.confirmationDialog.title"
      text="trustServices.trustService.delete.confirmationDialog.message"
      @cancel="showDeleteDialog = false"
      @accept="confirmDelete"
    />
  </main>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import InfoCard from '@/components/ui/InfoCard.vue';
import { mapActions, mapState, mapStores } from 'pinia';
import { useCertificationService } from '@/store/modules/trust-services';
import { Colors, Permissions, RouteName } from '@/global';
import { useNotifications } from '@/store/modules/notifications';
import { useUser } from '@/store/modules/user';
import DateTime from '@/components/ui/DateTime.vue';

/**
 * Component for a Certification Service details view
 */
export default defineComponent({
  name: 'CertificationServiceDetails',
  components: {
    DateTime,
    InfoCard,
  },
  data() {
    return {
      colors: Colors,
      showDeleteDialog: false,
      deleting: false,
    };
  },
  computed: {
    ...mapStores(useCertificationService),
    ...mapState(useUser, ['hasPermission']),
    showDelete(): boolean {
      return this.hasPermission(Permissions.DELETE_APPROVED_CA);
    },
  },
  methods: {
    ...mapActions(useNotifications, ['showError', 'showSuccess']),
    confirmDelete(): void {
      if (!this.certificationServiceStore.currentCertificationService) return;
      this.deleting = true;
      this.certificationServiceStore
        .deleteById(
          this.certificationServiceStore.currentCertificationService.id,
        )
        .then(() => {
          this.showDeleteDialog = false;
          this.deleting = false;
          this.showSuccess(
            this.$t('trustServices.trustService.delete.success'),
            true,
          );
          this.$router.replace({ name: RouteName.TrustServices });
        })
        .catch((error) => {
          this.showDeleteDialog = false;
          this.deleting = false;
          this.showError(error);
        });
    },
  },
});
</script>

<style lang="scss" scoped>
@import '@/assets/colors';
@import '@/assets/tables';

.card-title {
  font-size: 12px;
  text-transform: uppercase;
  color: $XRoad-Black70;
  font-weight: bold;
  padding-top: 5px;
  padding-bottom: 5px;
}

.delete-action {
  margin-top: 34px;
  color: $XRoad-Link;
  cursor: pointer;
  display: flex;
  flex-direction: row;

  .action-text {
    margin-top: 2px;
  }
}

.certification-service-info-card-group {
  margin-top: 24px;
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: flex-end;

  margin-bottom: 24px;

  .details-card {
    width: 100%;

    &:first-child {
      margin-right: 30px;
    }

    &:last-child {
      margin-left: 30px;
    }
  }
}
</style>
