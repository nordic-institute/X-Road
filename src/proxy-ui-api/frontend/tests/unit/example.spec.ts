import { mount, createLocalVue } from '@vue/test-utils';
import { SilenceWarnHack } from './silenceWarnHack';
import VueRouter from 'vue-router';
import Vuetify from 'vuetify';
import Toolbar from '@/components/Toolbar.vue';

const silenceWarnHack = new SilenceWarnHack();

describe('Toolbar', () => {
  let wrapper: any;

  const routes = [
    { path: '/clients', name: 'clients' },
  ];

  const router = new VueRouter({ routes });
  let localVue = null;

  beforeEach(() => {
    silenceWarnHack.enable();
    localVue = createLocalVue();
    localVue.use(VueRouter);
    localVue.use(Vuetify);
    silenceWarnHack.disable();

    wrapper = mount(Toolbar, {
      localVue,
      router,
    });
  });

  test('is a Vue instance', () => {
    expect(wrapper.isVueInstance()).toBeTruthy();
  });

  // `it' and `expect's ready to go now.
  it('snapshot test', () => {
    expect(wrapper.element).toMatchSnapshot();
  });

});
