package org.example;

import java.nio.ByteBuffer;

class NonStopClient {

    public NonStopClient(String thisName, String thatName) throws InterruptedException {
        stateListener c1 = new stateListener();
        ConnectionManager cm1 = new ConnectionManager(thisName, thatName, c1, c1);
        c1.setConnectionManager(cm1);
        cm1.startAndKeepAlive();
        cm1.join();

    }



    private class stateListener implements ConnectionStateListener, PacketsProviderAndAcceptor {
        public void setConnectionManager(ConnectionManager connectionManager) {
            this.connectionManager = connectionManager;
        }

        ConnectionManager connectionManager;

        @Override
        public void onConnected() {

        }

        @Override
        public void onConnectionLost() {

        }

        @Override
        public void onIncomingPacket() {
            connectionManager.getInputPackets().poll();
        }

        @Override
        public PacketOut getKeepAlive() {
            ByteBuffer byteBuffer = ByteBuffer.allocate(9);
            byteBuffer.put((byte) 0x01);
            byteBuffer.putLong(1, Utils.nowMs());
            byteBuffer.put(1, (byte) 44);
            return new PacketOut(byteBuffer.array());
        }

        @Override
        public boolean fastCheck(byte[] buf) {

            return true;
        }
    }
}
