module Base
  module TransactionCallbacks

    module_function

    def after_commit(&block)
      @after_commit ||= []
      @after_commit << block
    end

    def after_rollback(&block)
      @after_rollback ||= []
      @after_rollback << block
    end

    def reset_transaction_callbacks
      @after_commit = []
      @after_rollback = []
    end

    def execute_after_commit_actions
      @after_rollback = []

      if @after_commit
        begin
          logger.debug("executing after_commit actions")
          @after_commit.each do |proc|
            proc.call
          end
        rescue
          @after_commit = []
          raise $!
        end

        @after_commit = []
      end
    end

    def execute_after_rollback_actions
      @after_commit = []

      if @after_rollback
        logger.debug("executing after_rollback actions")

        @after_rollback.each do |proc|
          begin
            proc.call($!)
          rescue
            logger.error("error executing after_rollback action: #{$!.message}")
          end
        end

        @after_rollback = []
      end
    end
  end
end
