package me.andromedov.paradisepaints.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;

/** Per-client RGB favorites. The requested RGB is retained even when maps use a nearest palette match. */
public final class FavoriteColors {
    public static final int LIMIT=10;
    private final Path file;
    private final List<Integer> colors;

    public FavoriteColors(Path file) throws IOException {
        this.file=Objects.requireNonNull(file);
        colors=load(file);
    }
    private FavoriteColors(Path file,List<Integer> colors) { this.file=file; this.colors=colors; }
    public static FavoriteColors openClient() {
        Path file=FabricLoader.getInstance().getConfigDir().resolve("paradisepaints").resolve("favorite-colors.txt");
        try { return new FavoriteColors(file); }
        catch(IOException ignored) { return new FavoriteColors(file,new ArrayList<>()); }
    }
    public List<Integer> values() { return List.copyOf(colors); }
    public boolean contains(int rgb) { return colors.contains(rgb&0xffffff); }
    public boolean toggle(int rgb) throws IOException {
        rgb&=0xffffff;
        var next=new ArrayList<>(colors);
        boolean added=!next.remove((Integer)rgb);
        if(added) {
            next.add(0,rgb);
            while(next.size()>LIMIT) next.remove(next.size()-1);
        }
        persist(next);
        colors.clear(); colors.addAll(next);
        return added;
    }
    private static List<Integer> load(Path file) throws IOException {
        var result=new ArrayList<Integer>();
        if(!Files.exists(file)) return result;
        for(String line:Files.readAllLines(file,StandardCharsets.UTF_8)) {
            if(!line.matches("#[0-9A-Fa-f]{6}")) continue;
            int rgb=Integer.parseInt(line.substring(1),16);
            if(!result.contains(rgb) && result.size()<LIMIT) result.add(rgb);
        }
        return result;
    }
    private void persist(List<Integer> next) throws IOException {
        Path parent=file.getParent();
        if(parent!=null) Files.createDirectories(parent);
        Path temporary=Files.createTempFile(parent,"favorite-colors-",".tmp");
        try {
            List<String> lines=next.stream().map(rgb->String.format(Locale.ROOT,"#%06X",rgb)).toList();
            Files.write(temporary,lines,StandardCharsets.UTF_8,StandardOpenOption.TRUNCATE_EXISTING);
            try { Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException ignored) { Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
