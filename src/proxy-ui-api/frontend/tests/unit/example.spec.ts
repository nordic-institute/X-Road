import { mount, createLocalVue } from '@vue/test-utils';
import { SilenceWarnHack } from './silenceWarnHack';
import VueRouter from 'vue-router';
import Vuetify from 'vuetify';
import Toolbar from '@/components/ServiceIcon.vue';

const silenceWarnHack = new SilenceWarnHack();

const localVue = createLocalVue();
localVue.use(VueRouter);

describe('Toolbar', () => {
  let wrapper: any;
  let vuetify;

  const routes = [
    { path: '/clients', name: 'clients' },
  ];

  const router = new VueRouter({ routes });


  beforeEach(() => {

    silenceWarnHack.enable();
    vuetify = new Vuetify();
    localVue.use(Vuetify);
    silenceWarnHack.disable();

    wrapper = mount(Toolbar, {
      localVue,
      router,
      propsData: {
        service: {
          ssl_auth: true,
        },
      },
      mocks: {
        $t: () => 'localized text',
      },
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
