package dev.harsh.protonbridge;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class ProfileStore {
    private static final String ALIAS = "personal.proton.wireguard.profiles.v1";
    private final Context context;
    private final AtomicFile vault;
    ProfileStore(Context context) {
        this.context = context.getApplicationContext();
        vault = new AtomicFile(new File(context.getNoBackupFilesDir(), "proton-profiles.vault"));
    }
    synchronized List<WireGuardProfile> load() throws Exception {
        if (!vault.getBaseFile().exists()) {
            List<WireGuardProfile> initial = new ArrayList<>();
            try (InputStream in = context.getAssets().open("proton_personal/index.json")) {
                JSONArray index = new JSONArray(readLimited(in, 16384));
                for (int i=0;i<index.length();i++) {
                    JSONObject item = index.getJSONObject(i);
                    try (InputStream cfg = context.getAssets().open("proton_personal/"+item.getString("file"))) {
                        initial.add(WireGuardProfile.parse(item.getString("id"), item.getString("label"),
                                readLimited(cfg, WireGuardProfile.MAX_BYTES)));
                    }
                }
            }
            if (initial.isEmpty()) throw new Exception("Import a Proton WireGuard configuration to begin.");
            save(initial); return initial;
        }
        byte[] encrypted = vault.readFully();
        if (encrypted.length < 30 || encrypted.length > 1024*1024 || encrypted[0] != 1 || encrypted[1] != 12)
            throw new Exception("Saved profiles cannot be read. Import them again.");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        byte[] iv = java.util.Arrays.copyOfRange(encrypted,2,14);
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128,iv));
        byte[] plain = cipher.doFinal(encrypted,14,encrypted.length-14);
        try {
            JSONArray array = new JSONArray(new String(plain, StandardCharsets.UTF_8));
            List<WireGuardProfile> result = new ArrayList<>();
            for (int i=0; i<array.length(); i++) {
                JSONObject p = array.getJSONObject(i);
                result.add(WireGuardProfile.parse(p.getString("id"),p.getString("label"),p.getString("config")));
            }
            return result;
        } finally { java.util.Arrays.fill(plain,(byte)0); }
    }
    synchronized void save(List<WireGuardProfile> profiles) throws Exception {
        JSONArray array = new JSONArray();
        for (WireGuardProfile p : profiles) {
            JSONObject entry = new JSONObject();
            entry.put("id",p.id); entry.put("label",p.label);
            entry.put("config",p.originalForEncryptedStorage()); array.put(entry);
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] plain = array.toString().getBytes(StandardCharsets.UTF_8);
        byte[] encrypted;
        try { encrypted = cipher.doFinal(plain); }
        finally { java.util.Arrays.fill(plain,(byte)0); }
        FileOutputStream out = null;
        try {
            out = vault.startWrite();
            out.write(1); out.write(cipher.getIV().length); out.write(cipher.getIV()); out.write(encrypted);
            vault.finishWrite(out);
        } catch (Exception e) { if (out != null) vault.failWrite(out); throw e; }
    }
    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (store.containsAlias(ALIAS)) return (SecretKey)store.getKey(ALIAS,null);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build());
        return generator.generateKey();
    }
    static String readLimited(InputStream in, int maximum) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096]; int n;
        while ((n = in.read(buf)) != -1) {
            if (out.size()+n > maximum) throw new Exception("Configuration file is too large.");
            out.write(buf,0,n);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
