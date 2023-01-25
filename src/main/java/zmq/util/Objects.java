package zmq.util;

/**
 * @deprecated Uses {@link java.util.Objects}
 */
@Deprecated
public class Objects
{
    private Objects()
    {
        // no instantiation
    }

    public static <T> T requireNonNull(T object, String msg)
    {
        return java.util.Objects.requireNonNull(object, msg);
    }
}
