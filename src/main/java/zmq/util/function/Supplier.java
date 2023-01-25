package zmq.util.function;

/**
 * Represents a supplier of results.
 *
 * <p>There is no requirement that a new or distinct result be returned each
 * time the supplier is invoked.
 *
 * <p>This is a functional interface
 * whose functional method is {@link #get()}.
 *
 * @param <T> the type of results supplied by this supplier
 *
 * @deprecated  Use {@link java.util.function.Supplier} instead
 */
@Deprecated
public interface Supplier<T> extends java.util.function.Supplier<T>
{
}
