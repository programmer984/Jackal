package org.example.udphole;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.net.*;
import java.util.HashMap;
import java.util.Map;

class NodeTool implements SynchronizationTool {

    private Map<String, String> headers=new HashMap<>();

    private URL baseURL = null;
    private URI clientInfoUrl;

    private final String CLIENT_INFO = "clientInfo";

    public NodeTool() {
        try {
            headers.put("Content-Type", "application/json");

            baseURL = new URL("http://my-node-server.ru");
            clientInfoUrl = new URL(baseURL, CLIENT_INFO).toURI();
        } catch (MalformedURLException | URISyntaxException e) {
            e.printStackTrace();
        }

    }

    @Override
    public ClientInfo getClientInfo(String clientName, int oldness, JsonTool jsonTool, HttpTool httpTool)
            throws JsonConvertException, HttpException {
        long date = ClientInfo.calculateTimeStamp() - oldness;
        String query = String.format("%s?clientName=%s&minAge=%s" ,CLIENT_INFO, clientName, date);

        URL url = null;
        try {
            url = new URL(baseURL, query);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        String body = httpTool.get(url.toString(), headers);
        if (body.length()>0) {
            ClientInfo info = jsonTool.generateObject(body, ClientInfo.class);
            return info;
        }
        return null;
    }

    @Override
    public boolean sendInfo(ClientInfo clientInfo, JsonTool jsonTool, HttpTool httpTool)
            throws JsonConvertException, HttpException {
        String json = jsonTool.generateString(clientInfo);
        httpTool.post(clientInfoUrl.toString(), headers, json);
        return false;
    }

    @Override
    public void removeOldRecords(HttpTool httpTool) throws HttpException {
    }


}
