package io;

import io.cool.Utils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.atomic.AtomicInteger;

import org.jruby.Ruby;
import org.jruby.RubyBoolean;
import org.jruby.RubyClass;
import org.jruby.RubyFixnum;
import org.jruby.RubyIO;
import org.jruby.RubyModule;
import org.jruby.RubyNumeric;
import org.jruby.RubyObject;
import org.jruby.RubyString;
import org.jruby.anno.JRubyMethod;
import org.jruby.ext.socket.RubyUDPSocket;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.ByteList;
import org.jruby.util.io.BadDescriptorException;
import org.jruby.util.io.Stream;

/**
 * ::IO::Buffer on JRuby
 * 
 * @author taichi
 */
public class Buffer extends RubyObject {

	private static final long serialVersionUID = 6341438349389552651L;

	public static void load(Ruby r) {
		RubyModule IO = r.getModule("IO");
		Class<?> cls = Buffer.class;
		RubyClass rc = IO.defineClassUnder(cls.getSimpleName(), r.getObject(),
				Buffer::new);

		rc.defineAnnotatedMethods(cls);
		rc.defineConstant("MAX_SIZE", RubyFixnum.int2fix(r, 1024 * 1024 * 1024));
	}

	static AtomicInteger default_node_size = new AtomicInteger(1024 * 16);

	static final String DEFAULT_NODE_SIZE = "default_node_size";

	@JRubyMethod(name = DEFAULT_NODE_SIZE, meta = true)
	public static IRubyObject getDefaultNodeSize(IRubyObject self) {
		return RubyFixnum.int2fix(self.getRuntime(), default_node_size.get());
	}

	@JRubyMethod(name = DEFAULT_NODE_SIZE + "=", meta = true, argTypes = { RubyFixnum.class })
	public static IRubyObject setDefaultNodeSize(IRubyObject self,
			IRubyObject size) {
		if (size.isNil() == false) {
			int i = RubyFixnum.fix2int(size);
			if (0 < i) {
				default_node_size.set(i);
			}
		}
		return getDefaultNodeSize(self);
	}

	int nodeSize;
	ByteBuf internalBuffer;

	public Buffer(Ruby runtime, RubyClass metaClass) {
		super(runtime, metaClass);
	}

	@JRubyMethod
	public IRubyObject initialize() {
		return initialize(getDefaultNodeSize(getMetaClass()));
	}

	@JRubyMethod
	public IRubyObject initialize(IRubyObject size) {
		if (size.isNil()) {
			this.nodeSize = default_node_size.get();
		} else {
			int j = RubyFixnum.fix2int(size);
			if (0 < j) {
				this.nodeSize = j;
			} else {
				this.nodeSize = default_node_size.get();
			}
		}
		this.internalBuffer = PooledByteBufAllocator.DEFAULT
				.buffer(this.nodeSize);
		Utils.addFinalizer(getRuntime(), this, (ctx, ro) -> {
			this.internalBuffer.release();
			return getRuntime().getNil();
		});
		return this;
	}

	@JRubyMethod
	public void clear() {
		this.internalBuffer.clear();
	}

	@JRubyMethod
	public IRubyObject size() {
		return RubyFixnum.int2fix(getRuntime(),
				this.internalBuffer.readableBytes());
	}

	@JRubyMethod(name = "empty?")
	public RubyBoolean isEmpty() {
		return this.internalBuffer.readableBytes() < 1 ? getRuntime().getTrue()
				: getRuntime().getFalse();
	}

	@JRubyMethod(name = { "<<", "append", "write" })
	public IRubyObject append(IRubyObject data) {
		ByteList buf = data.asString().getByteList();
		this.internalBuffer.writeBytes(buf.getUnsafeBytes(), 0, buf.length());
		return data;
	}

	@JRubyMethod
	public IRubyObject prepend(IRubyObject data) {
		ByteList buf = data.asString().getByteList();
		ByteBuf newBuffer = PooledByteBufAllocator.DEFAULT
				.buffer(this.internalBuffer.readableBytes() + this.nodeSize);
		newBuffer.writeBytes(buf.getUnsafeBytes(), 0, buf.length());
		newBuffer.writeBytes(this.internalBuffer);
		this.internalBuffer.release();
		this.internalBuffer = newBuffer;
		return data;
	}

