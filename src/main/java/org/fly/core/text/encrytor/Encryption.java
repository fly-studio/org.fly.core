package org.fly.core.text.encrytor;

import com.sun.istack.Nullable;
import org.apache.commons.codec.binary.StringUtils;
import org.fly.core.io.IoUtils;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Random;

public class Encryption {
    private static final org.fly.core.text.encrytor.Base64.Decoder base64Decoder = org.fly.core.text.encrytor.Base64.getDecoder();
    private static final org.fly.core.text.encrytor.Base64.Encoder base64Encoder = org.fly.core.text.encrytor.Base64.getEncoder();

    /**
     * 随机bytes
     *
     * @param size
     * @return
     */
    public static byte[] randomBytes(int size)
    {
        byte[] bytes = new byte[size];
        Random rnd = new Random();
        rnd.nextBytes(bytes);

        return bytes;
    }

    /**
     * 将byte转为16进制
     *
     * @param bytes
     * @return
     */
    public static String byte2Hex(byte[] bytes) {
        StringBuffer stringBuffer = new StringBuffer();
        String temp = null;

        for (int i = 0; i < bytes.length; i++) {
            temp = Integer.toHexString(bytes[i] & 0xFF);

            if (temp.length() == 1) {
                // 1得到一位的进行补0操作
                stringBuffer.append("0");
            }

            stringBuffer.append(temp);
        }

        return stringBuffer.toString();
    }

    public static class Hash {
        public static String Sha1(String string)  {
            return Sha1(string.getBytes());
        }

        public static String Sha1(byte[] bytes) {
            try {
                return hash("SHA-1", bytes);
            } catch (NoSuchAlgorithmException e) {
                return "";
            }
        }

        public static String Md5(String string)  {
            return Md5(string.getBytes());
        }

        public static String Md5(byte[] bytes) {
            try {
                return hash("MD5", bytes);
            } catch (NoSuchAlgorithmException e) {
                return "";
            }
        }

        /**
         * Returns the lowercase hex string representation of a file's MD5 hash sum.
         */
        public static String Md5File(String file) throws IOException {
            try {
                MessageDigest digest = MessageDigest.getInstance("MD5");
                InputStream is = new FileInputStream(file);
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
                is.close();

                return byte2Hex(digest.digest());
            } catch (NoSuchAlgorithmException e) {
                return "";
            }
        }

        public static String Sha256(String string)  {
            return Sha256(string.getBytes());
        }

        public static String Sha256(byte[] bytes) {
            try {
                return hash("SHA-256", bytes);
            } catch (NoSuchAlgorithmException e) {
                return "";
            }
        }

        public static String hash(String algorithm, byte[] bytes) throws NoSuchAlgorithmException
        {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            digest.update(bytes);

            return byte2Hex(digest.digest());
        }
    }

    public static class Mac {
        public static String sha1(byte[] bytes, byte[] key) throws NoSuchAlgorithmException, InvalidKeyException
        {
            return hmac("HmacSHA1", bytes, key);
        }

        public static String sha256(byte[] bytes, byte[] key) throws NoSuchAlgorithmException, InvalidKeyException
        {
            return hmac("HmacSHA256", bytes, key);
        }

        public static String md5(byte[] bytes, byte[] key) throws NoSuchAlgorithmException, InvalidKeyException
        {
            return hmac("HmacMD5", bytes, key);
        }

        public static String hmac(String algorithm, byte[] bytes, byte[] key) throws NoSuchAlgorithmException, InvalidKeyException
        {
            SecretKeySpec signingKey = new SecretKeySpec(key, algorithm);
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance(algorithm);
            mac.init(signingKey);

            return byte2Hex(mac.doFinal(bytes));
        }
    }

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
            this(key.getBytes(), iv == null ? null : StringUtils.getBytesUsAscii(iv));
        }

        public AES(byte[] key, byte[] iv)
        {
            this.key = new SecretKeySpec(key, AES);
            this.iv = new IvParameterSpec(iv == null ? new byte[getBlockSize()] : iv); // fill 16 blank iv
        }

        public static int getBlockSize()
        {
            try {
                return cipher().getBlockSize();
            } catch (Exception e)
            {
                return 16;
            }
        }

        public static Cipher cipher() throws NoSuchAlgorithmException, NoSuchPaddingException
        {
            return Cipher.getInstance(AES + "/CBC/PKCS5Padding");
        }

        public byte[] encrypt(byte[] plaintext) throws NoSuchAlgorithmException, NoSuchPaddingException, BadPaddingException,  InvalidAlgorithmParameterException, InvalidKeyException, IllegalBlockSizeException
        {
            Cipher cipher = cipher();

            cipher.init(Cipher.ENCRYPT_MODE, key, iv);

            return cipher.doFinal(plaintext);
        }


        public byte[] decrypt(byte[] encryptedText) throws NoSuchAlgorithmException, NoSuchPaddingException, BadPaddingException, InvalidAlgorithmParameterException, InvalidKeyException, IllegalBlockSizeException
        {
            Cipher cipher = cipher();

            cipher.init(Cipher.DECRYPT_MODE, key, iv);

            return cipher.doFinal(encryptedText);
        }

        public String encryptToBase64(byte[] plaintext) throws NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, IllegalBlockSizeException
        {
            return Base64.encode(encrypt(plaintext));
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

        public static Cipher cipher() throws NoSuchPaddingException, NoSuchAlgorithmException
        {
            return Cipher.getInstance(RSA + "/ECB/PKCS1Padding");
        }

        public static byte[] encrypt(byte[] plaintext, Key key) throws NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException
        {
            Cipher cipher = cipher();
            cipher.init(Cipher.ENCRYPT_MODE, key);

            return cipher.doFinal(plaintext);
        }

        public static byte[] decrypt(byte[] encryptedText, Key key) throws NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException
        {
            Cipher cipher = cipher();
            cipher.init(Cipher.DECRYPT_MODE, key);

            return cipher.doFinal(encryptedText);
        }

        public byte[] publicEncrypt(byte[] plaintext) throws NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException
        {
           return encrypt(plaintext, publicKey);
        }

        public byte[] publicDecrypt(byte[] encryptedText) throws NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException
        {
            return decrypt(encryptedText, publicKey);
        }

        public byte[] privateEncrypt(byte[] plaintext) throws NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException
        {
           return encrypt(plaintext, privateKey);
        }

        public byte[] privateDecrypt(byte[] encryptedText) throws NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException
        {
            return decrypt(encryptedText, privateKey);
        }

        public String publicEncryptToBase64(byte[] plaintext) throws NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException
        {
            return Base64.encode(publicEncrypt(plaintext));
        }

        public byte[] publicDecryptFromBase64(String encryptedText) throws NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException
        {
            return publicDecrypt(Base64.decode(encryptedText));
        }

        public String privateEncryptToBase64(byte[] plaintext) throws NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException
        {
            return Base64.encode(privateEncrypt(plaintext));
        }

        public byte[] privateDecryptFromBase64(String encryptedText) throws NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException
        {
            return privateDecrypt(Base64.decode(encryptedText));
        }

    }
}
