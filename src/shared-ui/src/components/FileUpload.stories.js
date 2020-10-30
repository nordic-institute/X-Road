import FileUpload from './FileUpload.vue';
import LargeButton from './LargeButton.vue';


export default {
  title: 'X-Road/File upload',
  component: FileUpload,
  argTypes: {
    accepts: { control: 'text' },
    fileChanged: { action: 'fileChanged' },
  },
};

const Template = (args, { argTypes }) => ({
  props: Object.keys(argTypes),
  components: { FileUpload, LargeButton },
  template:
    `
    <file-upload
    v-bind="$props"
    @fileChanged="fileChanged"
    v-slot="{ upload }"
    >
      <large-button @click="upload">
        Upload
      </large-button>
    </file-upload>    
    `,
});

export const Primary = Template.bind({});
Primary.args = {
  accepts: ".tar, .jpg, .png, .pem, .cer, .der, .txt",
};

