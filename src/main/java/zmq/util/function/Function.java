package zmq.util.function;

/**
 * Represents a function that accepts one argument and produces a result.
 *
 * <p>This is a functional interface
 * whose functional method is {@link #apply(Object)}.
 *
 * @param <T> the type of the input to the function
 * @param <R> the type of the result of the function
 *
 * @deprecated Use {@link java.util.function.Function} instead
 */
@Deprecated
public interface Function<T, R> extends java.util.function.Function<T, R>
{
}
