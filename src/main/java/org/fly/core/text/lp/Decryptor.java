package org.fly.core.text.lp;

import org.apache.commons.codec.binary.StringUtils;
import org.fly.core.text.encrytor.Encryption;
import org.fly.core.text.json.Jsonable;
import org.fly.core.text.lp.result.EncryptedResult;

public class Decryptor {

    private Encryption.RSA rsa;

    public Decryptor(Encryption.IBase64 base64) {

        Encryption.setBase64(base64);

        rsa = new Encryption.RSA();

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

    public String decode(String key, String data)
    {
        if (key == null || key.isEmpty() || data == null || data.isEmpty())
            return null;

        try {
            byte[] keyBytes = rsa.privateDecryptFromBase64(key);
            byte[] dataBytes = Encryption.Base64.decode(data);

            EncryptedResult.Encrypted encrypted = Jsonable.fromJson(EncryptedResult.Encrypted.class, StringUtils.newStringUsAscii(dataBytes));

            Encryption.AES aes = new Encryption.AES(
                    Encryption.Base64.decode(keyBytes),
                    Encryption.Base64.decode(encrypted.iv)
            );

            byte[] realBytes = aes.decryptFromBase64(encrypted.value);

            return StringUtils.newStringUsAscii(realBytes);
        } catch (Exception e)
        {
            e.printStackTrace();
        }

        return null;
    }
}
