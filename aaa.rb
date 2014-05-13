$LOAD_PATH.unshift File.dirname(__FILE__)
$LOAD_PATH.unshift File.expand_path('../lib', __FILE__)

require 'cool.io'

class EchoServerConnection < Cool.io::TCPSocket
  def on_connect
    puts "#{remote_addr}:#{remote_port} connected"
  end

  def on_close
    puts "#{remote_addr}:#{remote_port} disconnected"
  end

  def on_read(data)
    write data
  end
end

@data = ""
def on_message(data)
  @data = data
end


TIMEOUT = 5.110
HOST = "localhost"
PORT = 8080

def simple
  # s = Coolio::TCPServer.new("localhost", 8080)
  s = Cool.io::TCPServer.new(HOST, PORT, EchoServerConnection, method(:on_message))
  puts s
  loop = Coolio::Loop.new
  loop.attach(s)
end

#simple()

def send_data(data)
  io = TCPSocket.new('127.0.0.1', PORT)
  begin
    io.write data
    #io.read
    sleep 1
  ensure
    io.close
  end
end

class MyConnection < Coolio::Socket
  attr_accessor :data, :connected, :closed

  def initialize(io, on_message)
    super(io)
    @on_message = on_message
  end

  def on_connect
    @connected = true
  end

  def on_close
    @closed = true
  end

  def on_read(data)
    @on_message.call(data)
  end
end



def test_run(data = nil)
  reactor = Coolio::Loop.new
  server = Coolio::TCPServer.new(HOST, PORT, MyConnection, method(:on_message))
  reactor.attach(server)
  thread = Thread.new { reactor.run(1) }
  send_data(data) if data
  sleep TIMEOUT
  reactor.stop
  server.detach
#  send_data('') # to leave from blocking loop
  thread.join
  @data
ensure
  server.close
  Coolio.shutdown
end

#if test_run("hello") == "hello"
# puts "OK"
#end

#loop = Cool.io::Loop.new
#loop.attach(s)
#loop.run()

def server
  require "socket"
  server = Coolio::Server.new(TCPServer.new(HOST,PORT), MyConnection, method(:on_message))
end
server()
