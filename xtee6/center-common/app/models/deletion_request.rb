class DeletionRequest < Request

  # Revokes respective registration request if it is present.
  def revoke_respective_reg_request
    reg_processing = get_respective_reg_processing()

    if reg_processing
      other_request = reg_processing.get_other_request(self)

      if origin.eql?(other_request.origin)
        logger.info("Revoking request for registering client '#{sec_serv_user}'"\
            "to security server '#{security_server}'")

        reg_processing.update_attributes!(:status => RequestProcessing::REVOKED)
      end
    end
  end

  def get_respective_reg_processing
    raise "This method must be overridden by subclass"
  end
end

