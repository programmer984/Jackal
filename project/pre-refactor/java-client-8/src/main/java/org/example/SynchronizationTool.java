package org.example;



public interface SynchronizationTool {
    ClientInfo getClientInfo(String clientName, int oldnessSeconds, JsonTool jsonTool, HttpTool httpTool) throws JsonConvertException, HttpException;
    boolean sendInfo(ClientInfo clientInfo, JsonTool jsonTool, HttpTool httpTool) throws  JsonConvertException, HttpException;
    void removeOldRecords(HttpTool httpTool) throws  HttpException;
}
