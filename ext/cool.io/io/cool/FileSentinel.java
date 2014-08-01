package io.cool;

import io.netty.channel.local.LocalEventLoopGroup;
import io.netty.util.concurrent.Future;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import org.jruby.util.log.Logger;
import org.jruby.util.log.LoggerFactory;

/**
 * @author taichi
 */
class FileSentinel {

	private static final Logger LOG = LoggerFactory
			.getLogger(FileSentinel.class.getName());

	WatchService watchService = Utils.newWatchService();

	final LocalEventLoopGroup eventLoop;
	final ConcurrentMap<Path, WatchKey> keys = new ConcurrentHashMap<>();
	final CopyOnWriteArrayList<BiConsumer<Path, WatchEvent<?>>> listeners = new CopyOnWriteArrayList<BiConsumer<Path, WatchEvent<?>>>();

	final AtomicBoolean running = new AtomicBoolean(false);
	Future<?> future;

	public FileSentinel(LocalEventLoopGroup watcherLoop) {
		this.eventLoop = watcherLoop;
	}

	public void watch(Path path) {
		LOG.info("watch {}", path);
		WatchKey key = Utils.watch(this.watchService,
				Files.isDirectory(path) ? path : path.getParent());
		keys.putIfAbsent(path, key);
	}

	public void unwatch(Path path) {
		LOG.info("unwatch {}", path);
		WatchKey key = keys.remove(path);
		if (key != null) {
			key.cancel();
			LOG.info("WatchKey exists and cacelled. {}", path);
		}
	}

	public BiConsumer<Path, WatchEvent<?>> register(
			BiConsumer<Path, WatchEvent<?>> listener) {
		listeners.add(listener);
		return listener;
	}

	public BiConsumer<Path, WatchEvent<?>> unregister(
			BiConsumer<Path, WatchEvent<?>> listener) {
		LOG.info("unregister BEGIN {}", listeners.size());
		listeners.remove(listener);
		LOG.info("unregister END   {}", listeners.size());
		return listener;
	}

	public void start() {
		if (running.getAndSet(true) == false) {
			future = eventLoop.submit(this::publishEvents);
		}
	}

	void publishEvents() {
		try {
			while (Thread.interrupted() == false && running.get()) {
				WatchKey key = watchService.poll(200, TimeUnit.MILLISECONDS);
				if (key != null) {
					Path path = Path.class.cast(key.watchable());
					for (WatchEvent<?> event : key.pollEvents()) {
						dispatch(path, event);
					}
					key.reset();
				}
			}
		} catch (ClosedWatchServiceException | RejectedExecutionException e) {
			LOG.debug("any time no problem.", e);
		} catch (Exception e) {
			LOG.error(e);
		}
	}

	void dispatch(Path root, WatchEvent<?> event) throws IOException {
		LOG.info("dispatch {} {}", event.kind().name(), root);
		listeners.stream().forEach(a -> a.accept(root, event));
	}

	public void stop() {
		LOG.info("stop {}", running);
		if (running.getAndSet(false)) {
			keys.values().forEach(k -> k.cancel());
			keys.clear();

			if (future != null && future.isCancellable()
					&& future.isDone() == false) {
				future.cancel(true);
			}
			Utils.close(watchService);
		}
	}
}
