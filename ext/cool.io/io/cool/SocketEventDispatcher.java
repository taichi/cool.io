package io.cool;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.log.Logger;

/**
 * @author taichi
 */
class SocketEventDispatcher extends ChannelInboundHandlerAdapter {

	static final Logger LOG = Utils.getLogger(SocketEventDispatcher.class);

	final IRubyObject socket;

	public SocketEventDispatcher(IRubyObject socket) {
		this.socket = socket;
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg)
			throws Exception {
		LOG.debug("{} {}", msg, msg.getClass());
		socket.callMethod(socket.getRuntime().getCurrentContext(), "on_read",
				Utils.to(socket.getRuntime(), (ByteBuf) msg));
	}
}
