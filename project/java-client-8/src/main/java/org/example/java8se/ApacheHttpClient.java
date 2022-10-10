package org.example.java8se;

import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.Map;

import org.example.*;

class ApacheHttpClient implements org.example.HttpTool {

    // 5 seconds timeout
    private RequestConfig requestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(5000)
            .build();
    private CloseableHttpClient httpClient = HttpClients.createDefault();

    public ApacheHttpClient() {

    }

    @Override
    public void post(String url, Map<String, String> headers, String content) throws HttpException {
        HttpPost request = new HttpPost(url);
        request.setConfig(requestConfig);
        for (Map.Entry<String, String> header : headers.entrySet()) {
            request.addHeader(header.getKey(), header.getValue());
        }

        if (content != null) {
            StringEntity stringEntity = new StringEntity(content,
                    ContentType.APPLICATION_JSON);
            request.setEntity(stringEntity);
        }

        try (CloseableHttpResponse response = httpClient.execute(request)) {
            if (response.getStatusLine().getStatusCode() >= 300) {
                throw new HttpException(response.getStatusLine().getReasonPhrase());
            }
        } catch (IOException e) {
            throw new HttpException(e);
        }
    }

    @Override
    public String get(String url, Map<String, String> headers) throws HttpException {
        HttpGet request = new HttpGet(url);
        request.setConfig(requestConfig);
        for (Map.Entry<String, String> header : headers.entrySet()) {
            request.addHeader(header.getKey(), header.getValue());
        }
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            if (response.getStatusLine().getStatusCode() < 300) {
                String result = EntityUtils.toString(response.getEntity());
                return result;
            }
            throw new HttpException("No data");
        } catch (IOException e) {
            throw new HttpException(e);
        }
    }
}
