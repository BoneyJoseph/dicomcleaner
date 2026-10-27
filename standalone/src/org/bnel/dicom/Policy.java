package org.bnel.dicom;

import com.pixelmed.dicom.*;
import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.*;
import java.util.*;

/** Local, conservative metadata policy for the explicitly supported image/REG SOP classes. */
public final class Policy {
    public static final String VERSION = "0.1.0";
    public enum DateMode { SHIFT, FIXED }
    public record Options(String patientName, String patientId, DateMode dateMode, LocalDate targetDate, boolean minimal) {
        public Options(String name,String id,DateMode mode,LocalDate date){this(name,id,mode,date,false);}
        public Options {
            patientName = patientName == null ? "" : patientName.strip();
            patientId = patientId == null ? "" : patientId.strip();
            if (!patientName.matches("[A-Za-z0-9 _^.-]{1,64}") ||
                    !patientId.matches("[A-Za-z0-9_.-]{1,64}"))
                throw new IllegalArgumentException("Use a research name (up to 64 ASCII letters, numbers, spaces, ^ . _ -) and ID (letters, numbers, . _ -).");
            Objects.requireNonNull(dateMode);
            Objects.requireNonNull(targetDate);
            if (targetDate.getYear() < 1 || targetDate.getYear() > 9999)
                throw new IllegalArgumentException("Choose a date between years 0001 and 9999.");
        }
    }
    private static final DateTimeFormatter DA = DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);
    private static final Map<Integer, String> RULES = loadRules();
    private static final Set<String> FREE_TEXT = Set.of("PN", "LO", "SH", "LT", "ST", "UT", "UC", "UR", "AE", "AS");
    // A CS is not automatically safe: Image Type and vendor-defined CS values can contain PHI.
    private static final Map<Integer,Set<String>> CONTROLLED = controlledValues();
    private static Map<Integer,Set<String>> controlledValues() {
        Map<Integer,Set<String>> m = new HashMap<>();
        m.put(0x00080060,Set.of("CT","MR","REG","OT"));
        m.put(0x00280004,Set.of("MONOCHROME1","MONOCHROME2","RGB","YBR_FULL","YBR_FULL_422","YBR_PARTIAL_422","YBR_PARTIAL_420","YBR_ICT","YBR_RCT","PALETTE COLOR"));
        m.put(0x00185100,Set.of("HFP","HFS","HFDR","HFDL","FFP","FFS","FFDR","FFDL","LFP","LFS","RFP","RFS","AFDR","AFDL","PFDR","PFDL"));
        m.put(0x00180020,Set.of("SE","IR","GR","EP","RM"));
        m.put(0x00180021,Set.of("SK","MTC","SS","TRSS","SP","MP","OSP","NONE"));
        m.put(0x00180022,Set.of("PER","RG","CG","PPG","FC","PFF","PFP","SP","FS"));
        m.put(0x00180023,Set.of("2D","3D"));
        m.put(0x00181312,Set.of("ROW","COL","OTHER"));
        m.put(0x00189075,Set.of("NONE","ISOTROPIC","DIRECTIONAL","BMATRIX"));
        m.put(0x00189076,Set.of("FREQUENCY","PHASE","SLICE_SELECT","NONE"));
        m.put(0x00089205,Set.of("MONOCHROME","COLOR"));
        m.put(0x00089206,Set.of("VOLUME","SAMPLED","DISTORTED","MIXED"));
        m.put(0x00089207,Set.of("NONE","MAX_IP","MIN_IP","MEAN_IP","MIXED"));
        m.put(0x00089208,Set.of("UNKNOWN","T1","T2","T2_STAR","PROTON_DENSITY","DIFFUSION","PERFUSION","ANGIO","FLOW_ENCODED","STIR","FLUID_ATTENUATED","TAGGING","MIXED"));
        m.put(0x00280009,Set.of()); // AT in conforming input; never accept arbitrary textual substitute.
        m.put(0x00280034,Set.of());
        m.put(0x00280051,Set.of("DECY","ATTN","SCAT","DTIM","RAN","RADL","DCAL","NORM","NONE"));
        m.put(0x00280064,Set.of("GEOMETRY","FIDUCIAL"));
        m.put(0x00282110,Set.of("00","01"));
        m.put(0x00282114,Set.of("ISO_10918_1","ISO_14495_1","ISO_15444_1","ISO_13818_2","ISO_14496_10","ISO_23008_2"));
        m.put(0x00281056,Set.of("LINEAR","LINEAR_EXACT","SIGMOID"));
        m.put(0x0070030c,Set.of("RIGID","RIGID_SCALE","AFFINE"));
        m.put(0x00200060,Set.of("R","L"));m.put(0x00200062,Set.of("R","L","U","B"));
        m.put(0x00200020,Set.of("A","P","R","L","H","F","AP","PA","RL","LR","HF","FH"));
        m.put(0x20500020,Set.of("IDENTITY","INVERSE"));
        return Collections.unmodifiableMap(m);
    }
    private final Options options;
    private final long shiftDays;
    // Deliberately per-job: no global/static linkage and no identifier mapping written to disk.
    private final Map<String, String> uids = new HashMap<>();
    private final Map<String, Integer> counts = new TreeMap<>();
    private final IdentifierPolicy identifierPolicy;
    private static final Set<Integer> ESSENTIAL = essentialTags();
    private static Set<Integer> essentialTags(){
        Set<Integer> keys=new HashSet<>();
        // Explicit classic CT/MR and REG allowlist. Enhanced/SC require the broader policy.
        String tags="00080005 00080008 00080016 00080018 00080020 00080021 00080022 00080023 0008002A 00080030 00080031 00080032 00080033 00080050 00080060 00080064 00080070 00080090 00080100 00080102 00080103 00080104 0008103E 00081110 00081115 00081140 00081150 00081155 00081160 00081199 00082111 00082112 00089215 " +
            "00100010 00100020 00100030 00100040 00180010 00180015 00180020 00180021 00180022 00180023 00180024 00180025 00180050 00180060 00180080 00180081 00180082 00180083 00180084 00180085 00180086 00180087 00180088 00180089 00180090 00180091 00180093 00180094 00180095 00181020 00181030 00181060 00181062 00181063 00181065 00181066 00181080 00181081 00181082 00181083 00181084 00181085 00181086 00181088 00181090 00181094 00181100 00181110 00181111 00181120 00181130 00181140 00181150 00181151 00181152 00181160 00181170 00181190 00181200 00181201 00181210 00181250 00181251 00181310 00181312 00181314 00181315 00181316 00181318 00181320 00185100 00189075 00189076 00189087 00189089 " +
            "0020000D 0020000E 00200010 00200011 00200012 00200013 00200020 00200032 00200037 00200052 00200060 00200062 00200100 00200105 00200110 00201040 00201041 " +
            "00280002 00280004 00280006 00280008 00280009 00280010 00280011 00280030 00280031 00280034 00280051 00280064 00280100 00280101 00280102 00280103 00280106 00280107 00280120 00280121 00280301 00280302 00281050 00281051 00281052 00281053 00281054 00281055 00281056 00281101 00281102 00281103 00281201 00281202 00281203 00281221 00281222 00281223 00282110 00282112 00282114 00283000 00283002 00283003 00283004 00283006 00283010 " +
            "00640002 00640003 00640005 00640007 00640008 00640009 0064000F 00640010 00700080 00700081 00700082 00700084 00700308 00700309 0070030A 0070030B 0070030C 0070030D 0070030F 00700310 20500020 300600C6 7FE00001 7FE00002 7FE00008 7FE00009 7FE00010";
        for(String tag:tags.split(" +"))keys.add((int)Long.parseLong(tag,16));return Set.copyOf(keys);
    }

    public Policy(Options options, LocalDate earliestStudy) {
        this(options,earliestStudy,new IdentifierPolicy());
    }
    Policy(Options options, LocalDate earliestStudy, IdentifierPolicy identifierPolicy) {
        this.options = options;
        this.identifierPolicy=identifierPolicy.fresh();
        if (options.dateMode == DateMode.SHIFT && earliestStudy == null)
            throw new IllegalArgumentException("No complete Study Date was found. Use fixed-date replacement.");
        this.shiftDays = earliestStudy == null ? 0 : java.time.temporal.ChronoUnit.DAYS.between(earliestStudy, options.targetDate);
        if (options.dateMode == DateMode.SHIFT && shiftDays == 0)
            throw new IllegalArgumentException("Choose a different target date; a zero-day shift leaves dates identifiable.");
    }
    public Map<String, Integer> counts() { return Collections.unmodifiableMap(counts); }
    private void count(String operation) { counts.merge(operation, 1, Integer::sum); }
    public static int key(AttributeTag t) { return (t.getGroup() << 16) | t.getElement(); }
    public static AttributeTag tag(int key) { return new AttributeTag(key >>> 16, key & 0xffff); }
    public static String value(AttributeList l, int key) { return Attribute.getSingleStringValueOrEmptyString(l, tag(key)); }
    public static void put(AttributeList l, int key, String value) throws DicomException {
        Attribute a = AttributeFactory.newAttribute(tag(key));
        if (!value.isEmpty()) a.addValue(value);
        l.put(a);
    }
    public static LocalDate parseDate(String value) {
        if (!value.matches("[0-9]{8}")) throw new IllegalArgumentException("A complete valid date is required (YYYYMMDD).");
        return LocalDate.parse(value, DA);
    }
    private static Map<Integer, String> loadRules() {
        Map<Integer, String> m = new HashMap<>();
        try (InputStream in = Policy.class.getResourceAsStream("/confidentiality.tsv")) {
            if (in == null) throw new IOException("Missing metadata policy");
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                if (line.startsWith("#") || line.isBlank()) continue;
                String[] cols = line.split("\\t");
                // Repeating curves/overlays and private patterns are handled explicitly below.
                if (!cols[0].toLowerCase(Locale.ROOT).contains("x"))
                    m.put((int)Long.parseLong(cols[0], 16), cols[1]);
            }
            if (m.size() < 600) throw new IOException("Incomplete metadata policy");
            return Collections.unmodifiableMap(m);
        } catch (IOException e) { throw new ExceptionInInitializerError(e); }
    }
    public String mapUid(String old) {
        if (old == null || old.isBlank()) throw new IllegalArgumentException("Missing required UID.");
        if (!old.matches("[0-9]+(\\.[0-9]+)+") || old.length() > 64)
            throw new IllegalArgumentException("Invalid DICOM UID. No files were exported.");
        return uids.computeIfAbsent(old, k -> {
            UUID id = UUID.randomUUID();
            byte[] b = java.nio.ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
            return "2.25." + new BigInteger(1, b);
        });
    }
    public void clean(AttributeList list) throws DicomException {
        recurse(list);
        put(list, 0x00100010, options.patientName);
        put(list, 0x00100020, options.patientId);
        put(list, 0x00100030, "");
        put(list, 0x00100040, "");
        for(int k:new int[]{0x00080050,0x00080090,0x00200010})if(list.get(tag(k))==null)put(list,k,"");
        // Pixel review is still outstanding at this stage. Set YES only during approved export.
        put(list, 0x00120062, "NO");
        put(list, 0x00120063, "BNEL " + VERSION + " metadata; " + options.dateMode.name());
        put(list, 0x00280303, options.dateMode == DateMode.SHIFT ? "MODIFIED" : "REMOVED");
        list.insertSuitableSpecificCharacterSetForAllStringValues();
    }
    private void recurse(AttributeList list) throws DicomException {
        for (Attribute a : new ArrayList<Attribute>(list.values())) {
            AttributeTag t = a.getTag();
            int k = key(t), group = t.getGroup();
            String vr = new String(a.getVR(), StandardCharsets.US_ASCII);
            String action = RULES.getOrDefault(k, "");
            if(options.minimal&&!ESSENTIAL.contains(k)) {list.remove(t);count("removed_nonessential_metadata");continue;}
            if (t.isPrivate() || group == 0 || group == 2 || group == 4 || group == 0x0012 ||
                    (group & 0xff00) == 0x5000 || (group & 0xff00) == 0x6000 ||
                    group == 0x0400 || group == 0x4ffe || group == 0xfffa ||
                    t.getElement() == 0 || k == 0xfffcfffc || vr.equals("UN") ||
                    k == 0x00880200 || k == 0x0040a730 || k == 0x00700001 || k == 0x00080201 ||
                    list.getDictionary().getNameFromTag(t) == null) {
                list.remove(t); count("removed_private_unknown_history_or_overlay"); continue;
            }
            if (group == 0x0010) {
                if (k == 0x00100010) a.setValue(options.patientName);
                else if (k == 0x00100020) a.setValue(options.patientId);
                else if (k == 0x00100030 || k == 0x00100040) a.removeValues();
                else if(!identifierPolicy.apply(list,a))list.remove(t);
                count("replaced_or_removed_patient_attribute"); continue;
            }
            if (a instanceof SequenceAttribute seq) {
                if (action.equals("X") || action.equals("D") || action.equals("X/D")) {
                    // Required D-sequences in E.1-1 belong to unsupported SR/RT/document IODs.
                    list.remove(t); count("removed_identifying_sequence");
                } else if (action.equals("X/Z") || action.equals("Z")) {
                    list.put(new SequenceAttribute(t)); count("emptied_identifying_sequence");
                } else {
                    for (Iterator<?> it = seq.iterator(); it.hasNext();) recurse(((SequenceItem)it.next()).getAttributeList());
                }
                continue;
            }
            // Disallow an explicit VR that disguises a textual identifier as another type.
            byte[] dictionaryVR = list.getDictionary().getValueRepresentationFromTag(t);
            String expected = dictionaryVR == null ? "" : new String(dictionaryVR,StandardCharsets.US_ASCII).toUpperCase(Locale.ROOT);
            if (!expected.equals(vr) && !Set.of("OX","XS","XO","US or SS","OB or OW").contains(expected))
                throw new IllegalArgumentException("A metadata element uses an unexpected value type. Correct the source before cleaning.");
            if (DicomIO.binary(vr) && !Set.of(0x7fe00010,0x7fe00008,0x7fe00009,0x00640009,
                    0x00281201,0x00281202,0x00281203,0x00281221,0x00281222,0x00281223,0x00283006,
                    0x7fe00001,0x7fe00002).contains(k)) {
                list.remove(t); count("removed_opaque_payload"); continue;
            }
            if (vr.equals("DA") || vr.equals("DT") || vr.equals("TM")) {
                if (options.dateMode == DateMode.FIXED && action.equals("X")) {
                    list.remove(t); count("removed_temporal_attribute"); continue;
                }
                if (options.dateMode == DateMode.FIXED && action.contains("Z") && !action.contains("D")) {
                    a.removeValues(); count("emptied_temporal_attribute"); continue;
                }
                changeDate(a, vr); count("changed_or_validated_temporal_attribute"); continue;
            }
            if(identifierPolicy.apply(list,a)){count("handled_identifier_by_operator_policy");continue;}
            if (action.equals("X")) { list.remove(t); count("removed_profile_attribute"); continue; }
            if (vr.equals("UI")) {
                if (UniqueIdentifierAttribute.isTransient(t)) {
                    String[] old = a.getStringValues();
                    a.removeValues();
                    if (old != null) for (String uid : old) if (!uid.isBlank()) a.addValue(mapUid(uid));
                    count("remapped_uid_attribute");
                }
                continue;
            }
            if (action.contains("D")) {
                dummy(a, vr); count("replaced_profile_attribute"); continue;
            }
            if (action.contains("Z")) { a.removeValues(); count("emptied_profile_attribute"); continue; }
            if (vr.equals("CS")) {
                if (k == 0x00080005) { list.remove(t); continue; } // Regenerated after text cleaning.
                if (k == 0x00700080) { a.setValue("RESEARCH"); continue; }
                Set<String> allowed = CONTROLLED.getOrDefault(k,Set.of());
                boolean imageType = k == 0x00080008 || k == 0x00089007;
                if (imageType) allowed = Set.of("ORIGINAL","DERIVED","PRIMARY","SECONDARY","AXIAL","LOCALIZER","OTHER","M","P","R","I","MAGNITUDE","PHASE","REAL","IMAGINARY","NONE","MIXED");
                String[] values = a.getStringValues(); a.removeValues();
                if (values != null) for (String value : values) {
                    if (allowed.contains(value)) a.addValue(value);
                    else { if (imageType) a.addValue("OTHER"); count("scrubbed_unrecognized_coded_text"); }
                }
                continue;
            }
            if (vr.equals("IS") || vr.equals("DS")) {
                String[] values=a.getStringValues();
                if (values!=null) for(String value:values) if(!value.isBlank() &&
                        !(vr.equals("IS") ? value.strip().matches("[+-]?[0-9]{1,12}") : value.strip().matches("[+-]?([0-9]+(\\.[0-9]*)?|\\.[0-9]+)([Ee][+-]?[0-9]+)?")))
                    throw new IllegalArgumentException("A numeric metadata field contains unexpected text. Correct the source before cleaning.");
            }
            if (FREE_TEXT.contains(vr)) {
                // Only constrained standard code tokens survive; never preserve arbitrary long code text.
                String scheme=value(list,0x00080102), code=a.getSingleStringValueOrEmptyString();
                if (k==0x00080102 && Set.of("DCM","SCT","SRT","LN","UCUM").contains(code)) continue;
                if (k==0x00080100 && ((scheme.equals("DCM") && code.matches("[0-9]{6}")) ||
                        (scheme.equals("SCT") && code.matches("[0-9]{6,18}")) ||
                        (scheme.equals("SRT") && code.matches("[A-Z0-9]-[A-Z0-9]{4,6}")) ||
                        (scheme.equals("LN") && code.matches("[0-9]{1,8}-[0-9]")) ||
                        (scheme.equals("UCUM") && Set.of("mm","cm","m","s","ms","Hz","1","HU","rad","deg").contains(code)))) continue;
                if (k == 0x00281054 && Set.of("HU", "US", "OD").contains(a.getSingleStringValueOrEmptyString())) continue;
                if (k == 0x00080104) a.setValue("REDACTED"); // code identity remains in Code Value
                else if (k == 0x00700080) a.setValue("RESEARCH"); // registration Content Label
                else a.removeValues();
                count("scrubbed_free_text");
            }
        }
    }
    private static void dummy(Attribute a, String vr) throws DicomException {
        a.removeValues();
        if (FREE_TEXT.contains(vr) || vr.equals("CS")) a.addValue(vr.equals("AS") ? "000Y" : "REDACTED");
        else if (Set.of("IS", "DS", "US", "SS", "UL", "SL", "FL", "FD").contains(vr)) a.addValue(0);
        // Unsupported opaque payloads are removed by the caller's SOP-class restrictions/policy.
    }
    private void changeDate(Attribute a, String vr) throws DicomException {
        String[] values = a.getStringValues();
        a.removeValues();
        if (values == null) return;
        for (String s : values) {
            if (s.isBlank()) continue;
            try {
                if (options.dateMode == DateMode.FIXED) {
                    a.addValue(vr.equals("TM") ? "000000" : options.targetDate.format(DA) + (vr.equals("DT") ? "000000" : ""));
                } else if (vr.equals("TM")) {
                    validateTime(s); a.addValue(s); // Preserve dynamic imaging timing, including sub-seconds.
                } else {
                    if ((vr.equals("DA") && s.length() != 8) || s.length() < 8) throw new IllegalArgumentException();
                    LocalDate shifted = parseDate(s.substring(0, 8)).plusDays(shiftDays);
                    if (shifted.getYear() < 1 || shifted.getYear() > 9999) throw new IllegalArgumentException();
                    String suffix = s.substring(8);
                    if (vr.equals("DT")) validateDateTimeSuffix(suffix);
                    a.addValue(shifted.format(DA) + suffix);
                }
            } catch (RuntimeException ex) {
                throw new IllegalArgumentException("A date/time is invalid, incomplete, or outside range. Use fixed-date replacement or correct the source.");
            }
        }
    }
    private static void validateTime(String s) {
        if (!s.matches("[0-9]{2}([0-9]{2}([0-9]{2}(\\.[0-9]{1,6})?)?)?")) throw new IllegalArgumentException();
        if (Integer.parseInt(s.substring(0,2)) > 23 || (s.length() >= 4 && Integer.parseInt(s.substring(2,4)) > 59)
                || (s.length() >= 6 && Integer.parseInt(s.substring(4,6)) > 60)) throw new IllegalArgumentException();
    }
    private static void validateDateTimeSuffix(String suffix) {
        String time = suffix;
        int sign = Math.max(suffix.indexOf('+'), suffix.indexOf('-'));
        if (sign >= 0) {
            String tz = suffix.substring(sign);
            if (!tz.matches("[+-][0-9]{4}")) throw new IllegalArgumentException();
            int h = Integer.parseInt(tz.substring(1,3)), m = Integer.parseInt(tz.substring(3));
            if (m > 59 || h > 14 || (h == 14 && m != 0)) throw new IllegalArgumentException();
            time = suffix.substring(0, sign);
        }
        if (!time.isEmpty()) validateTime(time);
    }
}
