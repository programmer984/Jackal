package org.example.java8se;

import org.example.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import org.example.JsonConvertException;

class Jackson implements org.example.JsonTool{
    private SimpleFilterProvider serializationFilterProvider = new SimpleFilterProvider();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonMapper jsonMapper = new JsonMapper();
    public Jackson()
    {
        serializationFilterProvider.addFilter("IPAddress",
                SimpleBeanPropertyFilter.filterOutAllExcept("Address"));
        jsonMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
    @Override
    public String generateString(Object obj) throws JsonConvertException{
        String json = null;
        try {
            json = objectMapper.setFilterProvider(serializationFilterProvider)
                    .writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new JsonConvertException(e);
        }
        return json;
    }

    @Override
    public <T> T generateObject(String json, Class<T> klass) throws JsonConvertException {
        try {
            return jsonMapper.readValue(json, klass);
        } catch (JsonProcessingException e) {
            throw new JsonConvertException(e);
        }
    }
}
