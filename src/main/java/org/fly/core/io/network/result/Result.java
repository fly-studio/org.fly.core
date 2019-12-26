package org.fly.core.io.network.result;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import org.fly.core.annotation.NotProguard;
import org.fly.core.text.json.Jsonable;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Date;

@NotProguard
public class Result extends Jsonable
{
    public int code = 0;
    public String message;
    @JsonDeserialize(using = ActionDeserialize.class)
    public Action action;
    @JsonRawValue
    public String data;
    public long uid;
    public Date at;
    public long duration;
    public String encrypted;

    public static class Action {
        public String type;
        public int timeout;
        public String url;

        public Action(@Nullable String type, @Nullable int timeout, @Nullable String url) {
            this.type = type;
            this.timeout = timeout;
            this.url = url;
        }
    }

    public void setAt(long at) {
        this.at = new Date(at);
    }

    public void setAt(Date at) {
        this.at = at;
    }

    private static class ActionDeserialize extends JsonDeserializer<Action> {
        @Override
        public Action deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            if (p.currentToken() == JsonToken.START_ARRAY)
            {
                JsonNode node = p.getCodec().readTree(p);

                int size = node.size();
                String type = null, url = null;
                int timeout = 0;

                if (size >= 1)
                    type = node.get(0).asText();

                if (size >= 2)
                    timeout = node.get(1).asInt();

                if (size >= 3)
                    url = node.get(2).asText();

                return new Action(type, timeout, url);
            }

            return null;
        }
    }
}
