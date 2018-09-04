package com.fly.core.text.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fly.core.contract.AbstractJsonable;

import java.io.File;
import java.io.IOException;

public class Jacksonable implements AbstractJsonable {

    public static class Builder{
        public static ObjectMapper makeAdapter()
        {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.enable(JsonParser.Feature.IGNORE_UNDEFINED);
            objectMapper.enable(JsonParser.Feature.ALLOW_COMMENTS);
            objectMapper.enable(JsonParser.Feature.ALLOW_SINGLE_QUOTES);
            objectMapper.enable(JsonParser.Feature.ALLOW_TRAILING_COMMA);
            objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            return objectMapper;
        }
    }

    public static <T> T fromJson(final Class<T> clazz, String json) throws IOException
    {
        return Builder.makeAdapter().readValue(json, clazz);
    }

    public static <T> T fromJson(final Class<T> clazz, File file) throws IOException
    {
        return Builder.makeAdapter().readValue(file, clazz);
    }

    public String toJson()
    {
        try {
            return Builder.makeAdapter().writeValueAsString(this);
        } catch (JsonProcessingException e)
        {

        }
        return null;
    }

    public void toJson(File file) throws Exception
    {
        Builder.makeAdapter().writeValue(file, this);
    }
}
