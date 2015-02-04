# -*- encoding: utf-8 -*-
$:.push File.expand_path("../lib", __FILE__)
require "cool.io/version"
require "cool.io/detect"

module Cool
  # Allow Coolio module to be referenced as Cool.io
  def self.io; Coolio; end
end

Gem::Specification.new do |s|
  s.name        = "cool.io"
  s.version     = Coolio::VERSION
  s.authors     = ["Tony Arcieri", "Masahiro Nakagawa"]
  s.email       = ["tony.arcieri@gmail.com", "repeatedly@gmail.com"]
  s.homepage    = "http://coolio.github.com"
  s.summary     = "A cool framework for doing high performance I/O in Ruby"
  s.description = "Cool.io provides a high performance event framework for Ruby which uses the libev C library"
  s.extensions = ["ext/cool.io/extconf.rb", "ext/iobuffer/extconf.rb"] unless jruby?
  
  if jruby?
    s.platform = "java"
    s.add_runtime_dependency 'jar-dependencies', '~>0.1.7'
    s.requirements << "jar io.netty:netty-transport, 4.0.25.Final"
    ext_jar = 'lib/coolio_ext.jar'
    s.files << ext_jar if File.exist?(ext_jar)
    s.add_development_dependency "builder", "~> 3.2.2"
  end

  s.files         = `git ls-files`.split("\n")
  s.test_files    = `git ls-files -- {test,spec,features}/*`.split("\n")
  s.executables   = `git ls-files -- bin/*`.split("\n").map{ |f| File.basename(f) }
  s.require_paths = ["lib"]
  
  s.add_development_dependency "rake-compiler", "~> 0.8.3"
  s.add_development_dependency "rspec", ">= 2.13.0"
  s.add_development_dependency "rdoc", ">= 3.6.0"
  s.add_development_dependency "rubyzip", ">= 1.0.0"
end
