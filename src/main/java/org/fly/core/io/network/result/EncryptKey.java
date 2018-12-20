package org.fly.core.io.network.result;

import org.fly.core.annotation.NotProguard;
import org.fly.core.text.json.Jsonable;

@NotProguard
public class EncryptKey extends Jsonable {
    public String iv;
    public String mac;
    public String key;
}
