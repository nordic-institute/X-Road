#
# The MIT License
# Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
# THE SOFTWARE.
#

java_import Java::ee.ria.xroad.common.util.CryptoUtils

class SharedParametersGenerator

  def initialize()
    @marshaller = ConfMarshaller.new(
       get_object_factory(), get_root_type_creator())

    @client_ids_generated = 0

    # We must remember security server owners and clients.
    @clients_to_client_types = {}

    Rails.logger.debug(
      "Initialized shared parameters generator: #{self.inspect()}")
  end

  def generate
    add_instance_identifier()
    add_approved_cas()
    add_approved_tsas()
    add_members()
    add_security_servers()
    add_global_groups()
    add_central_services()
    add_global_settings()

    return @marshaller.write_to_string()
  end

  private

  def get_object_factory
    return \
      Java::ee.ria.xroad.common.conf.globalconf.sharedparameters.ObjectFactory
  end

  def get_root_type_creator
    return Proc.new() do |factory|
      factory.createConf(factory.createSharedParametersType())
    end
  end

  def add_instance_identifier
    @marshaller.root.instanceIdentifier = SystemParameter.instance_identifier
  end


  def add_approved_cas
    ApprovedCa.find_each do |each_approved_ca|
      approved_ca_type = @marshaller.factory.createApprovedCAType()

      auth_only = each_approved_ca.authentication_only

      approved_ca_type.name = each_approved_ca.name
      approved_ca_type.authenticationOnly = auth_only

      approved_ca_type.topCA = get_ca_info_type(each_approved_ca.top_ca)

      each_approved_ca.intermediate_cas.find_each do |each_intermediate_ca|
        approved_ca_type.getIntermediateCA().add(
            get_ca_info_type(each_intermediate_ca))
      end

      if !auth_only
        identifier_decoder_type =
            @marshaller.factory.createIdentifierDecoderType()
        identifier_decoder_type.memberClass =
            each_approved_ca.identifier_decoder_member_class
        identifier_decoder_type.methodName =
            each_approved_ca.identifier_decoder_method_name
        approved_ca_type.identifierDecoder = identifier_decoder_type
      end

      @marshaller.root.getApprovedCA().add(approved_ca_type)
    end
  end

  def add_approved_tsas
    ApprovedTsa.find_each do |each|
      approved_tsa_type = @marshaller.factory.createApprovedTSAType()

      approved_tsa_type.name = each.name
      approved_tsa_type.url = each.url
      approved_tsa_type.cert = each.cert.to_java_bytes()

      @marshaller.root.getApprovedTSA().add(approved_tsa_type)
    end
  end

  def add_members
    XroadMember.find_each do |each_member|
      member_type = @marshaller.factory.createMemberType()
      member_type.name = each_member.name
      member_type.memberCode = each_member.member_code
      member_type.id = generate_client_id()

      member_class = each_member.member_class
      member_class_type = @marshaller.factory.createMemberClassType()
      member_class_type.code = member_class.code
      member_class_type.description = member_class.description
      member_type.memberClass = member_class_type

      each_member.subsystems.find_each do |each_subsystem|
        subsystem_type = @marshaller.factory.createSubsystemType()
        subsystem_type.subsystemCode = each_subsystem.subsystem_code
        subsystem_type.id = generate_client_id()

        @clients_to_client_types[each_subsystem] = subsystem_type
        member_type.getSubsystem().add(subsystem_type)
      end

      @clients_to_client_types[each_member] = member_type
      @marshaller.root.getMember().add(member_type)
    end
  end

  def add_security_servers
    SecurityServer.find_each do |each_server|
      server_type = @marshaller.factory.createSecurityServerType()

      server_type.owner = @clients_to_client_types[each_server.owner]
      server_type.serverCode = each_server.server_code
      server_type.address = each_server.address

      each_server.auth_certs.find_each do |each_cert|
        hash = CryptoUtils::certHash(each_cert.cert.to_java_bytes())
        server_type.getAuthCertHash.add(hash)
      end

      each_server.security_server_clients.find_each do |each_client|
        client_type = @marshaller.factory.createSecurityServerTypeClient(
            @clients_to_client_types[each_client])
        server_type.getClient().add(client_type)
      end

      @marshaller.root.getSecurityServer().add(server_type)
    end
  end

  def add_global_groups
    GlobalGroup.find_each do |each_group|
      group_type = @marshaller.factory.createGlobalGroupType()
      group_type.groupCode = each_group.group_code
      group_type.description = each_group.description

      each_group.global_group_members.find_each do |each_member|
        group_type.getGroupMember().add(
            get_client_identifier(each_member.group_member))
      end

      @marshaller.root.getGlobalGroup().add(group_type)
    end
  end

  def add_central_services
    CentralService.find_each do |each|
      service_type = @marshaller.factory.createCentralServiceType()
      service_type.serviceCode = each.service_code
      service_type.implementingService =
        get_service_identifier(each.target_service)

      @marshaller.root.getCentralService().add(service_type)
    end
  end

  def add_global_settings
    global_settings_type = @marshaller.factory.createGlobalSettingsType()

    MemberClass.find_each do |each|
      member_class_type = @marshaller.factory.createMemberClassType()
      member_class_type.code = each[:code]
      member_class_type.description = each[:description]

      global_settings_type.getMemberClass.add(member_class_type)
    end

    global_settings_type.ocspFreshnessSeconds =
        SystemParameter.ocsp_freshness_seconds

    @marshaller.root.globalSettings = global_settings_type
  end

  def get_ca_info_type(ca_info)
    ca_info_type = @marshaller.factory.createCaInfoType()
    ca_cert = ca_info.cert
    ca_info_type.cert = ca_cert.to_java_bytes() if ca_cert

    ca_info.ocsp_infos.each do |each|
      ocsp_info_type = @marshaller.factory.createOcspInfoType()
      ocsp_info_type.url = each.url
      ocsp_cert = each.cert
      ocsp_info_type.cert = ocsp_cert.to_java_bytes() if ocsp_cert

      ca_info_type.getOcsp().add(ocsp_info_type)
    end

    return ca_info_type
  end

  # Generates new id for client.
  def generate_client_id
    generated_id = "id#@client_ids_generated"
    @client_ids_generated = @client_ids_generated + 1

    return generated_id
  end

  def get_client_identifier(client)
    return Java::ee.ria.xroad.common.identifier.ClientId.create(
        client.xroad_instance,
        client.member_class,
        client.member_code,
        client.subsystem_code)
  end

  def get_service_identifier(service)
    return nil if service == nil

    return Java::ee.ria.xroad.common.identifier.ServiceId.create(
      service.xroad_instance,
      service.member_class,
      service.member_code,
      service.subsystem_code,
      service.service_code,
      service.service_version)
  end
end
