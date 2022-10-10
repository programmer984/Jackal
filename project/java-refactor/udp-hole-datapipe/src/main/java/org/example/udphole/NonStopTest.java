package org.example.udphole;

import java.nio.ByteBuffer;

class NonStopTest {




    public NonStopTest() throws InterruptedException {
        stateListener c1 = new stateListener();
        stateListener c2 = new stateListener();
        ConnectionManager cm1 = new ConnectionManager("java3", "java4", c1, c1);
        ConnectionManager cm2 = new ConnectionManager("java4", "java3", c2, c2);
        c1.setConnectionManager(cm1);
        c2.setConnectionManager(cm2);
        cm1.startAndKeepAlive();
        cm2.startAndKeepAlive();
        cm1.join();
        cm2.join();

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
            return new PacketOut(byteBuffer.array());
        }

        @Override
        public boolean fastCheck(byte[] buf) {
            return true;
        }
    }
}
