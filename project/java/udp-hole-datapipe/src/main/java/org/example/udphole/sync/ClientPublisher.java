package org.example.udphole.sync;

import org.example.tools.HttpException;
import org.example.tools.JsonConvertException;
import org.example.tools.RsaEncryptionTool;
import org.example.tools.SynchronizationTool;
import org.example.udphole.stun.EndPoint;
import org.example.udphole.stun.Stun;
import org.example.udphole.stun.StunException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ClientPublisher {
    private static final Logger logger
            = LoggerFactory.getLogger(ClientPublisher.class);
    private ClientInfo thisClient = null;
    private final String name;
    private final SynchronizationTool syncTool;
    private final RsaEncryptionTool rsaTool;


    public ClientPublisher(String name, SynchronizationTool syncTool, RsaEncryptionTool rsaTool) {
        this.syncTool = syncTool;
        this.name = name;
        this.rsaTool = rsaTool;
    }

    public ClientInfo retrieveInfoAbout(String thatClientName) {
        return retrieveInfoAbout(thatClientName, 30);
    }

    public ClientInfo retrieveInfoAbout(String thatClientName, int oldnessSeconds) {
        try {
            return syncTool.getClientInfo(thatClientName, oldnessSeconds);
        } catch (JsonConvertException | HttpException e) {
            e.printStackTrace();
        }
        return null;
    }


    public ClientInfo retrievePublicAddress() throws StunException {
        /*** Setup the STUN servers: ***/

        EndPoint endPoint = Stun.createEndpointOrNull();
        if (endPoint != null) {
            thisClient = new ClientInfo(this.name, endPoint.getLocalAddress(), endPoint.getLocalPort(),
                    endPoint.getPublicAddress(), endPoint.getOpenedSocket(), rsaTool);
        }
        return thisClient;
    }

    public boolean sendInfo(ClientInfo clientInfo) throws JsonConvertException, HttpException {
        syncTool.sendInfo(clientInfo);
        return true;
    }

}
