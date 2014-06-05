package io.cool;

import io.netty.channel.Channel;
import io.netty.channel.nio.AbstractNioChannel;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.channels.SelectableChannel;

import org.jruby.util.log.Logger;
import org.jruby.util.log.LoggerFactory;

/**
 * @author taichi
 */
class NettyHack {

	private static final Logger LOG = LoggerFactory.getLogger(NettyHack.class
			.getName());

	public static Method getMethod(Class<?> clazz, String name) {
		try {
			Method m = clazz.getDeclaredMethod(name);
			m.setAccessible(true);
			return m;
		} catch (NoSuchMethodException | SecurityException e) {
			LOG.error(e);
			throw new IllegalStateException(e);
		}
	}

	static final Method javaChannel = findJavaChannel();

	static Method findJavaChannel() {
		return getMethod(AbstractNioChannel.class, "javaChannel");
	}

	public static SelectableChannel runJavaChannel(Channel instance) {
		try {
			Object ch = javaChannel.invoke(instance);
			return (SelectableChannel) ch;
		} catch (IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			LOG.error(e);
			throw new IllegalStateException(e);
		}
	}

}
