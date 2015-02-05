package io.cool;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jruby.Ruby;
import org.jruby.RubyModule;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.log.Logger;

/**
 * @author taichi
 */
public class Coolio {

	static final Logger LOG = Utils.getLogger(Coolio.class);

	enum CacheKey {
		IO_LOOP, WORKER_POOL, FILE_SENTINEL;
	}

	public static void load(Ruby runtime) {
		RubyModule coolio = runtime.defineModule("Coolio");
		coolio.defineAnnotatedMethods(Coolio.class);

		newStorage(coolio, CacheKey.IO_LOOP);
		newStorage(coolio, CacheKey.WORKER_POOL);
		newStorage(coolio, CacheKey.FILE_SENTINEL);

		Utils.addFinalizer(runtime, coolio, Coolio::shutdown);
	}

	static <T> void newStorage(RubyModule coolio, CacheKey key) {
		coolio.setInternalVariable(key.name(),
				new AtomicReference<Optional<T>>(Optional.empty()));
	}

	static <T> T computeIfAbsent(Ruby runtime, CacheKey key, Supplier<T> fn) {
		AtomicReference<Optional<T>> ref = getStorage(runtime, key);
		return ref.updateAndGet(
				opt -> opt.isPresent() ? opt : Optional.of(fn.get())).get();
	}

	@SuppressWarnings("unchecked")
	static <T> AtomicReference<Optional<T>> getStorage(Ruby runtime,
			CacheKey key) {
		RubyModule coolio = Utils.getModule(runtime);
		AtomicReference<Optional<T>> storage = (AtomicReference<Optional<T>>) coolio
				.getInternalVariable(key.name());
		return storage;
	}

	static void shutdown(Ruby runtime, CacheKey key) {
		shutdown(runtime, key, (EventLoopGroup g) -> {
			g.shutdownGracefully().awaitUninterruptibly();
		});
	}

	static <T> void shutdown(Ruby runtime, CacheKey key, Consumer<T> fn) {
		AtomicReference<Optional<T>> ref = getStorage(runtime, key);
		ref.get().ifPresent(v -> {
			LOG.debug("shutdown BEGIN {} {}", key, v);
			fn.accept(v);
			LOG.debug("shutdown END   {} {}", key, v);
		});
	}

	public static NioEventLoopGroup getIoLoop(Ruby runtime) {
		// TODO how many workers do we need?
		return computeIfAbsent(runtime, CacheKey.IO_LOOP,
				NioEventLoopGroup::new);
	}

	public static EventLoopGroup getWorkerPool(Ruby runtime) {
		// TODO how many workers do we need?
		return computeIfAbsent(runtime, CacheKey.WORKER_POOL,
				LocalEventLoopGroup::new);
	}

	public static FileSentinel getFileSentinel(Ruby runtime) {
		return computeIfAbsent(runtime, CacheKey.FILE_SENTINEL,
				() -> new FileSentinel(getWorkerPool(runtime)));
	}

	public static IRubyObject shutdown(ThreadContext context, IRubyObject self) {
		Ruby runtime = context.getRuntime();
		LOG.debug("Finalize Cooolio BEGIN");
		shutdown(runtime, CacheKey.IO_LOOP);
		shutdown(runtime, CacheKey.FILE_SENTINEL,
				(FileSentinel fs) -> fs.stop());
		shutdown(runtime, CacheKey.WORKER_POOL);

		LOG.debug("Finalize Cooolio END");
		return context.nil;
	}
}
