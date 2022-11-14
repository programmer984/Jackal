package com.example.factory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.example.tools.*;

import java.io.IOException;

public class Jackson implements JsonTool {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Jackson(){
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public String generateString(Object obj) throws JsonConvertException{
        String json = null;
        try {
            json = objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new JsonConvertException(e);
        }
        return json;
    }

    @Override
    public <T> T generateObject(String json, Class<T> klass) throws JsonConvertException {
        try {
            return objectMapper.reader(klass).readValue(json);
        } catch (IOException e) {
            throw new JsonConvertException(e);
        }
    }
}
