# YouTrack Sampler

An application to demonstrate inconsistencies when querying WorkItems from the YouTrack REST API

## Getting started
- Set connections parameters in `sampler.properties`
  - `youtrack.api.url` - the path to your YouTrack instance API Endpoint (no trailing slash). e.g. `https://youtrack.jetbrains.com/api`
  - `youtrack.permanent.token` - your personal, permanent token with YouTrack API permissions
- Set optional sampler properties
  - `sampler.target.dir` - Output directory of the samples an log file
  - `sampler.date.start` - Start date to query the WorkItems from (yyyy-MM-dd)
  - `sampler.date.end` - End date to query the WorkItems from (yyyy-MM-dd)
  - `sampler.sample.count` - How many times should the samples be downloaded. 300 seemed sufficient in my case to demonstrate the issue
  - `sampler.threads` - Number of parallel threads. To impose a bit of pressure on the API. Maybe a thread count of 1 might also suffice to trigger the issue. I only tested with 3  
- Run the Starter.kt file and wait for the samples to be downloaded
- Monitor the log output for the statistics at the end

## Displaying the statistics without downloading the samples again
- Set the `sampler.target.dir` property to point to your existing samples directory
- Run the SampleEvaluator.kt file