package com.example.androidcontrol.factory;

import org.example.HttpException;
import org.example.HttpTool;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

public class HttpClientTool implements HttpTool {


    @Override
    public void post(String url, Map<String, String> headers, String content) throws HttpException {
        HttpURLConnection urlConnection = null;
        try {
            URL url2 = new URL(url);
            urlConnection = (HttpURLConnection) url2.openConnection();
            for(Map.Entry<String, String> header : headers.entrySet()){
                urlConnection.setRequestProperty(header.getKey(), header.getValue());
            }
            urlConnection.setDoOutput(true);
            urlConnection.setChunkedStreamingMode(0);
            OutputStream out = new BufferedOutputStream(urlConnection.getOutputStream());
            byte[] data = content.getBytes(StandardCharsets.UTF_8);
            out.write(data);
            out.flush();

            int code = urlConnection.getResponseCode();
            if (code == 200) {
                return;
            }
            throw new HttpException(urlConnection.getResponseMessage());
        } catch (IOException e) {
            throw new HttpException(e);
        } finally {
            urlConnection.disconnect();
        }
    }

    @Override
    public String get(String url, Map<String, String> headers) throws HttpException {

        HttpURLConnection urlConnection = null;
        try {
            URL url2 = new URL(url);
            urlConnection = (HttpURLConnection) url2.openConnection();
            for(Map.Entry<String, String> header : headers.entrySet()){
                urlConnection.setRequestProperty(header.getKey(), header.getValue());
            }

            InputStream in = new BufferedInputStream(urlConnection.getInputStream());
            int code = urlConnection.getResponseCode();
            int length = urlConnection.getContentLength();
            StringBuilder sb = new StringBuilder();
            if (code == 200) {
                if (in != null) {
                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(in));
                    String line;
                    while ((line = bufferedReader.readLine()) != null) {
                        sb.append(line);
                    }
                }
                return sb.toString();
            }
            throw new HttpException(urlConnection.getResponseMessage());
        } catch (IOException e) {
            throw new HttpException(e);
        } finally {
            urlConnection.disconnect();
        }
    }
}
