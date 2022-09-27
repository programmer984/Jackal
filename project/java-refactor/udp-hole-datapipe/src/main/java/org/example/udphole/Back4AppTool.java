package org.example.udphole;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.*;
import java.util.HashMap;
import java.util.Map;

class Back4AppTool implements SynchronizationTool {

    private final static String headerAppIdName = "X-Parse-Application-Id";
    private final static String headerAppIdValue = "XHURKxm4OvyNjvQrjoPQzAe47v4wYKaV5hBUHvjY";
    private final static String headerApiKeyName = "X-Parse-REST-API-Key";
    private final static String headerApiKeyValue = "uF0GDcxhVhIPmHVFkXsErhH63sQfoTMeMELgcQy0";
    private final static String headerMasterKeyName = "X-Parse-Master-Key";
    private final static String headerMasterKeyValue = "NeiFt8IbxKxCKCAvvfqucHWcxTJ9GV1ZguhRRPh0";
    private Map<String, String> headers=new HashMap<>();
    private Map<String, String> headersWithMaster=new HashMap<>();

    private URL baseURL = null;
    private URI clientInfoUrl;
    private URI removeOldRecordsURI;

    private final String CLIENT_INFO = "ClientInfo";

    public Back4AppTool() {
        try {
            headers.put("Content-Type", "application/json");
            headers.put(headerAppIdName, headerAppIdValue);
            headers.put(headerApiKeyName, headerApiKeyValue);
            headersWithMaster.putAll(headers);
            headersWithMaster.put(headerMasterKeyName, headerMasterKeyValue);

            baseURL = new URL("https://parseapi.back4app.com/classes/");
            clientInfoUrl = new URL(baseURL, CLIENT_INFO).toURI();

            removeOldRecordsURI = new URL("https://parseapi.back4app.com/functions/removeClientInfo").toURI();
        } catch (MalformedURLException | URISyntaxException e) {
            e.printStackTrace();
        }

    }

    @Override
    public ClientInfo getClientInfo(String clientName, int oldness, JsonTool jsonTool, HttpTool httpTool)
            throws JsonConvertException, HttpException {
        long date = ClientInfo.calculateTimeStamp() - oldness;
        String query = CLIENT_INFO + "?order=-CreationTimestamp&limit=1&where=" + URLEncoder.encode(
                String.format("{\"Name\":{\"$regex\":\"^%s$\"}, \"CreationTimestamp\":{\"$gte\":%d}}",
                        clientName, date));


        URL url = null;
        try {
            url = new URL(baseURL, query);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        String result = httpTool.get(url.toString(), headers);
        responseBody responseBody = jsonTool.generateObject(result, Back4AppTool.responseBody.class);
        if (responseBody != null && responseBody.results != null && responseBody.results.length > 0) {
            return responseBody.results[0];
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
        httpTool.post(removeOldRecordsURI.toString(), headersWithMaster, null);
    }

    private static class responseBody {
        @JsonProperty("results")
        public ClientInfo[] results;
    }
}
