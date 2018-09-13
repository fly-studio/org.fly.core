package org.fly.core.text.encrytor;

import com.sun.istack.Nullable;
import org.apache.commons.codec.binary.StringUtils;
import org.fly.core.io.IoUtils;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

public class Encryption {
    private static final org.fly.core.text.encrytor.Base64.Decoder base64Decoder = org.fly.core.text.encrytor.Base64.getDecoder();
    private static final org.fly.core.text.encrytor.Base64.Encoder base64Encoder = org.fly.core.text.encrytor.Base64.getEncoder();

    public static class Base64 {

        public static String encode(String bytes) {
            return encode(StringUtils.getBytesUsAscii(bytes));
        }

        public static String encode(byte[] bytes) {
            return StringUtils.newStringUsAscii(base64Encoder.encode(bytes));
        }

        public static byte[] decode(String base) {
            return base64Decoder.decode(base);
        }

        public static byte[] decode(byte[] base) {
            return base64Decoder.decode(base);
        }
    }

    public static class AES {
        private static final String AES  = "AES";

        private SecretKeySpec key;
        private IvParameterSpec iv;

        public AES(String key)
        {
            this(key, null);
        }

        public AES(byte[] key)
        {
            this(key, null);
        }

        public AES(String key, @Nullable String iv) {
            this(key.getBytes(), iv == null ? null : iv.getBytes());
        }

        public AES(byte[] key, byte[] iv)
        {
            this.key = new SecretKeySpec(key, AES);
            this.iv = iv == null ? null : new IvParameterSpec(iv);
        }

        public byte[] encrypt(byte[] originalText) throws NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, IllegalBlockSizeException
        {
            Cipher cipher = Cipher.getInstance(AES + "/CBC/PKCS5PADDING");
            if (iv == null)
                cipher.init(Cipher.ENCRYPT_MODE, key);
            else
                cipher.init(Cipher.ENCRYPT_MODE, key, iv);

            return cipher.doFinal(originalText);
        }


        public byte[] decrypt(byte[] encryptedText) throws NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, IllegalBlockSizeException
        {
            Cipher cipher = Cipher.getInstance(AES + "/CBC/PKCS5PADDING");
            if (iv == null)
                cipher.init(Cipher.DECRYPT_MODE, key);
            else
                cipher.init(Cipher.DECRYPT_MODE, key, iv);

            return cipher.doFinal(encryptedText);
        }

        public String encryptToBase64(byte[] originalText) throws NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, IllegalBlockSizeException
        {
            return Base64.encode(encrypt(originalText));
        }

        public byte[] decryptFromBase64(String encryptedText) throws NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, IllegalBlockSizeException
        {
            return decrypt(Base64.decode(encryptedText));
        }

    }

    public static class RSA {
        private static final String RSA  = "RSA";
        private static final String RSA_PUBLIC_BEGIN = "-----BEGIN PUBLIC KEY-----";
        private static final String RSA_PUBLIC_END = "-----END PUBLIC KEY-----";
        private static final String RSA_PRIVATE_BEGIN = "-----BEGIN RSA PRIVATE KEY-----";
        private static final String RSA_PRIVATE_END = "-----END RSA PRIVATE KEY-----";

        private PrivateKey privateKey;
        private PublicKey publicKey;

        public RSA() {
        }

        public RSA(PrivateKey privateKey, PublicKey publicKey) {
            this.privateKey = privateKey;
            this.publicKey = publicKey;
        }

        public void loadKey(File privateFile, File publicFile) throws NoSuchAlgorithmException, InvalidKeySpecException, IOException
        {
            loadPrivateFile(privateFile);
            loadPublicFile(publicFile);
        }

        public void loadKey(String privateKey, String publicKey) throws NoSuchAlgorithmException, InvalidKeySpecException
        {
            loadPrivateKey(privateKey);
            loadPublicKey(publicKey);
        }

        public void loadKey(byte[] privateKey, byte[] publicKey) throws NoSuchAlgorithmException, InvalidKeySpecException
        {
            loadPrivateKey(privateKey);
            loadPublicKey(publicKey);
        }

        public void loadPrivateKey(String key) throws NoSuchAlgorithmException, InvalidKeySpecException
        {

            key = key.replace(RSA_PRIVATE_BEGIN, "")
                    .replace(RSA_PRIVATE_END, "")
                    .replace("\r", "")
                    .replace("\n", "");
            loadPrivateKey(Base64.decode(key));
        }

