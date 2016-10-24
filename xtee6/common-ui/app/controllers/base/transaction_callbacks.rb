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

      if @after_commit && !@after_commit.empty?
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

      if @after_rollback && !@after_rollback.empty?
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
