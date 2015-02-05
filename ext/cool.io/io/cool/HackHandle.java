package io.cool;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.RecvByteBufAllocator;

/**
 * @author taichi
 */
public class HackHandle implements RecvByteBufAllocator.Handle {
	@Override
	public ByteBuf allocate(ByteBufAllocator alloc) {
		return new HackByteBuf(alloc);
	}

	@Override
	public int guess() {
		return 0;
	}

	@Override
	public void record(int actualReadBytes) {
	}
}
