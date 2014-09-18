package com.shopwiki.xzcute;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @owner jdickinson
 */
public final class DaemonThreadFactory {

	private static final ThreadFactory INSTANCE = new ThreadFactory() {
		@Override
		public Thread newThread(Runnable r) {
			Thread thread = new Thread(r);
			thread.setDaemon(true);
			return thread;
		}
	};

	public static ThreadFactory getInstance() {
		return INSTANCE;
	}

	public static ThreadFactory getInstance(final String name, final boolean appendCount) {
		return new ThreadFactory() {
			private AtomicInteger counter = new AtomicInteger(1);

			@Override
			public Thread newThread(Runnable runnable) {
				Thread thread;
				if (appendCount) {
					thread = new Thread(runnable, name + "-" + counter.getAndIncrement());
				} else {
					thread = new Thread(runnable, name);
				}
				thread.setDaemon(true);
				return thread;
			}
		};
	}
}
