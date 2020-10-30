import SimpleDialog from './SimpleDialog.vue';

/*
    // Title of the dialog
    title: {
      type: String,
      required: false,
    },
    // Dialog visible / hidden
    dialog: {
      type: Boolean,
      required: true,
    },
    // Disable save button
    disableSave: {
      type: Boolean,
    },
    cancelButtonText: {
      type: String,
      default: 'action.cancel',
    },
    // Text of the save button
    saveButtonText: {
      type: String,
      default: 'action.add',
    },
    width: {
      type: Number,
      default: 550,
    },
    showClose: {
      type: Boolean,
      default: true,
    },
    // Set save button loading spinner
    loading: {
      type: Boolean,
      default: false,
    },

*/


export default {
  title: 'X-Road/Simple dialog',
  component: SimpleDialog,
  argTypes: {
    width: { control: 'boolean' },
    dialog: { control: 'boolean' },
    loading: { control: 'boolean' },
    disableSave: { control: 'boolean' },
    title: { control: 'text' },
    content: { control: 'text' },
    saveButtonText: { control: 'text' },
    cancelButtonText: { control: 'text' },
    showClose: { control: 'boolean' },
    width: {control: 'number' },
    save: { action: 'save' },
    cancel: { action: 'cancel' }
  },
};

const Template = (args, { argTypes }) => ({
  props: Object.keys(argTypes),
  components: { SimpleDialog },
  template:
    `<simple-dialog @cancel="cancel" @save="save" v-bind="$props">
    <template slot="content">{{content}}</template>"
    </simple-dialog>`,
});

export const Primary = Template.bind({});
Primary.args = { 
  primary: true,
  dialog: true,
  loading: false,
  title: 'Title text',
  content: `Lorem ipsum dolor sit amet, 
  consectetur adipiscing elit. Nam porta vehicula dolor in molestie. 
  Praesent sem sem, pretium ut massa sit amet, ultrices volutpat elit. `
};

export const LongText = Template.bind({});
LongText.args = { 
  dialog: true,
  loading: false,
  title: 'Title text',
  content: `This is a very, very long text: Lorem ipsum dolor sit amet, 
  consectetur adipiscing elit. Nam porta vehicula dolor in molestie. 
  Praesent sem sem, pretium ut massa sit amet, ultrices volutpat elit. 
  Ut ac arcu at libero vestibulum condimentum ut sed mi. Nullam egestas 
  eu risus at sollicitudin. Sed eleifend pretium eleifend. Vivamus 
  aliquet quam et gravida iaculis. Duis quis gravida nibh, vel faucibus felis.`,
};
