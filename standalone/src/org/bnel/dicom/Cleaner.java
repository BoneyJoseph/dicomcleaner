package org.bnel.dicom;

import com.pixelmed.dicom.*;
import com.pixelmed.display.*;
import java.awt.Shape;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.zip.*;

/** No networking, source modifications, persistent identifier mappings, or PHI-bearing logs. */
public final class Cleaner {
    @FunctionalInterface public interface Progress { void update(String stage, int done, int total); }
    public record Instance(Path source, Path importRoot, Path relativePath, String sha256, String sop, String sopClass, String study,
                           String series, String patientKey, String patientName, String patientId,
                           String modality, int seriesNumber, int instanceNumber, int frames, String description,
                           String problem, LocalDate studyDate, String burnedInHint) {
        public boolean supported() { return problem.isEmpty(); }
        /** Non-empty when the file is likely to carry burned-in text (shown as a badge in the sources tree). */
        public boolean likelyBurnedIn() { return !burnedInHint.isEmpty(); }
        @Override public String toString() { return String.format("%04d", instanceNumber) + "  " + source.getFileName(); }
    }
    public record Scan(List<Instance> instances, int skipped, List<String> issues) {}
    public record ExportOptions(boolean zip, boolean dicomdir, boolean preserveHierarchy, boolean retainNames) {}
    public static final class Output {
        public final Instance input;
        public final Path file;
        public String sha256;
        public final String relativeName;
        public boolean reviewed, redacted;
        public Output(Instance input, Path file, String sha256, String relativeName) {
            this.input = input; this.file = file; this.sha256 = sha256; this.relativeName = relativeName;
        }
    }
    public record Referrer(Path source,String tagPath) {}
    public record ReferenceDetail(String originalUid,String cleanedUid,List<Path> knownTargets,List<Referrer> referringFiles,String status) {}
    public static final class Batch implements AutoCloseable {
        public final Path staging;
        public final List<Output> outputs = new ArrayList<>();
        public final Policy.Options options;
        public final List<String> skipped=new ArrayList<>();
        public final List<String> warnings=new ArrayList<>();
        /** Non-blocking identifier-check findings (see ResidualGuard). */
        public final List<ResidualGuard.Finding> identifierFindings=new ArrayList<>();
        public final Map<String,Integer> operations;
        public int unresolvedReferences;
        public final List<ReferenceDetail> referenceDetails=new ArrayList<>();
        public boolean requiresReclean;
        Batch(Path staging, Policy.Options options, String reason, Map<String,Integer> operations) {
            this.staging = staging; this.options = options;  this.operations = operations;
        }
        public boolean exportReady() {
            return !requiresReclean && !outputs.isEmpty();
        }
        @Override public void close() throws IOException { DicomIO.deleteTree(staging); }
    }
    private static void cancelled(BooleanSupplier cancel) {
        if (cancel.getAsBoolean() || Thread.currentThread().isInterrupted()) throw new CancellationException();
    }
    public static Scan scan(Collection<Path> roots, Progress progress, BooleanSupplier cancel) throws Exception {
        TreeSet<Path> paths = new TreeSet<>(); Map<Path,Path> importRoots = new HashMap<>();
        for (Path root : roots) {
            cancelled(cancel);
            if (Files.isSymbolicLink(root)) throw new IOException("Symbolic links are not imported. Choose the actual source folder.");
            Path canonical = root.toRealPath();
            if (Files.isDirectory(canonical)) {
                try (var walk = Files.walk(canonical)) {
                    for (Path p : walk.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)).toList()) {
                        cancelled(cancel); paths.add(p); importRoots.putIfAbsent(p,canonical);
                    }
                }
            } else { paths.add(canonical); importRoots.putIfAbsent(canonical,canonical.getParent()); }
        }
        List<Instance> instances = new ArrayList<>(); List<String> issues = new ArrayList<>();
        int done = 0, skipped = 0;
        for (Path path : paths) {
            cancelled(cancel); progress.update("Importing", ++done, paths.size());
            if (path.getFileName().toString().equalsIgnoreCase("DICOMDIR")) { skipped++; continue; }
            if (!DicomFileUtilities.isDicomOrAcrNemaFile(path.toFile())) {
                if (path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".dcm"))
                    issues.add("Unreadable DICOM: " + path.getFileName());
                else skipped++;
                continue;
            }
            try {
                String digest = DicomIO.hash(path);
                DicomIO.Loaded loaded = DicomIO.read(path); AttributeList l = loaded.list();
                String sopClass = Policy.value(l, 0x00080016), problem = "";
                if (sopClass.equals(SOPClass.MediaStorageDirectoryStorage)) { skipped++; continue; }
                if (!DicomIO.SUPPORTED.contains(sopClass)) problem = "Unsupported SOP class: inspect only";
                for (int key : new int[]{0x00080018,0x0020000d,0x0020000e})
                    if (Policy.value(l,key).isBlank()) problem = "Missing required DICOM UID";
                try { DicomIO.checkEmbeddedOverlays(l); }
                catch (IllegalArgumentException e) { problem = e.getMessage(); }
                LocalDate date = null;
                try { date = Policy.parseDate(Policy.value(l,0x00080020)); } catch (RuntimeException ignored) {}
                String id = Policy.value(l,0x00100020), name = Policy.value(l,0x00100010);
                String patientKey = id + "\u0000" + Policy.value(l,0x00100021) + "\u0000" + name;
                instances.add(new Instance(path,importRoots.get(path),importRoots.get(path).relativize(path),digest,Policy.value(l,0x00080018),sopClass,
                    Policy.value(l,0x0020000d),Policy.value(l,0x0020000e),patientKey,name,id,
                    Policy.value(l,0x00080060),Attribute.getSingleIntegerValueOrDefault(l,TagFromName.SeriesNumber,0),Attribute.getSingleIntegerValueOrDefault(l,TagFromName.InstanceNumber,0),
                    Math.max(1,Attribute.getSingleIntegerValueOrDefault(l,TagFromName.NumberOfFrames,1)),
                    Policy.value(l,0x0008103e),problem,date,burnedInHint(l,sopClass)));
            } catch (Exception e) {
                if (e instanceof CancellationException) throw e;
                // Show names locally to let the operator locate failures, never include these in reports.
                issues.add("Could not parse: " + path.getFileName());
            }
        }
        instances.sort(Comparator.comparing(Instance::patientKey).thenComparing(Instance::study).thenComparingInt(Instance::seriesNumber).thenComparing(Instance::series).thenComparingInt(Instance::instanceNumber).thenComparing(Instance::source));
        return new Scan(List.copyOf(instances),skipped,List.copyOf(issues));
    }
    public static Batch prepare(List<Instance> inputs, Policy.Options options, String reason,
                                Progress progress, BooleanSupplier cancel) throws Exception {
        return prepare(inputs,options,reason,new IdentifierPolicy(),Map.of(),progress,cancel);
    }
    static Batch prepare(List<Instance> inputs, Policy.Options options, String reason,IdentifierPolicy identifiers,
                         Map<String,ScanLabels.Labels> labels,Progress progress,BooleanSupplier cancel)throws Exception {
        return prepare(inputs,options,reason,identifiers,labels,inputs,progress,cancel);
    }
    static Batch prepare(List<Instance> inputs,Policy.Options options,String reason,IdentifierPolicy identifiers,
                         Map<String,ScanLabels.Labels> labels,List<Instance> imported,Progress progress,BooleanSupplier cancel)throws Exception {
        if(inputs.isEmpty())throw new IllegalArgumentException("Select folders or images to clean.");
        Policy policy=new Policy(options,null,identifiers);
        ResidualGuard guard=new ResidualGuard();guard.generated(policy.generatedUids());
        Path stage=Files.createTempDirectory("bnel-cleaner-");
        Batch batch=new Batch(stage,options,"",policy.counts());
        Set<String> seen=new HashSet<>();Map<String,Integer> seriesNumbers=new LinkedHashMap<>();
        Set<String> newSops=new HashSet<>(),imageFrames=new HashSet<>();
        Map<String,String> originalByCleanUid=new HashMap<>();
        try {
            int n=0;
            for(Instance input:inputs){
                cancelled(cancel);progress.update("Cleaning metadata",n++,inputs.size());
                Path out=null;
                try {
                    if(!input.supported())throw new IllegalArgumentException(input.problem());
                    if(seen.contains(input.sop()))throw new IllegalArgumentException("Duplicate SOP Instance UID; another copy is already included");
                    if(!DicomIO.hash(input.source()).equals(input.sha256()))throw new IOException("Source changed after import; import it again");
                    DicomIO.Loaded source=DicomIO.read(input.source());AttributeList l=source.list();
                    for(DicomIO.Row row:DicomIO.rows(l))if(Policy.key(row.attribute().getTag())==0x00081155&&!row.value().isBlank())for(String uid:row.value().split("\\\\")){
                        originalByCleanUid.put(policy.mapUid(uid),uid);
                    }
                    PixelSafety.inspect(source);
                    guard.collect(l);
                    Map<String,String> geometry=DicomIO.geometry(l),pixels=DicomIO.pixels(l),uidExpected=new HashMap<>();
                    for(DicomIO.Row row:DicomIO.rows(l))if(row.attribute().getTag().getGroup()!=2&&row.vr().equals("UI")&&UniqueIdentifierAttribute.isTransient(row.attribute().getTag())&&!row.value().isBlank()){
                        List<String> mapped=new ArrayList<>();for(String uid:row.value().split("\\\\"))mapped.add(policy.mapUid(uid));
                        uidExpected.put(row.path(),String.join("\\",mapped));
                    }
                    boolean classic=Set.of(SOPClass.CTImageStorage,SOPClass.MRImageStorage,SOPClass.SpatialRegistrationStorage,SOPClass.DeformableSpatialRegistrationStorage).contains(input.sopClass());
                    policy.forInstance(input.importRoot(),options.minimal()&&classic);
                    if(options.minimal()&&!classic)batch.warnings.add(input.relativePath()+": broader metadata policy used to preserve enhanced/secondary-capture structures");
                    policy.clean(l);
                    ScanLabels.Labels label=labels.get(input.series());if(label!=null)label.apply(l);
                    // A persistent tag-removal rule takes precedence over scan-label overrides.
                    identifiers.removeTags(l);
                    int series=seriesNumbers.computeIfAbsent(input.series(),k->seriesNumbers.size()+1);
                    String relative=String.format(Locale.ROOT,"DATA/S%07d/I%07d",series,n);
                    out=stage.resolve(relative);Files.createDirectories(out.getParent());DicomIO.write(l,out,source.syntax());
                    DicomIO.Loaded verified=DicomIO.read(out);
                    if(!DicomIO.geometry(verified.list()).equals(geometry)||!DicomIO.pixels(verified.list()).equals(pixels))throw new IOException("Pixel or geometry verification failed");
                    validateMetadata(verified.list(),options,policy.date(),uidExpected);
                    if(!source.syntax().equals(verified.syntax()))throw new IOException("Transfer syntax changed unexpectedly");
                    if(!DicomIO.hash(input.source()).equals(input.sha256()))throw new IOException("Source changed during cleaning; import it again");
                    batch.outputs.add(new Output(input,out,DicomIO.hash(out),relative));seen.add(input.sop());
                    guard.capture(seriesLabel(input),verified.list());guard.skipDate(policy.date());
                    newSops.add(Policy.value(verified.list(),0x00080018));
                    if(!DicomIO.isRegistration(input.sopClass()))imageFrames.add(Policy.value(verified.list(),0x00200052));
                }catch(CancellationException e){throw e;}
                catch(Exception e){if(out!=null)Files.deleteIfExists(out);batch.skipped.add(input.relativePath()+": "+reason(e));}
            }
            // Incomplete registrations are skipped without blocking the supported images.
            boolean changed;
            do {
                changed=false;
                for(Iterator<Output> it=batch.outputs.iterator();it.hasNext();){Output o=it.next();if(!DicomIO.isRegistration(o.input.sopClass()))continue;
                    AttributeList l=DicomIO.read(o.file).list();
                    if(!imageFrames.containsAll(DicomIO.frameUids(l))||!newSops.containsAll(DicomIO.referencedInstances(l))){
                        newSops.remove(Policy.value(l,0x00080018));it.remove();Files.deleteIfExists(o.file);changed=true;
                        batch.skipped.add(o.input.relativePath()+": registration refers to images or a frame of reference outside this selection");
                    }
                }
            }while(changed);
            Map<String,List<Referrer>> refs=new TreeMap<>();
            for(Output o:batch.outputs)for(DicomIO.Row row:DicomIO.rows(DicomIO.read(o.file).list()))
                if(Policy.key(row.attribute().getTag())==0x00081155&&!row.value().isBlank())for(String uid:row.value().split("\\\\"))
                    if(!newSops.contains(uid))refs.computeIfAbsent(uid,k->new ArrayList<>()).add(new Referrer(o.input.source(),row.path()));
            batch.unresolvedReferences=refs.size();
            Set<Path> selected=new HashSet<>();for(Instance input:inputs)selected.add(input.source());
            for(var entry:refs.entrySet()){
                String uid=originalByCleanUid.getOrDefault(entry.getKey(),entry.getKey());
                List<Path> targets=imported.stream().filter(i->i.sop().equals(uid)).map(Instance::source).distinct().toList();
                String state=targets.isEmpty()?"Not loaded — target filename/path is unknown":targets.stream().anyMatch(selected::contains)?"Skipped during cleaning":"Imported but not selected for this export";
                batch.referenceDetails.add(new ReferenceDetail(uid,entry.getKey(),targets,List.copyOf(new LinkedHashSet<>(entry.getValue())),state));
            }
            progress.update("Checking retained metadata for identifiers",inputs.size(),inputs.size());
            batch.identifierFindings.addAll(guard.findings());
            if(guard.replacementLooksIdentifying(options.patientName())||guard.replacementLooksIdentifying(options.patientId()))
                batch.identifierFindings.add(0,new ResidualGuard.Finding("Replacement identity","Replacement Patient Name / ID","contains an identifier from the source files",inputs.size()));
            progress.update("Metadata ready",inputs.size(),inputs.size());return batch;
        }catch(Throwable e){try{batch.close();}catch(IOException cleanup){e.addSuppressed(cleanup);}throw e;}
    }
    /**
     * Why a file is likely to contain burned-in text, or "" when there is no indication. There is no OCR:
     * these are metadata cues only. MPR/reformat images (DERIVED\SECONDARY with patient geometry) are not
     * flagged, to keep the badge meaningful.
     */
    static String burnedInHint(AttributeList l,String sopClass){
        List<String> why=new ArrayList<>();
        if(Policy.value(l,0x00280301).strip().equalsIgnoreCase("YES"))why.add("scanner flags burned-in annotation");
        if(isSecondaryCapture(sopClass))why.add("secondary capture (screenshot, report or dose page)");
        Attribute imageType=l.get(TagFromName.ImageType);
        String type="\\"+(imageType==null?"":imageType.getDelimitedStringValuesOrEmptyString()).toUpperCase(Locale.ROOT)+"\\";
        if(type.contains("SCREEN SAVE")||type.contains("SCREENSHOT"))why.add("Image Type says screen save");
        else if(type.contains("\\SECONDARY\\")&&l.get(TagFromName.ImagePositionPatient)==null&&!isSecondaryCapture(sopClass))why.add("secondary image without patient position");
        return String.join("; ",why);
    }
    static boolean isSecondaryCapture(String sop){return sop.equals("1.2.840.10008.5.1.4.1.1.7")||sop.startsWith("1.2.840.10008.5.1.4.1.1.7.");}
    static String seriesLabel(Instance i){String d=i.description()==null||i.description().isBlank()?"(no series description)":i.description();return i.modality()+" · "+d+" · series "+i.seriesNumber();}
    private static String reason(Exception e){return e instanceof IllegalArgumentException||e instanceof IOException?String.valueOf(e.getMessage()):"Could not process this instance ("+e.getClass().getSimpleName()+")";}
    private static void validateMetadata(AttributeList l, Policy.Options options, LocalDate date, Map<String,String> expectedUids) throws Exception {
        if (!Policy.value(l,0x00100010).equals(options.patientName()) || !Policy.value(l,0x00100020).equals(options.patientId()))
            throw new IOException("Replacement identity verification failed.");
        for (DicomIO.Row row : DicomIO.rows(l)) {
            AttributeTag t = row.attribute().getTag(); int group = t.getGroup();
            if (options.keepSiemensDiffusion() && !row.path().contains("/") && Policy.siemensDiffusionTag(l,t)) continue;
            if (t.isPrivate() || group == 0x0400 || group == 0xfffa || (group & 0xff00) == 0x6000 || row.vr().equals("UN"))
                throw new IOException("A disallowed metadata element survived cleaning.");
            if (expectedUids.containsKey(row.path()) && !expectedUids.get(row.path()).equals(row.value()))
                throw new IOException("UID reference validation failed.");
            if (!row.value().isEmpty() && Set.of("DA","DT","TM").contains(row.vr())) {
                String expected = row.vr().equals("TM") ? "000000" : date.toString().replace("-","") + (row.vr().equals("DT") ? "000000" : "");
                if (!Arrays.stream(row.value().split("\\\\")).allMatch(expected::equals)) throw new IOException("An original date/time survived cleaning.");
            }
        }
    }
    public static void redact(Output output, List<? extends Shape> shapes) throws Exception {
        if (shapes.isEmpty()) throw new IllegalArgumentException("Draw at least one blackout rectangle first.");
        if (!DicomIO.hash(output.file).equals(output.sha256)) throw new IOException("Staged data changed. Clean the selection again.");
        DicomIO.Loaded loaded = DicomIO.read(output.file); AttributeList l = loaded.list();
        if (l.get(TagFromName.PixelData) == null) throw new IllegalArgumentException("This instance has no image pixels.");
        // Explicitly scoped to integer monochrome CT/MR pixels; other images remain inspectable.
        String photometric = Policy.value(l,0x00280004);
        if (!photometric.startsWith("MONOCHROME")) throw new IllegalArgumentException("Blackout currently supports monochrome CT/MRI. Redact color images externally and reimport them.");
        Map<String,String> geometry = DicomIO.geometry(l);
        SourceImage image = new SourceImage(l);
        Path replacement = output.file.resolveSibling(output.file.getFileName()+".redacting");
        try {
            Vector<Shape> areas = new Vector<>(shapes);
            ImageEditUtilities.blackout(image,l,areas,false,true,false,0);
            l.correctDecompressedImagePixelModule(true);
            l.insertLossyImageCompressionHistoryIfDecompressed(true);
            l.remove(Policy.tag(0x00120062));
            // YES/NO for burned-in annotation is only set after the operator reviews the resulting images.
            DicomIO.write(l,replacement,TransferSyntax.ExplicitVRLittleEndian);
            if (!DicomIO.geometry(DicomIO.read(replacement).list()).equals(geometry)) throw new IOException("Geometry changed during redaction.");
        } finally { try { image.close(); } catch (Throwable ignored) {} }
        try { Files.move(replacement,output.file,StandardCopyOption.REPLACE_EXISTING); }
        finally { Files.deleteIfExists(replacement); }
        output.sha256 = DicomIO.hash(output.file); output.reviewed = false; output.redacted = true;
    }
    public static Path export(Batch batch, Path target, ExportOptions options,
                              Progress progress, BooleanSupplier cancel) throws Exception {
        if (!batch.exportReady()) throw new IllegalArgumentException("Clean the selected files before exporting.");
        target = target.toAbsolutePath().normalize();
        if (Files.exists(target)) throw new IOException("Choose a new output name. Existing files and folders are never overwritten.");
        Path parent = target.getParent().toRealPath();
        for (Output o : batch.outputs) {
            if (target.equals(o.input.source) || target.startsWith(o.input.importRoot))
                throw new IOException("Choose an output location outside the imported source folders.");
        }
        Path transaction = Files.createTempDirectory(parent,".bnel-export-"); Path archive = null;
        try {
            int n=0; Map<Output,String> exportedNames = exportNames(batch,options);
            for (Output o : batch.outputs) {
                cancelled(cancel); progress.update("Exporting",n,batch.outputs.size());
                if (!DicomIO.hash(o.file).equals(o.sha256)) throw new IOException("Cleaned data changed. Clean the selection again.");
                DicomIO.Loaded loaded = DicomIO.read(o.file);
                Map<String,String> pixels = DicomIO.pixels(loaded.list());
                Path out=transaction.resolve(exportedNames.get(o)); Files.createDirectories(out.getParent());
                DicomIO.write(loaded.list(),out,loaded.syntax());
                if (!DicomIO.pixels(DicomIO.read(out).list()).equals(pixels)) throw new IOException("Export verification failed.");
                progress.update("Exporting",++n,batch.outputs.size());
            }
            if (options.dicomdir) {
                progress.update("Creating DICOMDIR",0,1);
                DicomDirectory directory = new DicomDirectory();
                for (Output o:batch.outputs) directory.readDicomFileAndAddToDirectory(transaction.toFile(),exportedNames.get(o).replace('/',File.separatorChar));
                directory.write(transaction.resolve("DICOMDIR").toString());
                DicomIO.redactDirectoryWriterLabels(transaction.resolve("DICOMDIR"));
            }
            cancelled(cancel);
            if (options.zip) {
                archive=Files.createTempFile(parent,".bnel-archive-",".zip");
                try (ZipOutputStream zout=new ZipOutputStream(Files.newOutputStream(archive))) {
                    try (var paths=Files.walk(transaction)) {
                        for (Path p:paths.filter(Files::isRegularFile).toList()) {
                            cancelled(cancel); zout.putNextEntry(new ZipEntry(transaction.relativize(p).toString().replace(File.separatorChar,'/')));
                            Files.copy(p,zout);zout.closeEntry();
                        }
                    }
                }
                Files.move(archive,target);
            } else Files.move(transaction,target);
            progress.update("Export complete",1,1); return target;
        } finally {
            DicomIO.deleteTree(transaction);
            if (archive!=null) Files.deleteIfExists(archive);
        }
    }
    static Map<Output,String> exportNames(Batch b, ExportOptions options) throws IOException {
        Map<Output,String> names = new LinkedHashMap<>();
        Map<Path,String> roots = new LinkedHashMap<>(), folders = new LinkedHashMap<>();
        for (Output o:b.outputs) roots.computeIfAbsent(o.input.importRoot,k->String.format("R%07d",roots.size()+1));
        Set<String> unique = new HashSet<>(); int n=0;
        for (Output o:b.outputs) {
            String name=o.relativeName;
            if (options.preserveHierarchy) {
                List<String> parts = new ArrayList<>();
                if (roots.size()>1) parts.add(roots.get(o.input.importRoot));
                Path current=o.input.importRoot, rel=o.input.relativePath;
                for (int j=0;j<rel.getNameCount()-1;j++) {
                    current=current.resolve(rel.getName(j));
                    parts.add(options.retainNames ? rel.getName(j).toString() : folders.computeIfAbsent(current,k->String.format("F%07d",folders.size()+1)));
                }
                parts.add(options.retainNames ? rel.getFileName().toString() : String.format("I%07d",++n));
                name=String.join("/",parts);
            }
            if (options.dicomdir && (name.split("/").length>8 || !Arrays.stream(name.split("/")).allMatch(p->p.matches("[A-Z0-9_]{1,8}"))))
                throw new IOException("DICOMDIR requires up to 8 short uppercase path components. Use neutral names or the series-folder layout.");
            if (!unique.add(name.toLowerCase(Locale.ROOT)) || name.equalsIgnoreCase("DICOMDIR") || name.equalsIgnoreCase("BNEL-Review-Report.txt"))
                throw new IOException("Output names collide. Use neutral names or clean the source groups separately.");
            names.put(o,name);
        }
        return names;
    }
}
