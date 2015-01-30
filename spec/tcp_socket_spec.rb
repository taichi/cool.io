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
          if s.eof?
            s.close
            socks.delete s
          else
            s.write s.gets
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
    xit "detaches all watchers on #close before loop#run" do
      client = Coolio::TCPSocket.connect(@host, @port)
      loop.attach client
      client.close
      expect(loop.watchers.size).to eq 0
    end
  end

  context "#on_connect" do
    class CC < Cool.io::TCPSocket
      attr_accessor :connected
      def on_connect
        @connected = true
      end
    end
    
    it "connected client called on_connect" do
      c = CC.connect(@host, @port)
      loop.attach c
      loop.run_once
      sleep 1
      expect(c.connected).to eq true
      c.close
    end
  end
  
  context "#on_close" do
    class CC < Cool.io::TCPSocket
      attr_accessor :closed
      def on_close
        @closed = true
      end
    end
  
    xit "disconnected client called on_close" do
      c = CC.connect(@host, @port)
      loop.attach c
      c.close
      expect(c.closed).to eq true
    end
  end
  
  context "#on_read" do
    class CC < Cool.io::TCPSocket
      attr_accessor :read_data, :times
      def on_connect
        write "0\0"
      end
      def on_read(data)
        puts "on_read ${data}"
        read_data += data
        @times += 1
        if @times < 5
          write "${@times}"
          write ""
        end
      end
    end
    
    xit "receive 5 times" do
      c = CC.connect(@host, @port)
      loop.attach c
      10.times do
        loop.run_once
      end
      expect(c.times).to eq 4
      expect(c.read_data).to eq "01234"
      c.close
    end
  end
end
