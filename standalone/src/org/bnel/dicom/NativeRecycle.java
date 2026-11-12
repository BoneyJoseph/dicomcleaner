package org.bnel.dicom;

import java.nio.file.*;
import java.util.Locale;

/** Bundled Windows Shell adapter; refuses non-recyclable operations. */
final class NativeRecycle {
    static final boolean AVAILABLE=load();
    private static boolean load(){
        if(!System.getProperty("os.name","").toLowerCase(Locale.ROOT).startsWith("windows"))return false;
        try{
            Path location=Path.of(NativeRecycle.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path directory=Path.of(System.getProperty("bnel.nativeDir",location.getParent().toString()));
            System.load(directory.resolve("bnel-recycle.dll").toAbsolutePath().toString());return true;
        }catch(Exception|LinkageError e){return false;}
    }
    static native String move(String absolutePath);
}
