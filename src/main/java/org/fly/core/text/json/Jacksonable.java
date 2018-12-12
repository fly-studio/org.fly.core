package org.fly.core.text.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.apache.commons.codec.binary.StringUtils;
import org.fly.core.contract.AbstractJsonable;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

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

        public static <T> Map<String, T> jsonToMap(String json)
        {
            if (json != null && !json.isEmpty()) {
                ObjectMapper objectMapper = Jacksonable.Builder.makeAdapter();

                try {
                    return objectMapper.readValue(json, new TypeReference<Map<String, T>>(){});
                } catch (IOException e)
                {
                    e.printStackTrace();
                    return null;
                }
            }

            return null;
        }

        public static <T> List<Map<String, T>> jsonToRecords(String json)
        {
            if (json != null && !json.isEmpty()) {
                ObjectMapper objectMapper = Jacksonable.Builder.makeAdapter();

                try {
                    return objectMapper.readValue(json, new TypeReference<List<Map<String, T>>>(){});
                } catch (IOException e)
                {
                    e.printStackTrace();
                    return null;
                }
            }

            return null;
        }

        public static <T> List<T> jsonToList(String json)
        {
            if (json != null && !json.isEmpty()) {
                ObjectMapper objectMapper = Jacksonable.Builder.makeAdapter();

                try {
                    return objectMapper.readValue(json, new TypeReference<List<T>>(){});
                } catch (IOException e)
                {
                    e.printStackTrace();
                    return null;
                }
            }

            return null;
        }


    }

    public static <T> T fromJson(ObjectMapper objectMapper, final Class<T> clazz, String json) throws IOException
    {
        return objectMapper.readValue(json, clazz);
    }

    public static <T> T fromJson(final Class<T> clazz, String json) throws IOException
    {
        return fromJson(Builder.makeAdapter(), clazz, json);
    }

    public static <T> T fromJson(ObjectMapper objectMapper, final Class<T> clazz, byte[] json) throws IOException
    {
        return fromJson(objectMapper, clazz, StringUtils.newStringUtf8(json));
    }

    public static <T> T fromJson(final Class<T> clazz, byte[] json) throws IOException
    {
        return fromJson(clazz, StringUtils.newStringUtf8(json));
    }

    public static <T> T fromJson(ObjectMapper objectMapper, final Class<T> clazz, File file) throws IOException
    {
        return objectMapper.readValue(file, clazz);
    }

    public static <T> T fromJson(final Class<T> clazz, File file) throws IOException
    {
        return fromJson(Builder.makeAdapter(), clazz, file);
    }

    public String toJson(ObjectMapper objectMapper)
    {
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e)
        {
            e.printStackTrace();
        }
        return null;
    }

    public String toJson()
    {
        return toJson(Builder.makeAdapter());
    }

    public void toJson(ObjectMapper objectMapper, File file) throws Exception
    {
        objectMapper.writeValue(file, this);
    }

    public void toJson(File file) throws Exception
    {
        toJson(Builder.makeAdapter(), file);
    }
}
