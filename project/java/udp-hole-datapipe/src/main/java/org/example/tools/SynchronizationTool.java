package org.example.tools;


import org.example.udphole.sync.ClientInfo;

public interface SynchronizationTool {
    ClientInfo getClientInfo(String clientName, int oldnessSeconds) throws JsonConvertException, HttpException;
    boolean sendInfo(ClientInfo clientInfo) throws  JsonConvertException, HttpException;
}
