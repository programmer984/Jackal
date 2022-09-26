package org.example.udphole.stun;

import java.io.IOException;
import java.net.*;
import java.util.*;

public class Stun {
    public static final int STUN_PORT = 3478;
    public static final int AWAIT_TIME_MS = 2000;
    private static WeakHashMap<String, InetAddress> hostAddresses = new WeakHashMap<>();

    public static EndPoint createEndpointOrNull(Iterable<String> hostnames) throws StunException {
        EndPoint result = null;
        Iterator<String> iterator = hostnames.iterator();
        while (iterator.hasNext()) {
            String name = iterator.next();
            if (!hostAddresses.containsKey(name)) {
                try {
                    hostAddresses.put(name, InetAddress.getByName(name));
                } catch (Exception ignored) {
                }
            }
        }
        if (hostAddresses.size()==0){
            throw new StunException("thre are no stun servers");
        }
        DatagramSocket s = null;

        for (int i=0;i<10;i++){
            int skypePort = (int) (50000 + (Math.random()*10000));
            try {
                s = new DatagramSocket(skypePort);
                break;
            } catch (SocketException e) {
                e.printStackTrace();
            }
        }
        final DatagramSocket socket = s;

        try {
            if (hostAddresses.size() > 0) {

                List<InetSocketAddress> publicEndpoints = new ArrayList<>();

                Runnable runnable = () -> {
                    try {
                        byte[] inBufLocal = new byte[150];
                        DatagramPacket packet = new DatagramPacket(inBufLocal, inBufLocal.length);
                        socket.receive(packet);
                        InetSocketAddress publicEndpoint = fromBindingResponse(inBufLocal);
                        if (publicEndpoint != null) {
                            publicEndpoints.add(publicEndpoint);
                        }
                    } catch (Exception e) {
                        throw new StunException(e);
                    }
                };

                //start listen reply
                Thread replyListener = new Thread(runnable, "stun reply listener");
                replyListener.setDaemon(true);
                replyListener.start();

                for (int i = 0; i < 10; i++) {
                    //start send request
                    hostAddresses.values().parallelStream()
                            .forEach(address -> {
                                try {
                                    byte[] body = new StunMessage().generate();
                                    socket.send(new DatagramPacket(body, body.length, new InetSocketAddress(address, STUN_PORT)));
                                } catch (IOException e) {
                                    throw new StunException(e);
                                }
                            });


                    Thread.sleep(100);
                    if (publicEndpoints.size() > 0) {
                        break;
                    }
                }

                if (publicEndpoints.size() > 0) {
                    List<InetAddress> localAddresses = new ArrayList<>();
                    Enumeration<NetworkInterface> enumeration = NetworkInterface.getNetworkInterfaces();
                    while (enumeration.hasMoreElements()) {
                        NetworkInterface networkInterface = enumeration.nextElement();
                        for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                            InetAddress address = interfaceAddress.getAddress();
                            if (address.isLoopbackAddress()) continue;
                            if (address.isMulticastAddress()) continue;
                            if (address instanceof Inet6Address) continue;
                            localAddresses.add(address);
                        }
                    }

                    InetSocketAddress publicAddress = publicEndpoints.get(0);
                    result = new EndPoint(localAddresses, socket.getLocalPort(), publicAddress, socket);
                }

                replyListener.interrupt();
                replyListener.join(AWAIT_TIME_MS);
            }
        } catch (SocketException | InterruptedException e) {
            throw new StunException(e);
        }
        return result;
    }

    private static InetSocketAddress fromBindingResponse(byte[] buf) throws UnknownHostException {
        //response binding and IPv4
        if (buf[0] == 1 && buf[1] == 1 && buf[0x19] == 1) {
            int port = buf[0x1A] << 8;
            port |= buf[0x1B];
            port &= 0x0000FFFF;
            InetAddress address = InetAddress.getByAddress(new byte[]{buf[0x1C], buf[0x1D], buf[0x1E], buf[0x1F]});
            return new InetSocketAddress(address, port);
        }
        return null;
    }

}
