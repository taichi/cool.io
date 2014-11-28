package io.cool;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import org.jruby.RubyString;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.log.Logger;
import org.jruby.util.log.LoggerFactory;

/**
 * @author taichi
 */
class SocketEventDispatcher extends ChannelInboundHandlerAdapter {

	static final Logger LOG = LoggerFactory
			.getLogger(SocketEventDispatcher.class.getName());

	final IRubyObject socket;

	public SocketEventDispatcher(IRubyObject socket) {
		this.socket = socket;
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg)
			throws Exception {
		LOG.info("{} {}", msg, msg.getClass());
		ByteBuf buf = (ByteBuf) msg;
		byte[] bytes = new byte[buf.readableBytes()];
		buf.readBytes(bytes);
		IRubyObject data = RubyString.newStringNoCopy(socket.getRuntime(),
				bytes);
		socket.callMethod(socket.getRuntime().getCurrentContext(), "on_read",
				data);
		// ctx.write(msg);
	}
}
