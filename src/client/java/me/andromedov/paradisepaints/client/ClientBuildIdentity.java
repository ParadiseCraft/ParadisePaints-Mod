package me.andromedov.paradisepaints.client;

import java.io.IOException;
import java.nio.file.*;
import java.security.*;
import java.util.Arrays;
import net.fabricmc.loader.api.FabricLoader;

/** SHA-256 of the exact installed JAR. Development class directories deliberately report zeroes. */
public final class ClientBuildIdentity {
    private static final byte[] UNAVAILABLE=new byte[32];
    private static volatile byte[] cached;
    private ClientBuildIdentity() {}

    public static byte[] sha256() {
        byte[] value=cached;
        if(value==null) {
            synchronized(ClientBuildIdentity.class) {
                value=cached;
                if(value==null) cached=value=discover();
            }
        }
        return value.clone();
    }

    private static byte[] discover() {
        try {
            var container=FabricLoader.getInstance().getModContainer("paradisepaints").orElseThrow();
            var paths=container.getOrigin().getPaths();
            if(paths.size()!=1 || !Files.isRegularFile(paths.getFirst())) return UNAVAILABLE.clone();
            return sha256(paths.getFirst());
        } catch(RuntimeException | IOException error) { return UNAVAILABLE.clone(); }
    }

    static byte[] sha256(Path file) throws IOException {
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(var input=Files.newInputStream(file)) {
                byte[] buffer=new byte[8192];
                for(int read;(read=input.read(buffer))>=0;) if(read>0) digest.update(buffer,0,read);
            }
            return digest.digest();
        } catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable",impossible); }
    }

    static boolean unavailable(byte[] hash) { return Arrays.equals(hash,UNAVAILABLE); }
}
