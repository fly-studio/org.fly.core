package org.fly.core.text.lp.result;

import org.fly.core.text.json.Jsonable;

public class EncryptKey extends Jsonable {
    public String iv;
    public String mac;
    public String key;
}
