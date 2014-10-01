require "conf_helper"
require "system_parameter"

java_import Java::ee.cyber.sdsb.common.util.CryptoUtils

class GlobalConfGenerator

  CENTRAL_SERVER_SSL_CERT = "/etc/sdsb/ssl/internal.crt"

  include ConfHelper

  def initialize
    @conf ||= Conf.new(nil,
      Java::ee.cyber.sdsb.common.conf.globalconf.ObjectFactory)

    @factory = @conf.factory
    @conf.init(@factory.createConf(@factory.createGlobalConfType()))

    @root = @conf.root

    # Map database member objects to XML member type objects
    @members_to_membertype = Hash.new
  end

  def generate
    identifier = SystemParameter::sdsb_instance
    if !identifier
      raise "No SDSB identifier set!"
    end

    @root.instanceIdentifier = identifier

    add_members
    add_member_classes
    add_security_categories
    add_security_servers
    add_groups
    add_central_services
    add_pkis
    add_tsps
    add_global_settings
    add_central_server_ssl_cert

    @conf.write_to_string
  end

  # -- Helper methods ---------------------------------------------------------
  private

  def add_members
    SdsbMember.all.each do |member|
      member_type = @factory.createMemberType
      member_type.memberClass = member.member_class.code
      member_type.memberCode = member.member_code
      member_type.name = member.name
      member_type.id = "id#{SecureRandom.hex}"

      member.subsystems.each do |subsystem|
        subsystem_type = @factory.createSubsystemType
        subsystem_type.subsystemCode = subsystem.subsystem_code
        subsystem_type.id = "id#{SecureRandom.hex}"

        member_type.getSubsystem().add(subsystem_type)

        @members_to_membertype[subsystem] = subsystem_type
      end

      @root.getMember().add(member_type)

      # Remember the members, since they are needed for security servers
      @members_to_membertype[member] = member_type
    end
  end

  def add_member_classes
    MemberClass.all.each do |mc|
      mc_type = @factory.createMemberClassType
      mc_type.code = mc.code
      mc_type.description = mc.description

      @root.getMemberClass().add(mc_type)
    end
  end

  def add_security_categories
    SecurityCategory.all.each do |cat|
      cat_type = @factory.createSecurityCategoryType
      cat_type.code = cat.code
      cat_type.description = cat.description

      @root.getSecurityCategory().add(cat_type)
    end
  end

  def add_security_servers
    SecurityServer.all.each do |ss|
      ss_type = @factory.createSecurityServerType
      ss_type.owner = @members_to_membertype[ss.owner]
      ss_type.serverCode = ss.server_code
      ss_type.address = ss.address

      ss.auth_certs.each do |auth_cert|
        hash = CryptoUtils::certHash(auth_cert.certificate.to_java_bytes)
        ss_type.getAuthCertHash().add(hash)
      end

      ss.security_server_clients.each do |client|
        c = @factory.createSecurityServerTypeClient(
          @members_to_membertype[client])
        ss_type.getClient().add(c)
      end

      ss.security_categories.each do |category|
        ss_type.getSecurityCategory().add(category.code)
      end

      @root.getSecurityServer().add(ss_type)
    end
  end

  def add_groups
    GlobalGroup.all.each do |group|
      group_type = @factory.createGlobalGroupType
      group_type.groupCode = group.group_code
      group_type.description = group.description

      group.global_group_members.each do |member|
        if !member.group_member
          raise "All group members must have identifier, but group member "\
              "with id '#{member.id}' has none. There is corrupt data in "\
              "table 'global_group_members'."
        end

        group_type.getMember().add(client_id(member.group_member))
      end

      @root.getGlobalGroup().add(group_type)
    end
  end

  def add_central_services
    CentralService.all.each do |cs|
      cs_type = @factory.createCentralServiceType
      cs_type.serviceCode = cs.service_code

      if cs.target_service != nil
        cs_type.implementingService = service_id(cs.target_service)
      end

      @root.getCentralService().add(cs_type)
    end
  end

  def add_pkis
    Pki.all.each do |pki|
      pki_type = @factory.createPkiType
      pki_type.name = pki.name
      pki_type.authenticationOnly = pki.authentication_only

      pki_type.setTopCA(convert_ca(pki.top_ca))

      pki.intermediate_cas.each do |cainfo|
        pki_type.getIntermediateCA().add(convert_ca(cainfo))
      end

      if pki.name_extractor_method_name and not pki.name_extractor_method_name.empty? then 
        name_extractor_type = @factory.createNameExtractorType
        name_extractor_type.memberClass = pki.name_extractor_member_class
        name_extractor_type.methodName = pki.name_extractor_method_name
        pki_type.nameExtractor = name_extractor_type
      end

      @root.getPki().add(pki_type)
    end
  end

  def add_tsps
    ApprovedTsp.all.each do |tsp|
      tsp_type = @factory.createApprovedTspType
      tsp_type.name = tsp.name
      tsp_type.url = tsp.url
      tsp_type.cert = tsp.cert.to_java_bytes

      @root.getApprovedTsp().add(tsp_type)
    end
  end

  def add_global_settings
    gs_type = @factory.createGlobalSettingsType

    gs_type.managementRequestServiceAddress =
      SystemParameter::management_service_url

    gs_type.managementRequestServiceId =
      Java::ee.cyber.sdsb.common.identifier.ClientId.create(
        @root.instanceIdentifier,
        SystemParameter::management_service_id_class,
        SystemParameter::management_service_id_code,
        SystemParameter::management_service_id_subsystem)

    @root.globalSettings = gs_type
  end

  def add_central_server_ssl_cert
    cert_lines = get_central_server_ssl_cert
    cert_base64 = cert_lines[1..-2].join # exclude --- BEGIN... lines

    @root.centralServerSslCert = Base64.decode64(cert_base64).to_java_bytes
  end

  def convert_ca(cainfo)
    cainfo_type = @factory.createCaInfoType
    if cainfo.cert
      cainfo_type.cert = cainfo.cert.to_java_bytes
    end

    cainfo.ocsp_infos.each do |ocsp|
      ocsp_info_type = @factory.createOcspInfoType
      ocsp_info_type.url = ocsp.url
      if ocsp.cert
        ocsp_info_type.cert = ocsp.cert.to_java_bytes
      end
      cainfo_type.getOcsp().add(ocsp_info_type)
    end
    cainfo_type
  end

  def client_id(c)
    Java::ee.cyber.sdsb.common.identifier.ClientId.create(
      c.sdsb_instance, c.member_class, c.member_code, c.subsystem_code)
  end

  def service_id(s)
    Java::ee.cyber.sdsb.common.identifier.ServiceId.create(
      s.sdsb_instance,
      s.member_class,
      s.member_code,
      s.subsystem_code,
      s.service_code,
      s.service_version)
  end

  def get_central_server_ssl_cert
    IO.readlines(CENTRAL_SERVER_SSL_CERT)
  end

end

