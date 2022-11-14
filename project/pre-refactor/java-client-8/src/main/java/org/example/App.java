package org.example;

import org.example.java8se.Java8SeBeansFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hello world!
 */
public class App {
    private static final Logger logger
            = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) throws Exception {
        logger.info("Hello, world! ");

        FactoryHolder.setFactory(new Java8SeBeansFactory());

        if (args.length == 0) {
            new org.example.NonStopTest();
        } else {
            new NonStopClient(args[0], args[1]);
        }
    }

    private void justConnect() throws Exception {

        ClientPublisher cp = new ClientPublisher("java");

        ClientInfo thisInfo = cp.retrievePublicAddress("jitsi.org", "numb.viagenie.ca", "stun.ekiga.net");
        ClientInfo otherInfo = null;

        if (cp.sendInfo(thisInfo)) {
            int counter = 0;
            while (true) {
                otherInfo = cp.retrieveInfoAbout("android", 10);
                if (otherInfo != null) {
                    logger.debug("retrived info about {}\n", otherInfo);
                    break;
                }
                logger.debug(".");
                Thread.sleep(100);
                if (++counter > 20) {
                    logger.debug("receive info from sync timeout\n");
                    return;
                }
            }
            logger.debug("starting connecting between {} \nand {}\n", thisInfo, otherInfo);
            UDPHolePuncher c = new UDPHolePuncher(thisInfo, otherInfo);
            if (c.connect(30)) {
                for (UDPEndPoint endPoint : c.getRemoteEndpoints()) {
                    logger.debug("connected to {}\n", endPoint);
                }
            } else {
                logger.debug("connection timeout");
            }
            if (thisInfo.getSocket().isConnected()) {
                thisInfo.getSocket().close();
            }
        }
    }


}
