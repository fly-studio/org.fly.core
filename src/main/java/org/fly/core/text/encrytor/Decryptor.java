package org.fly.core.text.encrytor;

import org.apache.commons.codec.binary.StringUtils;
import org.fly.core.text.json.Jsonable;

public class Decryptor {

    private Encryption.RSA rsa;

    public Decryptor() {
        rsa = new Encryption.RSA();

        random();
    }

    public void random()
    {
        try
        {
            rsa.generate();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getPublicKey()
    {
        return rsa.getPublicKey();
    }

    public byte[] decodeKey(String key)
    {
        return decodeKey(Encryption.Base64.decode(key));
    }

    public byte[] decodeKey(byte[] key)
    {
        try {
            return rsa.privateDecrypt(key);
        } catch (Exception e)
        {
            e.printStackTrace();
            return null;
        }
    }

    public String decodeData(String encrypted, String data)
    {
        if (encrypted == null || encrypted.isEmpty() || data == null || data.isEmpty())
            return null;

        return decodeData(
                Encryption.Base64.decode(encrypted),
                Encryption.Base64.decode(data)
        );
    }

    public String decodeData(byte[] encrypted, byte[] data)
    {
        if (encrypted == null || encrypted.length == 0 || data == null || data.length == 0)
            return null;

        try {

            byte[] keyBytes = rsa.privateDecrypt(encrypted);

            org.fly.core.io.network.result.EncryptKey encryptKey = Jsonable.fromJson(org.fly.core.io.network.result.EncryptKey.class, keyBytes);

            Encryption.AES aes = new Encryption.AES(
                Encryption.Base64.decode(encryptKey.key),
                Encryption.Base64.decode(encryptKey.iv)
            );

            byte[] realBytes = aes.decrypt(data);

            return StringUtils.newStringUtf8(realBytes);

        } catch (Exception e)
        {
            e.printStackTrace();
        }

        return null;
    }
}
