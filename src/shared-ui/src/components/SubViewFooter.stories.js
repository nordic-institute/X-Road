import SubViewFooter from './SubViewFooter.vue';

export default {
  title: 'X-Road/Sub view footer',
  component: SubViewFooter,
  argTypes: {
    showClose: { control: 'boolean' },
    title: { control: 'text' },
    close: { action: 'close' },
  },
};

const Template = (args, { argTypes }) => ({
  props: Object.keys(argTypes),
  components: { SubViewFooter },
  template: `<sub-view-footer @close="close" />`,
});

export const Primary = Template.bind({});
Primary.args = {
  showClose: true,
};
