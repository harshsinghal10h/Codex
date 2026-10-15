package com.harsh.vlcglass;
import android.content.Context;
import android.security.keystore.*;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** API keys and companion tokens are encrypted with a device-bound key. */
public final class CredentialStore {
 private final Context context;
 public CredentialStore(Context c){context=c.getApplicationContext();}
 private SecretKey key()throws Exception{
  KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
  if(!store.containsAlias("VLCGlassAnalysis")){KeyGenerator gen=KeyGenerator.getInstance("AES","AndroidKeyStore");gen.init(new KeyGenParameterSpec.Builder("VLCGlassAnalysis",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());gen.generateKey();}
  return (SecretKey)store.getKey("VLCGlassAnalysis",null);
 }
 public void put(String name,String value)throws Exception{
  if(value==null||value.isEmpty())return;
  Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());
  String saved=Base64.encodeToString(c.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(c.doFinal(value.getBytes("UTF-8")),Base64.NO_WRAP);
  context.getSharedPreferences("analysis-secrets",0).edit().putString(name,saved).apply();
 }
 public String get(String name)throws Exception{
  String raw=context.getSharedPreferences("analysis-secrets",0).getString(name,"");if(raw.isEmpty())return "";
  String[] parts=raw.split(":",2);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));return new String(c.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),"UTF-8");
 }
}
