package org.fly.core.text.lp.result;

import org.fly.core.text.json.Jsonable;

public class EncryptedResult extends Result {

    public String encrypted = "";
    public String data;

    public static class Encrypted extends Jsonable {
        public String iv;
        public String value;
        public String mac;
    }

}
