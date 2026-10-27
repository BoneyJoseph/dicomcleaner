package org.bnel.dicom;

import com.pixelmed.dicom.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;

public final class DicomIO {
    private DicomIO() {}
    public record Loaded(AttributeList list, String syntax) {}
    public record Row(String path, String name, String vr, int vm, long bytes, String value, Attribute attribute) {}
    public static final Set<String> SUPPORTED = Set.of(
        SOPClass.CTImageStorage, SOPClass.EnhancedCTImageStorage, SOPClass.LegacyConvertedEnhancedCTImageStorage,
        SOPClass.MRImageStorage, SOPClass.EnhancedMRImageStorage, SOPClass.EnhancedMRColorImageStorage,
        SOPClass.LegacyConvertedEnhancedMRImageStorage, SOPClass.SpatialRegistrationStorage,
        SOPClass.DeformableSpatialRegistrationStorage, SOPClass.SecondaryCaptureImageStorage,
        SOPClass.MultiframeGrayscaleByteSecondaryCaptureImageStorage,
        SOPClass.MultiframeGrayscaleWordSecondaryCaptureImageStorage,
        SOPClass.MultiframeTrueColorSecondaryCaptureImageStorage);
    private static final Set<Integer> GEOMETRY = Set.of(
        0x00180050,0x00180088,0x00181120,0x00185100,0x00189089,0x00189090,0x00189091,
        0x00200032,0x00200037,0x00200052,0x00201041,0x00209111,0x00209113,0x00209116,
        0x00280002,0x00280004,0x00280006,0x00280008,0x00280010,0x00280011,0x00280030,
        0x00280100,0x00280101,0x00280102,0x00280103,0x00281052,0x00281053,0x00289110,
        0x00640005,0x00640007,0x00640008,0x00640009,
        0x0070030b,0x0070030c,0x300600c6);
    public static Loaded read(Path path) throws IOException, DicomException {
        AttributeList l = new AttributeList();
        l.setDecompressPixelData(false);
        try (DicomInputStream in = new DicomInputStream(path.toFile())) {
            l.read(in);
            String ts = in.getTransferSyntaxToReadDataSet().getUID();
            if (ts == null || ts.isBlank()) throw new IOException("Missing transfer syntax");
            return new Loaded(l, ts);
        }
    }
    public static void write(AttributeList l, Path destination, String syntax) throws Exception {
        l.removeMetaInformationHeaderAttributes();
        l.removeGroupLengthAttributes();
        FileMetaInformation.addFileMetaInformation(l, syntax, "BNEL_CLEANER");
        // Library creates a new zero-filled 128-byte preamble; originals are never reused.
        l.write(destination.toFile(), syntax, true, true);
    }
    public static String hash(Path path) throws IOException {
        MessageDigest md = sha256();
        try (InputStream in = Files.newInputStream(path)) {
            byte[] buf = new byte[1024*1024]; int n;
            while ((n = in.read(buf)) >= 0) {
                if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
                md.update(buf, 0, n);
            }
        }
        return HexFormat.of().formatHex(md.digest());
    }
    public static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    public static String attributeHash(Attribute a) throws Exception {
        if (a == null) return "absent";
        MessageDigest md = sha256();
        try (DicomOutputStream out = new DicomOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), md), null, TransferSyntax.ExplicitVRLittleEndian)) {
            a.write(out);
        }
        return HexFormat.of().formatHex(md.digest());
    }
    public static Map<String,String> geometry(AttributeList l) throws Exception {
        Map<String,String> map = new TreeMap<>();
        for (Row r : rows(l)) {
            int k = Policy.key(r.attribute.getTag());
            if (GEOMETRY.contains(k) && !r.vr.equals("SQ") && !r.vr.equals("UI")) map.put(r.path, attributeHash(r.attribute));
        }
        return map;
    }
    public static Map<String,String> pixels(AttributeList l) throws Exception {
        Map<String,String> map = new TreeMap<>();
        for (int k : new int[]{0x7fe00010,0x7fe00008,0x7fe00009})
            if (l.get(Policy.tag(k)) != null) map.put(Integer.toHexString(k), attributeHash(l.get(Policy.tag(k))));
        return map;
    }
    public static List<Row> rows(AttributeList l) {
        List<Row> rows = new ArrayList<>(); append(l, "", rows); return rows;
    }
    private static void append(AttributeList l, String prefix, List<Row> rows) {
        for (Attribute a : l.values()) {
            String path = prefix + a.getTag().toString();
            String name = l.getDictionary().getNameFromTag(a.getTag());
            if (name == null) name = a.getTag().isPrivate() ? "Private attribute" : "Unknown attribute";
            String vr = new String(a.getVR(), StandardCharsets.US_ASCII), value;
            if (a instanceof SequenceAttribute seq) value = seq.getNumberOfItems() + " item(s)";
            else if (binary(vr)) value = "Binary data — " + (a.getVL() == 0xffffffffL ? "encapsulated" : a.getVL() + " bytes");
            else {
                try { String[] values = a.getStringValues(); value = values == null ? "" : String.join("\\", values); }
                catch (DicomException ex) { value = "Binary data — " + a.getVL() + " bytes"; }
            }
            rows.add(new Row(path, name, vr, a.getVM(), a.getVL(), value, a));
            if (a instanceof SequenceAttribute seq) {
                int index = 0;
                for (Iterator<?> it = seq.iterator(); it.hasNext();) {
                    append(((SequenceItem)it.next()).getAttributeList(), path + "[" + (++index) + "]/", rows);
                }
            }
        }
    }
    public static boolean binary(String vr) { return Set.of("OB","OW","OF","OD","OL","OV","UN").contains(vr); }
    public static Set<String> referencedInstances(AttributeList l) {
        Set<String> result = new HashSet<>();
        for (Row r : rows(l)) if (Policy.key(r.attribute.getTag()) == 0x00081155 && !r.value.isBlank())
            result.addAll(Arrays.asList(r.value.split("\\\\")));
        return result;
    }
    public static Set<String> frameUids(AttributeList l) {
        Set<String> result = new HashSet<>();
        for (Row r : rows(l)) if (r.vr.equals("UI") && r.name.contains("FrameOfReference") && !r.value.isBlank())
            result.addAll(Arrays.asList(r.value.split("\\\\")));
        return result;
    }
    public static boolean isRegistration(String sop) {
        return sop.equals(SOPClass.SpatialRegistrationStorage) || sop.equals(SOPClass.DeformableSpatialRegistrationStorage);
    }
    public static void checkEmbeddedOverlays(AttributeList l) {
        for (int g = 0x6000; g <= 0x60fe; g += 2) {
            if (l.get(new AttributeTag(g,0x0102)) != null && l.get(new AttributeTag(g,0x3000)) == null &&
                    Attribute.getSingleIntegerValueOrDefault(l, new AttributeTag(g,0x0102), 0) > 0)
                throw new IllegalArgumentException("Embedded overlay bits require pixel cleanup before import. Export a copy with overlays removed first.");
        }
    }
    public static void deleteTree(Path dir) throws IOException {
        if (dir == null || !Files.exists(dir)) return;
        try (var stream = Files.walk(dir)) {
            for (Path p : stream.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }
}
