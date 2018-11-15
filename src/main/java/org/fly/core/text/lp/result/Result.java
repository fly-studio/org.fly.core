package org.fly.core.text.lp.result;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.fly.core.text.json.Jsonable;

import java.util.Date;

public class Result extends Jsonable
{
    public enum RESULT {
        api,
        success,
        failure,
        error,
        notice,
        warning
    }
    public boolean encrypted = false;
    public RESULT result;
    @JsonProperty("status_code") public int statusCode = 200;
    public int uid;
    public boolean debug;
    public Object data;
    public Date time;
    public float duration;

}