	@JRubyMethod(argTypes = { RubyNumeric.class })
	public IRubyObject read(IRubyObject length) {
		int l = RubyNumeric.fix2int(length);
		if (l < 1) {
			throw getRuntime().newArgumentError(
					"length must be greater than zero");
		}
		int rb = this.internalBuffer.readableBytes();
		byte[] bytes = new byte[rb < l ? rb : l];
		this.internalBuffer.readBytes(bytes);
		return RubyString.newStringNoCopy(getRuntime(), bytes);
	}

	@JRubyMethod(argTypes = { IRubyObject.class, RubyFixnum.class }, required = 2)
	public IRubyObject read_frame(IRubyObject data, IRubyObject mark) {
		int m = RubyNumeric.fix2int(mark);
		if (m < 1) {
			throw getRuntime().newArgumentError(
					"mark must be greater than zero");
		}
		if (255 < m) {
			throw getRuntime().newArgumentError("mark must be less than 256");
		}

		int index = this.internalBuffer.bytesBefore((byte) m);
		if (index < 0) {
			byte[] bytes = new byte[this.internalBuffer.readableBytes()];
			transfer(data, bytes);
			return getRuntime().getFalse();
		}

		byte[] bytes = new byte[index + 1];
		transfer(data, bytes);
		return getRuntime().getTrue();
	}

	void transfer(IRubyObject data, byte[] bytes) {
		this.internalBuffer.readBytes(bytes);
		if (data instanceof RubyString) {
			RubyString str = (RubyString) data;
			str.cat(bytes);
		}
		if (data instanceof RubyIO) {
			RubyIO io = (RubyIO) data;
			io.write(getRuntime().getCurrentContext(),
					RubyString.newStringNoCopy(getRuntime(), bytes));
		}
	}

	@JRubyMethod
	public IRubyObject to_str() {
		byte[] bytes = new byte[this.internalBuffer.readableBytes()];
		this.internalBuffer.getBytes(this.internalBuffer.readerIndex(), bytes);
		return RubyString.newStringNoCopy(getRuntime(), bytes);
	}

	@JRubyMethod(argTypes = { RubyIO.class }, required = 1)
	public IRubyObject read_from(IRubyObject io) throws IOException,
			BadDescriptorException {
		RubyIO IO = (RubyIO) io;
		if (io instanceof RubyUDPSocket) {
			return processUDP(IO);
		}

		Stream stream = IO.getOpenFile().getMainStream();
		stream.setBlocking(false);
		ByteList bytes = stream.readall();
		this.internalBuffer.writeBytes(bytes.getUnsafeBytes(), 0,
				bytes.length());
		return RubyFixnum.int2fix(getRuntime(), bytes.length());
	}

	IRubyObject processUDP(RubyIO IO) throws IOException {
		DatagramChannel dc = (DatagramChannel) IO.getChannel();
		synchronized (dc.blockingLock()) {
			boolean old = dc.isBlocking();
			try {
				dc.configureBlocking(false);
				ByteBuffer dst = ByteBuffer.allocate(this.nodeSize);
				// call connect before for read.
				// but receiver socket cannot connect.
				dc.receive(dst);
				dst.flip();
				this.internalBuffer.writeBytes(dst);
				return RubyFixnum.int2fix(getRuntime(), dst.position());
			} finally {
				dc.configureBlocking(old);
			}
		}
	}

	@JRubyMethod(argTypes = { RubyIO.class }, required = 1)
	public IRubyObject write_to(IRubyObject io) {
		RubyIO IO = (RubyIO) io;
		byte[] bytes = new byte[this.internalBuffer.readableBytes()];
		this.internalBuffer.readBytes(bytes);
		RubyString str = RubyString.newStringNoCopy(getRuntime(), bytes);
		return IO.write(getRuntime().getCurrentContext(), str);
	}

	public ByteBuf internalBuffer() {
		return this.internalBuffer;
	}
}
