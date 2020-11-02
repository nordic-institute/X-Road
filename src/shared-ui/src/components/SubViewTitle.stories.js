import SubViewTitle from './SubViewTitle.vue';

export default {
  title: 'X-Road/Sub view title',
  component: SubViewTitle,
  argTypes: {
    showClose: { control: 'boolean' },
    title: { control: 'text' },
    close: { action: 'close' },
  },
};

const Template = (args, { argTypes }) => ({
  props: Object.keys(argTypes),
  components: { SubViewTitle },
  template: `<sub-view-title @close="close" v-bind="$props" />`,
});

export const Primary = Template.bind({});
Primary.args = {
  showClose: true,
  title: 'Title text',
};
