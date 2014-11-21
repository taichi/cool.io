#--
# Copyright (C)2011 Tony Arcieri
# You can redistribute this under the terms of the Ruby license
# See file LICENSE for details
#++

require "cool.io/version"
require "cool.io/detect"

unless jruby?
  require "cool.io/custom_require"
  cool_require "iobuffer_ext"
  cool_require "cool.io_ext"
end

require "cool.io/loop"
require "cool.io/meta"
require "cool.io/iowatcher"
require "cool.io/io"
require "cool.io/timer_watcher"
require "cool.io_jars" if jruby?
require "coolio_ext.jar" if jruby?
require "cool.io/listener" unless jruby?
require "cool.io/async_watcher"
require "cool.io/dns_resolver" unless jruby?
require "cool.io/socket" unless jruby?
require "cool.io/server"
require "cool.io/http_client" unless jruby?
require "cool.io/dsl"

module Coolio
  def self.inspect
    "Cool.io"
  end
end

module Cool
  # Allow Coolio module to be referenced as Cool.io
  def self.io
    Coolio
  end
end
