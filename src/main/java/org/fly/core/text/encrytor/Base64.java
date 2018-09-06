package org.fly.core.text.encrytor;

import org.apache.commons.codec.binary.StringUtils;

public class Base64 implements Encryption.IBase64 {
    private static final org.apache.commons.codec.binary.Base64 base64 = new org.apache.commons.codec.binary.Base64();

    @Override
    public String encode(String bytes) {
        return encode(StringUtils.getBytesUsAscii(bytes));
    }

    @Override
    public String encode(byte[] bytes) {
        return StringUtils.newStringUsAscii(base64.encode(bytes));
    }

    @Override
    public byte[] decode(String base) {
        return base64.decode(base);
    }

    @Override
    public byte[] decode(byte[] base) {
        return base64.decode(base);
    }
}
