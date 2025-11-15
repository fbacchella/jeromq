package zmq.util;

public class ValueReference<V>
{
    private V value;

    public ValueReference(V value)
    {
        this.value = value;
    }

    public ValueReference()
    {
    }

    public V get()
    {
        return value;
    }

    public void set(V value)
    {
        this.value = value;
    }

    @Override
    public String toString()
    {
        return value == null ? "null" : value.toString();
    }
}
