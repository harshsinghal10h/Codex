import brut.androlib.smali.SmaliBuilder;
import brut.androlib.smali.SmaliDecoder;
import com.android.tools.smali.dexlib2.Opcodes;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.writer.builder.DexBuilder;
import com.android.tools.smali.dexlib2.writer.io.FileDataStore;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

/** Uses Apktool's pinned smali library without rebuilding Chromium resources. */
public final class SmaliBridge {
    public static void main(String[] args) throws Exception {
        if (args.length!=3) throw new IllegalArgumentException("decode APK DIRECTORY | assemble DIRECTORY DEX");
        if (args[0].equals("decode")) {
            File apk=new File(args[1]);
            byte[] data;
            try (ZipFile zip=new ZipFile(apk)) {
                data=zip.getInputStream(zip.getEntry("classes5.dex")).readAllBytes();
            }
            new SmaliDecoder(apk,true).decodeFile(new DexBackedDexFile(data,0),new File(args[2]));
        } else if (args[0].equals("assemble")) {
            DexBuilder dex=new DexBuilder(new Opcodes(29));
            SmaliBuilder compiler=new SmaliBuilder(29);
            List<Path> files;
            try (var paths=Files.walk(Path.of(args[1]))) {
                files=paths.filter(p -> p.toString().endsWith(".smali")).sorted().collect(Collectors.toList());
            }
            for (Path file:files) {
                if (!compiler.buildFile(file.toFile(),dex)) throw new IllegalStateException("Smali compilation failed");
            }
            dex.writeTo(new FileDataStore(new File(args[2])));
        } else throw new IllegalArgumentException("Unknown operation");
    }
}
