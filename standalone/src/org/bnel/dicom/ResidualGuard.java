package org.bnel.dicom;

import com.pixelmed.dicom.*;
import java.io.IOException;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Defense in depth. The primary rule deletes unrestricted text rather than guessing its meaning. */
final class ResidualGuard {
    private final Set<String> identifiers=new HashSet<>();
    private static final Set<Integer> ID_TAGS=Set.of(0x00100010,0x00100020,0x00101000,0x00101001,
        0x00080050,0x00181000,0x00181002,0x00200010,0x00380010,0x00401001);
    private static final Set<String> TEXT=Set.of("PN","LO","SH","LT","ST","UT","UC","UR","AE","AS","CS","UI");
    private static final Pattern SUSPICIOUS=Pattern.compile(
        "(?i)([A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}|https?://|(?:[A-Z]:\\\\|\\\\\\\\)[^ ]+|(?:patient|mrn|dob|ssn|accession)[ _:-]*[A-Z0-9]{3,}|\\b[0-9]{3}-[0-9]{2}-[0-9]{4}\\b)");
    static String normalize(String value) {
        return Normalizer.normalize(value,Normalizer.Form.NFKD).replaceAll("\\p{M}","")
            .toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]","");
    }
    void collect(AttributeList source) {
        for(DicomIO.Row row:DicomIO.rows(source)) {
            int k=Policy.key(row.attribute().getTag());
            if(ID_TAGS.contains(k)||row.vr().equals("PN")||IdentifierPolicy.category(source,row.attribute())!=null) {
                for(String value:row.value().split("[\\\\=]")) {
                    add(value);
                    if(row.vr().equals("PN")) for(String part:value.split("[\\^ ,]+")) add(part);
                }
            }
            if(row.vr().equals("DA")||row.vr().equals("DT"))for(String date:row.value().split("\\\\"))if(date.length()>=8)add(date.substring(0,8));
            // Every original nonstructural UID, at every depth.
            if(row.attribute().getTag().getGroup()!=2&&row.vr().equals("UI")&&UniqueIdentifierAttribute.isTransient(row.attribute().getTag()))
                for(String value:row.value().split("\\\\")) add(value);
        }
    }
    private void add(String value) {String n=normalize(value);if(n.length()>=4)identifiers.add(n);}
    void validateReplacement(Policy.Options options) throws IOException {
        check(options.patientName(),"replacement name");check(options.patientId(),"replacement ID");
    }
    private void check(String value,String location) throws IOException {
        String normalized=normalize(value);
        for(String token:identifiers) if(normalized.contains(token))
            throw new IOException("An original identifier was found in "+location+". Choose unrelated research identifiers or correct the source. Export is blocked.");
        if(SUSPICIOUS.matcher(value).find()) throw new IOException("Potential identifying text remains in "+location+". Export is blocked.");
    }
    void verify(AttributeList cleaned) throws IOException {
        for(DicomIO.Row row:DicomIO.rows(cleaned)) if(TEXT.contains(row.vr())&&!row.value().isBlank())
            check(row.value(),"metadata element "+row.path());
    }
}
