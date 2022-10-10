public interface DataPipe {
    void startConnectAsync();
    void stop();
    DataPipeStates getCurrentState();
    void sendData(byte[] data, int offset, int length);
}
