package org.example.udphole;

import org.example.udphole.stun.EndPoint;
import org.example.udphole.stun.Stun;
import org.example.udphole.stun.StunException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class ClientPublisher {
    private ClientInfo thisClient = null;
    private String name;
    private SynchronizationTool syncTool;
    private JsonTool jsonTool;
    private HttpTool httpTool;
    private static final Logger logger
            = LoggerFactory.getLogger("ConnectionLib");

    public ClientPublisher(String name){
        this(name, new NodeTool());
    }
    public ClientPublisher(String name, SynchronizationTool syncTool) {
        this.syncTool = syncTool;
        BeansFactory factory = FactoryHolder.getFactory();
        this.jsonTool = factory.getJsonTool();
        this.httpTool = factory.getHttpTool();
        this.name = name;
    }

    public ClientInfo retrieveInfoAbout(String thatClientName) {
        return retrieveInfoAbout(thatClientName, 30);
    }

    public ClientInfo retrieveInfoAbout(String thatClientName, int oldnessSeconds) {
        try {
            return syncTool.getClientInfo(thatClientName, oldnessSeconds, jsonTool, httpTool);
        } catch (JsonConvertException | HttpException e) {
            e.printStackTrace();
        }
        return null;
    }


    public ClientInfo retrievePublicAddress(String... stunServers) throws StunException {
        /*** Setup the STUN servers: ***/

        EndPoint endPoint = Stun.createEndpointOrNull(Arrays.asList(stunServers));
        if (endPoint != null) {
            thisClient = new ClientInfo(this.name, endPoint.getLocalAddress(), endPoint.getLocalPort(),
                    endPoint.getPublicAddress(), endPoint.getOpenedSocket());
        }
        return thisClient;
    }

    public boolean sendInfo(ClientInfo clientInfo) throws JsonConvertException, HttpException {
        syncTool.sendInfo(clientInfo, jsonTool, httpTool);
        return true;
    }

    public void removeOldRecords() {
        try {
            syncTool.removeOldRecords(httpTool);
        } catch (HttpException ex) {

        }
    }
}
