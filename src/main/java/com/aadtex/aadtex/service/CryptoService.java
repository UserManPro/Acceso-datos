package com.aadtex.aadtex.service;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class CryptoService {
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";
    public byte[] decrypt(byte[] encrypted, Path keyFile) throws IOException {
        byte[] key = Files.readString(keyFile, StandardCharsets.UTF_8).trim().getBytes(StandardCharsets.UTF_8);
        if (key.length != 16) throw new IllegalArgumentException("La clave AES debe tener exactamente 16 bytes.");
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"));
            return cipher.doFinal(encrypted);
        } catch (javax.crypto.BadPaddingException | javax.crypto.IllegalBlockSizeException e) {
            throw new IllegalArgumentException("No se pudo descifrar el lote; comprueba clave e integridad.", e);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("No está disponible el algoritmo requerido.", e);
        } finally { Arrays.fill(key, (byte) 0); }
    }
}
