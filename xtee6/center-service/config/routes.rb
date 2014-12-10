CenterService::Application.routes.draw do
  root :to => 'management_requests#create', :via => :post
  match 'gen_conf' => 'conf_generator#index', :via => :get
end
