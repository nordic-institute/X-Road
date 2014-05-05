# Checks status of global conf generation and reports result to the view
class GlobalConfGenStatusController < ApplicationController
  include BaseHelper

  def check_status
    generation_status = GlobalConfGenerationStatus.get()
    last_attempt_time = generation_status[:time]

    if generation_status[:no_status_file] == true
      render_json(get_response_json("NO_STATUS_FILE"))
    elsif conf_generated_more_than_minute_ago?(last_attempt_time)
      render_json(get_response_json("OUT_OF_DATE", last_attempt_time))
    elsif generation_status[:success] == true
      render_json(get_response_json("SUCCESS"))
    else
      render_json(get_response_json("FAILURE", last_attempt_time))
    end
  end
  
  private

  def get_response_json(status, last_attempt_time = nil)
    response_json = {:status => status}

    if(last_attempt_time != nil)
      response_json[:last_attempt_time] = format_time(last_attempt_time)
    end

    response_json
  end

  def conf_generated_more_than_minute_ago?(generation_time)
    generation_time < Time.now() - 60
  end
end
