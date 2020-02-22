package org.fly.core.io.network.result;

import org.fly.core.annotation.NotProguard;
import org.fly.core.text.json.Jsonable;

@NotProguard
public class EncryptedResult extends Jsonable {
    public String data;
    public String encrypted;
}
