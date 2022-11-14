package org.example.udphole.sync;

import org.example.CommonConfig;
import org.example.tools.*;

import java.net.*;
import java.util.HashMap;
import java.util.Map;

public class NodeTool implements SynchronizationTool {

    private Map<String, String> headers = new HashMap<>();
    private final JsonTool jsonTool;
    private final HttpTool httpTool;
    private URL baseURL = null;
    private URI clientInfoUrl;

    private final String CLIENT_INFO = "clientInfo";

    public NodeTool(JsonTool jsonTool, HttpTool httpTool) {
        this.jsonTool = jsonTool;
        this.httpTool = httpTool;
        try {
            headers.put("Content-Type", "application/json");
            baseURL = new URL(CommonConfig.nodeSyncServer);
            clientInfoUrl = new URL(baseURL, CLIENT_INFO).toURI();
        } catch (MalformedURLException | URISyntaxException e) {
            e.printStackTrace();
        }

    }

    @Override
    public ClientInfo getClientInfo(String clientName, int oldness)
            throws JsonConvertException, HttpException {
        String query = String.format("%s?clientName=%s&oldness=%s", CLIENT_INFO, clientName, oldness);

        URL url = null;
        try {
            url = new URL(baseURL, query);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        String body = httpTool.get(url.toString(), headers);
        if (body.length() > 0) {
            ClientInfo info = jsonTool.generateObject(body, ClientInfo.class);
            return info;
        }
        return null;
    }

    @Override
    public boolean sendInfo(ClientInfo clientInfo)
            throws JsonConvertException, HttpException {
        String json = jsonTool.generateString(clientInfo);
        httpTool.post(clientInfoUrl.toString(), headers, json);
        return false;
    }

}
