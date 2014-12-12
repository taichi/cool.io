namespace :jruby do
  VERSION = "1.7.17"
  ZIPPATH = ".jruby/#{VERSION}.zip"
  task :extract do
    require 'zip'
    
    Zip::File.open ZIPPATH do |zf|
      zf.each do |e|
        zf.extract e, ".jruby/#{e.to_s}"
      end
    end
  end
  
  def download (type, dest)
    require 'open-uri'
    require 'net/https'

    target = "https://s3.amazonaws.com/jruby.org/downloads/#{VERSION}/jruby-#{type}-#{VERSION}.zip"
    puts "download from #{target}"
    open(target, "rb", :ssl_verify_mode => OpenSSL::SSL::VERIFY_NONE) do |tmp|
      cp tmp, dest
    end
  end

  task :fetch do
    download 'bin', ZIPPATH
    download 'src', ".jruby/#{VERSION}-src.zip"
  end
  
  desc "fetch and extract JRuby-#{VERSION} to local directory."
  task :install => [:fetch, :extract]
end
