package org.bnel.dicom;

import com.pixelmed.dicom.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

/** "Keep Siemens diffusion/fMRI tags": only the vetted list survives, end to end. Run from EngineTest. */
final class SiemensTagTest {
    static AttributeTag t(int g, int e) { return new AttributeTag(g, e); }
    static Path write(Path dir) throws Exception {
        AttributeList l = new AttributeList();
        Policy.put(l, 0x00080016, SOPClass.MRImageStorage); Policy.put(l, 0x00080018, "2.25.5551234567");
        Policy.put(l, 0x0020000d, "2.25.5561234567"); Policy.put(l, 0x0020000e, "2.25.5571234567");
        Policy.put(l, 0x00200052, "2.25.5581234567"); Policy.put(l, 0x00080060, "MR");
        Policy.put(l, 0x00100010, "Smith^Mary"); Policy.put(l, 0x00100020, "705693123"); Policy.put(l, 0x00080020, "20240317");
        Policy.put(l, 0x0008103e, "ep2d_diff_mddw_20"); Policy.put(l, 0x00080008, "ORIGINAL\\PRIMARY\\DIFFUSION\\NONE");
        for (int[] kv : new int[][]{{0x00280002, 1}, {0x00280010, 4}, {0x00280011, 4}, {0x00280100, 16}, {0x00280101, 12}, {0x00280102, 11}, {0x00280103, 0}})
            Policy.put(l, kv[0], String.valueOf(kv[1]));
        Policy.put(l, 0x00280004, "MONOCHROME2");
        // Siemens MR header block (creator at 0019,0010 -> data at 0019,10xx)
        LongStringAttribute mrCreator = new LongStringAttribute(t(0x0019, 0x0010)); mrCreator.addValue("SIEMENS MR HEADER"); l.put(mrCreator);
        IntegerStringAttribute bval = new IntegerStringAttribute(t(0x0019, 0x100c)); bval.addValue(1000); l.put(bval);
        FloatDoubleAttribute grad = new FloatDoubleAttribute(t(0x0019, 0x100e)); grad.addValue(0.5); grad.addValue(0.5); grad.addValue(0.707); l.put(grad);
        FloatDoubleAttribute times = new FloatDoubleAttribute(t(0x0019, 0x1029)); times.addValue(0.0); times.addValue(52.5); l.put(times);
        LongStringAttribute notVetted = new LongStringAttribute(t(0x0019, 0x1013)); notVetted.addValue("not on the list"); l.put(notVetted);
        // Siemens CSA header block: image header kept, series header (protocol text) never kept
        LongStringAttribute csaCreator = new LongStringAttribute(t(0x0029, 0x0010)); csaCreator.addValue("SIEMENS CSA HEADER"); l.put(csaCreator);
        OtherByteAttribute csaImage = new OtherByteAttribute(t(0x0029, 0x1010));
        csaImage.setValues("SV10\u0004\u0003\u0002\u0001B_value\u0000\u0000ImaComments Smith follow-up\u0000\u0000".getBytes(StandardCharsets.ISO_8859_1)); l.put(csaImage);
        OtherByteAttribute csaSeries = new OtherByteAttribute(t(0x0029, 0x1020));
        csaSeries.setValues("SV10 tProtocolName = \"Smith study\"".getBytes(StandardCharsets.ISO_8859_1)); l.put(csaSeries);
        // A different creator in the same group must not be kept
        LongStringAttribute other = new LongStringAttribute(t(0x0019, 0x0011)); other.addValue("OTHER VENDOR"); l.put(other);
        IntegerStringAttribute otherVal = new IntegerStringAttribute(t(0x0019, 0x110c)); otherVal.addValue(42); l.put(otherVal);
        OtherWordAttribute px = new OtherWordAttribute(TagFromName.PixelData); px.setValues(new short[16]); l.put(px);
        Path f = dir.resolve("dwi.dcm");
        DicomIO.write(l, f, TransferSyntax.ExplicitVRLittleEndian);
        return f;
    }
    static void run(Path root) throws Exception {
        Path dir = Files.createDirectories(root.resolve("siemens"));
        write(dir);
        var scan = Cleaner.scan(List.of(dir), (s, d, n) -> {}, () -> false);
        for (boolean keep : new boolean[]{false, true}) {
            for (boolean minimal : new boolean[]{false, true}) {
                Policy.Options o = new Policy.Options("Research^001", "R001", Policy.DateMode.FIXED, LocalDate.of(2000, 1, 1), minimal, Map.of(), keep);
                try (Cleaner.Batch b = Cleaner.prepare(scan.instances(), o, "", (s, d, n) -> {}, () -> false)) {
                    String mode = " (keep=" + keep + ", minimal=" + minimal + ")";
                    EngineTest.check(b.outputs.size() == 1 && b.skipped.isEmpty(), "siemens: file cleaned" + mode + " " + b.skipped);
                    AttributeList out = DicomIO.read(b.outputs.get(0).file).list();
                    boolean bv = out.get(t(0x0019, 0x100c)) != null, gd = out.get(t(0x0019, 0x100e)) != null,
                        st = out.get(t(0x0019, 0x1029)) != null, csa = out.get(t(0x0029, 0x1010)) != null;
                    EngineTest.check(keep ? bv && gd && st && csa : !bv && !gd && !st && !csa, "siemens: vetted tags follow the option" + mode);
                    EngineTest.check(out.get(t(0x0019, 0x1013)) == null, "siemens: non-vetted Siemens tag removed" + mode);
                    EngineTest.check(out.get(t(0x0029, 0x1020)) == null, "siemens: CSA series header never kept" + mode);
                    EngineTest.check(out.get(t(0x0019, 0x110c)) == null && out.get(t(0x0019, 0x0011)) == null, "siemens: other creator's block removed" + mode);
                    if (keep) {
                        EngineTest.check(out.get(t(0x0019, 0x100c)).getSingleIntegerValueOrDefault(0) == 1000, "siemens: b-value intact" + mode);
                        EngineTest.check(b.identifierFindings.stream().anyMatch(f -> f.location().contains("(0029,1010)") && f.kind().contains("name")),
                            "siemens: identifier check scans CSA text" + mode + " " + b.identifierFindings);
                    }
                }
            }
        }
    }
}
