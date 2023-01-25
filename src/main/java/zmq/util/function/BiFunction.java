package zmq.util.function;

import java.util.function.Function;

/**
 * Represents a function that accepts two arguments and produces a result.
 * This is the two-arity specialization of {@link Function}.
 *
 * <p>This is a functional interface
 * whose functional method is {@link #apply(Object, Object)}.
 *
 * @param <T> the type of the first argument to the function
 * @param <U> the type of the second argument to the function
 * @param <R> the type of the result of the function
 *
 * @see java.util.function.BiFunction
 * @deprecated Use {@link java.util.function.BiFunction} instead
 */
@Deprecated
public interface BiFunction<T, U, R> extends java.util.function.BiFunction<T, U, R>
{
    R apply(T t, U u);
}
