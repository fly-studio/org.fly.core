package org.fly.core.function;

@FunctionalInterface
public interface Function<T, R> {
    R apply(T var1);
}
