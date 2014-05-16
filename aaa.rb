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
    puts "onRead"
    puts write data
  end
  
  def on_write_complete
    puts "write comp!!"
  end
end

@data = ""
def on_message(data)
  puts "onMessage"
  @data = data
end


TIMEOUT = 2.110
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
  sock = TCPSocket.new('127.0.0.1', PORT)
  begin
    sock.write data
    puts sock.read(5)
    sleep 1
    puts "REAAAAAAD"
  ensure
    sock.close
  end
end

class MyConnection < Coolio::Socket
  attr_accessor :data, :connected, :closed

  def initialize(io, on_message)
    super(io)
    @on_message = on_message
  end

  def on_connect
    puts "CONNECT !!!!"
    @connected = true
  end

  def on_close
    puts "CLOSED!!!!"
    @closed = true
  end

  def on_read(data)
    puts "ON_READ"
    @on_message.call(data)
  end
end


def test_run(data = nil)
  reactor = Coolio::Loop.new
  server = Coolio::TCPServer.new(HOST, PORT, EchoServerConnection, method(:on_message))
  reactor.attach(server)
  thread = Thread.new { reactor.run(1) }
  send_data(data) if data
  sleep TIMEOUT
  reactor.stop
  server.detach
#  send_data('') # to leave from blocking loop
  thread.join
  @data
rescue
  raise $!
ensure
  server.close unless server.nil?
  Coolio.shutdown
end

test_run("hello")
#send_data("hello")

#loop = Cool.io::Loop.new
#loop.attach(s)
#loop.run()

def server
  require "socket"
  server = Coolio::Server.new(TCPServer.new(HOST,PORT), MyConnection, method(:on_message)) do
    puts "hoge"
  end
  sock = TCPSocket.new('127.0.0.1', PORT)
  soc = MyConnection.new(sock, method(:on_message))
  soc.__send__(:on_connect)
end
#server()
