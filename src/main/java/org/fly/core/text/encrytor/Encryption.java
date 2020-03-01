package org.fly.core.text.encrytor;

import org.apache.commons.codec.binary.StringUtils;
import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.MessageDigestAlgorithms;
import org.fly.core.io.IoUtils;
import org.jetbrains.annotations.Nullable;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.interfaces.RSAKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Random;

/*import org.bouncycastle.asn1.ASN1Object;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.pkcs.RSAPrivateKey;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.jce.provider.BouncyCastleProvider;*/

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
        StringBuilder sb = new StringBuilder();
        String temp = null;

        for (int i = 0; i < bytes.length; i++) {
            temp = Integer.toHexString(bytes[i] & 0xFF);

            if (temp.length() == 1) {
                // 1得到一位的进行补0操作
                sb.append("0");
            }

            sb.append(temp);
        }

        return sb.toString();
    }

    /**
     * Hash类，比如MD5/SHA-1/SHA-256
     */
    public static class Hash {
        public static String Sha1(String string)  {
            return Sha1(string.getBytes());
        }

        public static String Sha1(byte[] bytes) {
            try {
                return hash(MessageDigestAlgorithms.SHA_1, bytes);
            } catch (NoSuchAlgorithmException e) {
                return "";
            }
        }

        public static String Md5(String string)  {
            return Md5(string.getBytes());
        }

        public static String Md5(byte[] bytes) {
            try {
                return hash(MessageDigestAlgorithms.MD5, bytes);
            } catch (NoSuchAlgorithmException e) {
                return "";
            }
        }

        /**
         * Returns the lowercase hex string representation of a file's MD5 hash sum.
         */
        public static String Md5File(String file) throws IOException {
            try {
                return hashFile(MessageDigestAlgorithms.MD5, file);
            } catch (NoSuchAlgorithmException e) {
                return "";
            }
        }

        public static String Sha256(String string)  {
            return Sha256(string.getBytes());
        }

        public static String Sha256(byte[] bytes) {
            try {
                return hash(MessageDigestAlgorithms.SHA_256, bytes);
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

        public static String hash(String algorithm, String bytes) throws NoSuchAlgorithmException
        {
            return hash(algorithm, bytes.getBytes());
        }

        public static String hashFile(String algorithm, String file) throws NoSuchAlgorithmException, IOException
        {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            InputStream is = new FileInputStream(file);

            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            is.close();

            return byte2Hex(digest.digest());
        }

    }

    public static class HMac {
        public static String sha1(byte[] bytes, byte[] key) throws NoSuchAlgorithmException, InvalidKeyException
        {
            return hmac(HmacAlgorithms.HMAC_SHA_1.getName(), bytes, key);
        }

        public static String sha256(byte[] bytes, byte[] key) throws NoSuchAlgorithmException, InvalidKeyException
        {
            return hmac(HmacAlgorithms.HMAC_SHA_256.getName(), bytes, key);
        }

        public static String md5(byte[] bytes, byte[] key) throws NoSuchAlgorithmException, InvalidKeyException
        {
            return hmac(HmacAlgorithms.HMAC_MD5.getName(), bytes, key);
        }

        public static String hmac(String algorithm, byte[] bytes, byte[] key) throws NoSuchAlgorithmException, InvalidKeyException
        {
            SecretKeySpec signingKey = new SecretKeySpec(key, algorithm);
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance(algorithm);
            mac.init(signingKey);

            return byte2Hex(mac.doFinal(bytes));
        }

        public static String hmac(String algorithm, String bytes, String key) throws NoSuchAlgorithmException, InvalidKeyException
        {
            return hmac(algorithm, bytes.getBytes(), key.getBytes());
        }

        public static String hmac(HmacAlgorithms algorithm, byte[] bytes, byte[] key) throws NoSuchAlgorithmException, InvalidKeyException
        {
            return hmac(algorithm.getName(), bytes, key);
        }

        public static String hmac(HmacAlgorithms algorithm, String bytes, String key) throws NoSuchAlgorithmException, InvalidKeyException
        {
            return hmac(algorithm, bytes.getBytes(), key.getBytes());
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

        public final static String LINE = "-----";

        public enum FORMAT {
            PUBLIC("PUBLIC KEY"),
            PRIVATE("RSA PRIVATE KEY"),
            PRIVATE8("PRIVATE KEY"),
            PRIVATE8_ENCRYPTED("ENCRYPTED PRIVATE KEY");

            private String value;

            FORMAT(String value) {
                this.value = value;
            }

            @Override
            public String toString() {
                return value;
            }
        }

        private PrivateKey privateKey;
        private PublicKey publicKey;

        public RSA() {
            //Security.addProvider(new BouncyCastleProvider());
        }

        public RSA(PrivateKey privateKey, PublicKey publicKey) {
            this.privateKey = privateKey;
            this.publicKey = publicKey;
            //Security.addProvider(new BouncyCastleProvider());
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

        /**
         * Pkcs1 or Pkcs8 KEY
         * @param key
         * @throws NoSuchAlgorithmException
         * @throws InvalidKeySpecException
         */
        public void loadPrivateKey(String key) throws NoSuchAlgorithmException, InvalidKeySpecException
        {
            loadPrivateKey(pemToBytes(key));
        }

        public void loadPublicKey(String key) throws NoSuchAlgorithmException, InvalidKeySpecException
        {
            loadPublicKey(pemToBytes(key));
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
            String key = IoUtils.read(file, StandardCharsets.UTF_8);
            loadPrivateKey(key);
        }

        public void loadPublicFile(File file) throws NoSuchAlgorithmException, InvalidKeySpecException, IOException
        {
            String key = IoUtils.read(file, StandardCharsets.UTF_8);
            loadPublicKey(key);
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

  /*      public static String pkcs8ToPkcs1(String key) throws IOException {

            byte[] pkcs1 = pkcs8ToPkcs1(pemToBytes(key));

            return bytesToPem(FORMAT.PRIVATE, pkcs1);
        }

        public static String pkcs1ToPkcs8(String key) throws IOException {

            byte[] pkcs8 = pkcs1ToPkcs8(pemToBytes(key));

            return bytesToPem(FORMAT.PRIVATE8, pkcs8);
        }

        public static byte[] pkcs8ToPkcs1(byte[] key) throws IOException
        {
            PrivateKeyInfo pki = PrivateKeyInfo.getInstance(key);
            RSAPrivateKey pkcs1Key = RSAPrivateKey.getInstance(pki.parsePrivateKey());
            return pkcs1Key.getEncoded();
        }

        public static byte[] pkcs1ToPkcs8(byte[] key) throws IOException
        {
            AlgorithmIdentifier algorithmIdentifier = new AlgorithmIdentifier(PKCSObjectIdentifiers.pkcs8ShroudedKeyBag);    //PKCSObjectIdentifiers.pkcs8ShroudedKeyBag
            ASN1Object asn1Object = ASN1Sequence.getInstance(key);
            PrivateKeyInfo pki = new PrivateKeyInfo(algorithmIdentifier, asn1Object);
            return pki.getEncoded();
        }
*/
        private static byte[] pemToBytes(String key)
        {
            key = key.replace(LINE + "BEGIN " + FORMAT.PUBLIC + LINE, "")
                    .replace(LINE + "BEGIN " + FORMAT.PRIVATE + LINE, "")
                    .replace(LINE + "BEGIN " + FORMAT.PRIVATE8 + LINE, "")
                    .replace(LINE + "BEGIN " + FORMAT.PRIVATE8_ENCRYPTED + LINE, "")
                    .replace(LINE + "END " + FORMAT.PUBLIC + LINE, "")
                    .replace(LINE + "END " + FORMAT.PRIVATE + LINE, "")
                    .replace(LINE + "END " + FORMAT.PRIVATE8 + LINE, "")
                    .replace(LINE + "END " + FORMAT.PRIVATE8_ENCRYPTED + LINE, "")
                    .replace("\r", "")
                    .replace("\n", "");
            return Base64.decode(key);
        }

        private static String bytesToPem(FORMAT type, byte[] key) {
            StringBuilder buffer = new StringBuilder();

            buffer.append(LINE + "BEGIN ").append(type).append(LINE)
                    .append("\n")
                    .append(Base64.encode(key)
                            .replaceAll("(.{64})", "$1\n")
                            .trim())
                    .append("\n")
                    .append(LINE + "END ").append(type).append(LINE);

            return buffer.toString();

            /*PemObject pemObject = new PemObject(type, privateKey);
            StringWriter stringWriter = new StringWriter();
            PemWriter pemWriter = new PemWriter(stringWriter);
            pemWriter.writeObject(pemObject);
            pemWriter.close();
            return stringWriter.toString();*/
        }


        public String getPublicKey()
        {
            return publicKey == null ? null : bytesToPem(FORMAT.PUBLIC, publicKey.getEncoded());
        }

        public String getPrivateKey()
        {
            return privateKey == null ? null : bytesToPem(FORMAT.PRIVATE8, privateKey.getEncoded());
        }

        public static Cipher cipher() throws NoSuchPaddingException, NoSuchAlgorithmException
        {
            return Cipher.getInstance(RSA + "/ECB/PKCS1Padding");
        }

        public static byte[] encrypt(byte[] plaintext, Key key) throws NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException
        {
            Cipher cipher = cipher();
            cipher.init(Cipher.ENCRYPT_MODE, key);

            int blockSize = ((RSAKey)key).getModulus().bitLength() / 8;

            try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
                int len = plaintext.length;
                int offset = 0;

                while (offset < len)
                {
                    int read = Math.min(len - offset, blockSize - 11); // PADDING 的需要-11
                    byteArrayOutputStream.write(cipher.doFinal(plaintext, offset, read));
                    offset += read;
                }

                return byteArrayOutputStream.toByteArray();
            } catch (IOException e) {
                e.printStackTrace();
            }
           return null;
        }

        public static byte[] decrypt(byte[] encryptedText, Key key) throws NoSuchPaddingException, BadPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException
        {
            Cipher cipher = cipher();
            cipher.init(Cipher.DECRYPT_MODE, key);

            int blockSize = ((RSAKey)key).getModulus().bitLength() / 8;

            try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
                int len = encryptedText.length;
                int offset = 0;

                while (offset < len)
                {
                    int read = Math.min(len - offset, blockSize);
                    byteArrayOutputStream.write(cipher.doFinal(encryptedText, offset, read));
                    offset += read;
                }

                return byteArrayOutputStream.toByteArray();

            } catch (IOException e) {
                e.printStackTrace();
            }

            return null;
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
