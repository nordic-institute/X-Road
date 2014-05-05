def create_test_data
  DistributedFiles.add_file("globalconf", 
    "<contents of globalconf>")
  DistributedFiles.add_file("identifiermapping", 
    "<contents of identifiermapping>")
    
  SystemParameter.create(key: SystemParameter::CONF_SIGN_KEY_ID, 
    value: "consumer")
  SystemParameter.create(key: SystemParameter::CONF_SIGN_ALGO_ID, 
    value: "SHA-256")
end

def do_simple_test
  globalconf = DistributedFiles.get("globalconf.xml")
  puts "GlobalConf: #{globalconf}"
  
  identifiermapping = DistributedFiles.get("identifiermapping.xml")
  puts "IdentifierMapping: #{identifiermapping}"
end

def perform_test
  create_test_data
  do_simple_test
ensure
  DistributedFiles.delete_all
  SystemParameter.delete_all
end
perform_test