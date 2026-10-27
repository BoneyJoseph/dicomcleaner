package org.bnel.dicom;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Portable preference file; never persists patient fields, source paths, or review decisions. */
final class AppSettings {
    final Properties values=new Properties();
    final Path path;
    AppSettings(){
        Path base;
        String configured=System.getProperty("bnel.settingsDir");
        if(configured!=null)base=Path.of(configured);
        else {
            String launcher=System.getProperty("jpackage.app-path");
            base=launcher==null?Path.of(System.getProperty("user.home"),".bnel-dicom-cleaner"):Path.of(launcher).getParent();
        }
        path=base.resolve("BNEL-settings.properties");
        try(InputStream in=Files.newInputStream(path)){values.load(in);}catch(IOException ignored){}
    }
    boolean bool(String key,boolean fallback){return Boolean.parseBoolean(values.getProperty(key,String.valueOf(fallback)));}
    int integer(String key,int fallback){try{return Integer.parseInt(values.getProperty(key));}catch(Exception e){return fallback;}}
    String text(String key,String fallback){return values.getProperty(key,fallback);}
    void set(String key,Object value){values.setProperty(key,String.valueOf(value));}
    void save() throws IOException {
        Files.createDirectories(path.getParent());Path temp=Files.createTempFile(path.getParent(),".bnel-preferences-",".tmp");
        try {
            try(OutputStream out=Files.newOutputStream(temp)){values.store(out,"BNEL Dicom Cleaner — options and layout only");}
            Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);
        } finally {Files.deleteIfExists(temp);}
    }
}