        public void loadPublicKey(String key) throws NoSuchAlgorithmException, InvalidKeySpecException
        {
            key = key.replace(RSA_PUBLIC_BEGIN, "")
                    .replace(RSA_PUBLIC_END, "")
                    .replace("\r", "")
                    .replace("\n", "");

            loadPublicKey(Base64.decode(key));
        }

        public void loadPublicKey(byte[] bytes) throws NoSuchAlgorithmException, InvalidKeySpecException
        {
            X509EncodedKeySpec ks = new X509EncodedKeySpec(bytes);

            KeyFactory kf = KeyFactory.getInstance(RSA);

            publicKey = kf.generatePublic(ks);
        }

        public void loadPrivateKey(byte[] bytes) throws NoSuchAlgorithmException, InvalidKeySpecException
        {
            PKCS8EncodedKeySpec ks = new PKCS8EncodedKeySpec(bytes);

            KeyFactory kf = KeyFactory.getInstance(RSA);

            privateKey = kf.generatePrivate(ks);
        }

        public void loadPrivateFile(File file) throws NoSuchAlgorithmException, InvalidKeySpecException, IOException
        {
            byte[] bytes = IoUtils.readBytes(file);
            loadPrivateKey(bytes);
        }

        public void loadPublicFile(File file) throws NoSuchAlgorithmException, InvalidKeySpecException, IOException
        {
            byte[] bytes = IoUtils.readBytes(file);
            loadPublicKey(bytes);
        }

        public void generate() throws NoSuchAlgorithmException
        {
            generate(2048);
        }

        public void generate(int length) throws NoSuchAlgorithmException
        {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance(RSA);
            kpg.initialize(length);
            KeyPair kp = kpg.generateKeyPair();

            privateKey = kp.getPrivate();
            publicKey = kp.getPublic();
        }

        public String getPublicKey()
        {
            StringBuilder buffer = new StringBuilder();

            buffer.append(RSA_PUBLIC_BEGIN)
                    .append("\n")
                    .append(Base64.encode(publicKey.getEncoded())
                            .replaceAll("(.{64})", "$1\n")
                            .trim())
                    .append("\n")
                    .append(RSA_PUBLIC_END);

            return buffer.toString();
        }

        public String getPrivateKey()
        {
            StringBuilder buffer = new StringBuilder();

            buffer.append(RSA_PRIVATE_BEGIN)
                    .append("\n")
                    .append(Base64.encode(privateKey.getEncoded())
                            .replaceAll("(.{64})", "$1\n")
                            .trim())
                    .append("\n")
                    .append(RSA_PRIVATE_END);

            return buffer.toString();
        }

        public static byte[] encrypt(byte[] originalText, Key key) throws NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException
        {
            Cipher cipher = Cipher.getInstance(RSA + "/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key);

            return cipher.doFinal(originalText);
        }

        public static byte[] decrypt(byte[] encryptedText, Key key) throws NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException
        {
            Cipher cipher = Cipher.getInstance(RSA + "/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, key);

            return cipher.doFinal(encryptedText);
        }

        public byte[] publicEncrypt(byte[] originalText) throws NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException
        {
           return encrypt(originalText, publicKey);
        }

        public byte[] publicDecrypt(byte[] encryptedText) throws NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException
        {
            return decrypt(encryptedText, publicKey);
        }

        public byte[] privateEncrypt(byte[] originalText) throws NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException
        {
           return encrypt(originalText, privateKey);
        }

        public byte[] privateDecrypt(byte[] encryptedText) throws NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException
        {
            return decrypt(encryptedText, privateKey);
        }

        public String publicEncryptToBase64(byte[] originalText) throws NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException
        {
            return Base64.encode(publicEncrypt(originalText));
        }

        public byte[] publicDecryptFromBase64(String encryptedText) throws NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException
        {
            return publicDecrypt(Base64.decode(encryptedText));
        }

        public String privateEncryptToBase64(byte[] originalText) throws NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException
        {
            return Base64.encode(privateEncrypt(originalText));
        }

        public byte[] privateDecryptFromBase64(String encryptedText) throws NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException
        {
            return privateDecrypt(Base64.decode(encryptedText));
        }

    }
}
