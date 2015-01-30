require File.expand_path('../spec_helper', __FILE__)

describe Coolio::TCPSocket do
  let :loop do
    Coolio::Loop.new
  end

  before :each do
    @echo = TCPServer.new(0)
    @host = @echo.addr[3]
    @port = @echo.addr[1]
    @running = true
    @echo_thread = Thread.new do
      socks = [@echo]
      while @running
        selected = select(socks, [], [], 0.1)
        next if selected.nil?
        selected[0].each do |s|
          if s == @echo
            socks.push s.accept
            next
          end
          unless s.eof?
            puts "try to read on server"
            str = s.read 1
            puts "read #{str}"
            s.write(str)
          end
        end
      end
      socks.each do |s|
        s.close
      end
      Thread.pass
    end
  end
  
  after :each do
    @running = false
    @echo_thread.join
  end

  context "#close" do
    it "detaches all watchers on #close before loop#run" do
      client = Coolio::TCPSocket.connect(@host, @port)
      loop.attach client
      client.close
      expect(loop.watchers.size).to eq 0
    end
  end

  context "#on_connect" do
    class OnConnect < Cool.io::TCPSocket
      attr :connected
      def on_connect
        @connected = true
      end
    end
    
    it "connected client called on_connect" do
      c = OnConnect.connect(@host, @port)
      loop.attach c
      loop.run_once
      expect(c.connected).to eq true
      c.close
    end
  end
  
  context "#on_close" do
    class OnClose < Cool.io::TCPSocket
      attr :closed
      def on_close
        @closed = true
      end
    end
  
    it "disconnected client called on_close" do
      c = OnClose.connect(@host, @port)
      loop.attach c
      c.close
      expect(c.closed).to eq true
    end
  end
  
  context "#on_read" do
    class OnRead < Cool.io::TCPSocket
      attr :read_data, :times
      def on_read(data)
        puts "on_read ${data}"
        read_data += data
        @times += 1
        if @times < 5
          write "${@times}"
        end
      end
    end
    
    it "receive 5 times" do
      c = OnRead.connect(@host, @port)
      loop.attach c
      loop.run_once # on_connect
      c.write "0"
      loop.run_once # flush_buffer
      5.times do
        loop.run_once # on_read
      end
      expect(c.times).to eq 4
      expect(c.read_data).to eq "01234"
      c.close
    end
  end
end
