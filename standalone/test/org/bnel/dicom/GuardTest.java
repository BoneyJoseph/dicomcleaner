package org.bnel.dicom;

import com.pixelmed.dicom.*;
import java.time.LocalDate;
import java.util.*;

/** Identifier-check rules: catches embedded numbers/dates/names, avoids known false alarms. Run from EngineTest. */
final class GuardTest {
    private static AttributeList list(int[] tags, String... values) throws Exception {
        AttributeList l = new AttributeList();
        for (int i = 0; i < tags.length; i++) Policy.put(l, tags[i], values[i]);
        return l;
    }
    private static Set<String> kinds(List<ResidualGuard.Finding> findings, String locationPart) {
        Set<String> out = new HashSet<>();
        for (ResidualGuard.Finding f : findings) if (f.location().contains(locationPart)) out.add(f.kind());
        return out;
    }
    static void run() throws Exception {
        AttributeList source = list(new int[]{0x00100010, 0x00100020, 0x00080020, 0x00080080, 0x00080050, 0x00200010},
            "Smith^Mary", "705-693-123", "20260607", "Lakeside General Hospital", "ACC-88123", "4721");

        // 1. Identifiers hidden inside retained text are found.
        ResidualGuard g = new ResidualGuard();
        Set<String> generated = new HashSet<>();
        g.generated(generated);
        g.collect(source);
        AttributeList cleaned = list(new int[]{0x0008103e, 0x00081030, 0x00181030, 0x00180024, 0x00080008, 0x00204000},
            "T1 MPRAGE smith", "xh$jknl7056931236780nmjko", "scan on 07-06-2026", "abc202606076799",
            "ORIGINAL\\PRIMARY", "Lakeside follow-up");
        g.capture("MR · test", cleaned);
        List<ResidualGuard.Finding> f = g.findings();
        EngineTest.check(kinds(f, "SeriesDescription").contains("contains a name from the source files"), "guard: surname in Series Description");
        EngineTest.check(kinds(f, "StudyDescription").contains("contains a number from a source identifier"), "guard: 705-693-123 inside junk text");
        EngineTest.check(kinds(f, "ProtocolName").contains("contains a source date"), "guard: DD-MM-YYYY date");
        EngineTest.check(kinds(f, "SequenceName").contains("contains a source date"), "guard: YYYYMMDD date inside digits");
        EngineTest.check(kinds(f, "ImageComments").contains("contains an ID or institution text from the source files"), "guard: institution word");
        EngineTest.check(kinds(f, "ImageType").isEmpty(), "guard: MARY inside PRIMARY is not a hit");

        // 2. Known false alarms are avoided: app-generated UIDs, standard UIDs, stop words, replacement date.
        ResidualGuard quiet = new ResidualGuard();
        Set<String> live = new HashSet<>();
        quiet.generated(live);
        quiet.collect(source);
        String randomUid = "2.25.47217056931231234567890123456789";   // contains 4721 and 705693123
        live.add(randomUid);
        quiet.skipDate(LocalDate.of(2026, 6, 7));                      // operator chose the source date as synthetic date
        AttributeList clean2 = list(new int[]{0x00080018, 0x00080016, 0x00080020, 0x0008103e},
            randomUid, SOPClass.MRImageStorage, "20260607", "General T2 axial 20260607");
        quiet.capture("MR · quiet", clean2);
        EngineTest.check(quiet.findings().isEmpty(), "guard: no false alarms for generated UIDs, standard UIDs, stop words, replacement date: " + quiet.findings());

        // 3. Replacement identity check and aggregation across files.
        EngineTest.check(quiet.replacementLooksIdentifying("SUBJ-705693123"), "guard: replacement ID containing source number");
        EngineTest.check(!quiet.replacementLooksIdentifying("Research^001"), "guard: neutral replacement accepted");
        ResidualGuard many = new ResidualGuard();
        many.generated(Set.of());
        many.collect(source);
        for (int i = 0; i < 5; i++) many.capture("MR · many", list(new int[]{0x0008103e}, "Mary Smith T1"));
        List<ResidualGuard.Finding> agg = many.findings();
        EngineTest.check(agg.size() == 1 && agg.get(0).files() == 5, "guard: findings aggregated per series/location: " + agg);
    }
}
