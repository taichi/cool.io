package io.cool;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.local.LocalEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

import org.jruby.Ruby;
import org.jruby.RubyModule;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.log.Logger;
import org.jruby.util.log.LoggerFactory;

/**
 * @author taichi
 */
public class Coolio {

	static final Logger LOG = LoggerFactory.getLogger(Coolio.class.getName());

	enum CacheKey {
		IO_LOOP, WORKER_LOOP, FILE_SENTINEL;
	}

	static final String CACHE_STORAGE = "io.cool.CacheStorage";

	public static void load(Ruby runtime) {
		RubyModule coolio = runtime.defineModule("Coolio");
		coolio.defineAnnotatedMethods(Coolio.class);

		coolio.setInternalVariable(CACHE_STORAGE, new ConcurrentHashMap<>());
		Utils.addFinalizer(runtime, coolio, (tc, self) -> {
			shutdown(runtime.getCurrentContext(), coolio);
			return tc.nil;
		});
	}

	static <T> T getCacheEntry(Ruby runtime, CacheKey key, Supplier<T> fn) {
		ConcurrentMap<CacheKey, T> storage = getStorage(runtime);
		return storage.computeIfAbsent(key, k -> fn.get());
	}

	@SuppressWarnings("unchecked")
	static <T> ConcurrentMap<CacheKey, T> getStorage(Ruby runtime) {
		RubyModule coolio = Utils.getModule(runtime);
		ConcurrentMap<CacheKey, T> storage = (ConcurrentMap<CacheKey, T>) coolio
				.getInternalVariable(CACHE_STORAGE);
		return storage;
	}

	static void shutdown(CacheKey k, Map<CacheKey, Object> storage) {
		EventLoopGroup g = (EventLoopGroup) storage.get(k);
		if (g != null) {
			LOG.info("shutdown BEGIN {} {}", k, g);
			g.shutdownGracefully().awaitUninterruptibly();
			LOG.info("shutdown END   {} {}", k, g);
		}
	}

	public static EventLoopGroup getIoLoop(Ruby runtime) {
		// TODO how many workers do we need?
		return getCacheEntry(runtime, CacheKey.IO_LOOP, NioEventLoopGroup::new);
	}

	public static EventLoopGroup getWorkerLoop(Ruby runtime) {
		// TODO how many workers do we need?
		return getCacheEntry(runtime, CacheKey.WORKER_LOOP,
				LocalEventLoopGroup::new);
	}

	public static FileSentinel getFileSentinel(Ruby runtime) {
		return getCacheEntry(runtime, CacheKey.FILE_SENTINEL,
				() -> new FileSentinel(getWorkerLoop(runtime)));
	}

	/**
	 * for testing purpose
	 * 
	 * @param runtime
	 */
	public static void shutdown(ThreadContext context, IRubyObject self) {
		Ruby runtime = context.getRuntime();
		Map<CacheKey, Object> storage = getStorage(runtime);
		if (storage.isEmpty() == false) {
			LOG.info("Finalize Cooolio BEGIN");
			shutdown(CacheKey.IO_LOOP, storage);
			FileSentinel fs = (FileSentinel) storage
					.get(CacheKey.FILE_SENTINEL);
			if (fs != null) {
				fs.stop();
			}
			shutdown(CacheKey.WORKER_LOOP, storage);
			LOG.info("Finalize Cooolio END");
			storage.clear();
		}
	}
}
