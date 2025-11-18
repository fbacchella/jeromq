package zmq.util;

import java.util.Optional;

// Emulates the errno mechanism present in C++, in a per-thread basis.
public class Errno
{
    @FunctionalInterface
    private interface ErrorContext {
        int getErrno();
        default Optional<Throwable> getThrowable()
        {
            return Optional.empty();
        }
    }

    private static class WithException implements ErrorContext {
        private final int errno;
        private final Throwable exception;

        private WithException(int errno, Throwable exception) {
            this.errno = errno;
            this.exception = exception;
        }

        @Override
        public int getErrno() {
            return errno;
        }

        @Override
        public Optional<Throwable> getThrowable() {
            return Optional.of(exception);
        }
    }
    private static final ThreadLocal<ErrorContext> local = ThreadLocal.withInitial(() -> () -> 0);

    public int get()
    {
        return local.get().getErrno();
    }

    public Optional<Throwable> getException()
    {
        return local.get().getThrowable();
    }

    public void set(int errno, Throwable ex)
    {
        local.set(new WithException(errno, ex));
    }

    public void set(int errno)
    {
        local.set(() -> errno);
    }
    public boolean is(int err)
    {
        return get() == err;
    }

    @Override
    public String toString()
    {
        String exception = getException().map(t -> ", ex = " + t.getMessage()).orElse("");
        return "Errno[" + get() + exception + "]";
    }
}
