package net.p3pp3rf1y.sophisticatedstorage.util;

import java.util.function.Supplier;

/**
 * Porting Lib 的 Lazy 在 26.3 不再可用，这里给一个等价的最小实现。
 */
public final class Lazy<T> implements Supplier<T> {
	private Supplier<T> supplier;
	private T value;

	private Lazy(Supplier<T> supplier) {
		this.supplier = supplier;
	}

	public static <T> Lazy<T> of(Supplier<T> supplier) {
		return new Lazy<>(supplier);
	}

	@Override
	public T get() {
		if (supplier != null) {
			value = supplier.get();
			supplier = null;
		}
		return value;
	}
}
