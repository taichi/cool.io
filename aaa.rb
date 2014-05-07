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


s = Coolio::TCPServer.new("localhost", 8080, EchoServerConnection)
s.listen(10)
puts s


loop = Cool.io::Loop.new
loop.attach(s)
loop.run()
