namespace :jruby do
  VERSION = "1.7.16.1"
  ZIPPATH = ".jruby/#{VERSION}.zip"
  task :extract do
    require 'zip'
    
    Zip::File.open ZIPPATH do |zf|
      zf.each do |e|
        zf.extract e, ".jruby/#{e.to_s}"
      end
    end
  end

  task :fetch do
    require 'open-uri'
    
    URL = "https://s3.amazonaws.com/jruby.org/downloads/#{VERSION}/jruby-bin-#{VERSION}.zip"
    puts "download from #{URL}"
    open(URL, "rb") do |tmp|
      cp tmp, ZIPPATH
    end
  end
  
  desc "fetch and extract JRuby-#{VERSION} to local directory."
  task :install => [:fetch, :extract]
end
