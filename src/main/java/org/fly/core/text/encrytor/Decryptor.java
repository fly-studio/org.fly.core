package org.fly.core.text.encrytor;

import com.sun.istack.NotNull;
import com.sun.istack.Nullable;
import org.apache.commons.codec.binary.StringUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.fly.core.io.network.result.EncryptKey;
import org.fly.core.io.network.result.Result;
import org.fly.core.text.json.Jsonable;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

public class Decryptor {

    private Encryption.RSA rsa;
    private KEY_MODE key_mode;
    private enum KEY_MODE {
        Own,
        ThirdParty
    }

    public Decryptor() {
        rsa = new Encryption.RSA();
        random();
        key_mode = KEY_MODE.Own;
    }

    public Decryptor(@NotNull String publicKey, @Nullable String privateKey) {
        rsa = new Encryption.RSA();
        try {
            if (privateKey != null) rsa.loadPrivateKey(privateKey);
            rsa.loadPublicKey(publicKey);
            key_mode = KEY_MODE.ThirdParty;

        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.printStackTrace();
        }
    }

    public void random() {
        try {
            rsa.generate();
            key_mode = KEY_MODE.Own;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String getPublicKey() {
        return rsa.getPublicKey();
    }

    public byte[] decodeKey(String key) {
        return decodeKey(Encryption.Base64.decode(key));
    }

    public byte[] decodeKey(byte[] key) {
        try {
            return rsa.privateDecrypt(key);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public Result encodeData(String data)
    {
        if (data == null || data.isEmpty())
            return null;

        try {
            byte[] key = Encryption.randomBytes(32);
            byte[] iv = Encryption.randomBytes(16);
            byte[] value = new Encryption.AES(key, iv).encrypt(data.getBytes());
            String mac = Encryption.HMac.sha256(ArrayUtils.addAll(Encryption.Base64.encode(iv).getBytes(), value) , key);

            EncryptKey encryptKey = new EncryptKey();
            encryptKey.mac = mac;
            encryptKey.iv = Encryption.Base64.encode(iv);
            encryptKey.key = Encryption.Base64.encode(key);

            Result result = new Result();
            result.encrypted = key_mode == KEY_MODE.ThirdParty ? rsa.publicEncryptToBase64(encryptKey.toJson().getBytes()) : rsa.privateEncryptToBase64(encryptKey.toJson().getBytes());
            result.data = Encryption.Base64.encode(value);

            return result;
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | BadPaddingException | InvalidAlgorithmParameterException | InvalidKeyException | IllegalBlockSizeException e) {
            e.printStackTrace();
        }

        return null;
    }

    public String decodeData(Result result)
    {
        return decodeData(result.encrypted, result.data);
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

            byte[] keyBytes = key_mode == KEY_MODE.ThirdParty ? rsa.publicDecrypt(encrypted) : rsa.privateDecrypt(encrypted);

            EncryptKey encryptKey = Jsonable.fromJson(EncryptKey.class, keyBytes);

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
