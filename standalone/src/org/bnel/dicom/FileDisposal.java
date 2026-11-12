package org.bnel.dicom;

import java.nio.file.*;
import java.util.*;

/** Explicit original-file disposal only. No permanent-delete fallback. */
final class FileDisposal {
    @FunctionalInterface interface Trash {boolean move(Path source)throws Exception;}
    record Result(Set<Path> moved,Map<Path,String> failed){}
    static boolean supported(){return NativeRecycle.AVAILABLE;}
    static Result recycle(Collection<Cleaner.Instance> selected)throws Exception{
        if(!supported())throw new IllegalArgumentException("Recycle Bin is unavailable here. Exclude the files from export instead; no permanent deletion is performed.");
        return recycle(selected,path->NativeRecycle.move(path.toString())!=null);
    }
    static Result recycle(Collection<Cleaner.Instance> selected,Trash trash){
        Set<Path> moved=new LinkedHashSet<>();Map<Path,String> failed=new LinkedHashMap<>();
        for(Cleaner.Instance file:selected){
            try{
                if(Files.isSymbolicLink(file.source())||!Files.isRegularFile(file.source(),LinkOption.NOFOLLOW_LINKS)||!DicomIO.hash(file.source()).equals(file.sha256()))throw new IllegalArgumentException("File changed since import; it was left in place.");
                if(!trash.move(file.source()))throw new IllegalArgumentException("Windows could not recycle this file. Network/removable drives and reparse points are refused; no permanent deletion was requested.");
                if(Files.exists(file.source()))throw new IllegalArgumentException("The original is still present; verify its location.");
                moved.add(file.source());
            }catch(Exception e){failed.put(file.source(),e instanceof IllegalArgumentException?e.getMessage():"Could not move this file; check permissions and whether another program has it open.");}
        }
        return new Result(Set.copyOf(moved),Map.copyOf(failed));
    }
}
