package com.example.factory;

import org.example.tools.*;

import java.io.IOException;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OkHttp implements HttpTool {

    public static final MediaType JSON
            = MediaType.get("application/json; charset=utf-8");
    OkHttpClient client = new OkHttpClient();

    @Override
    public void post(String url, Map<String, String> headers, String content) throws HttpException {
        RequestBody body = RequestBody.create(JSON, content);
        Request.Builder builder = new Request.Builder()
                .url(url)
                .post(body);

        for (Map.Entry<String, String> header : headers.entrySet()) {
            builder.addHeader(header.getKey(), header.getValue());
        }
        Request request = builder.build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new HttpException(response.message());
            }
        } catch (IOException e) {
            throw new HttpException(e);
        }
    }

    @Override
    public String get(String url, Map<String, String> headers) throws HttpException {
        Request.Builder builder = new Request.Builder()
                .url(url);
        for (Map.Entry<String, String> header : headers.entrySet()) {
            builder.addHeader(header.getKey(), header.getValue());
        }

        Request request = builder.build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return response.body().string();
            }
            throw new HttpException(response.message());
        } catch (IOException e) {
            throw new HttpException(e);
        }
    }
}
