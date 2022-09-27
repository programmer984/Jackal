@FunctionalInterface
public interface PipeDataConsumer {
    void onDataReceived(byte[] data, int offset, int size);
}
