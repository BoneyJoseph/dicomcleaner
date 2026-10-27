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
                           String modality, int instanceNumber, int frames, String description,
                           String problem, LocalDate studyDate) {
        public boolean supported() { return problem.isEmpty(); }
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
    public static final class Batch implements AutoCloseable {
        public final Path staging;
        public final List<Output> outputs = new ArrayList<>();
        public final Policy.Options options;
        public final String exceptionReason;
        public final Map<String,Integer> operations;
        public int unresolvedReferences;
        public boolean referencesAcknowledged;
        Batch(Path staging, Policy.Options options, String reason, Map<String,Integer> operations) {
            this.staging = staging; this.options = options; this.exceptionReason = reason; this.operations = operations;
        }
        public boolean exportReady() {
            return !outputs.isEmpty() && outputs.stream().allMatch(o -> o.reviewed) &&
                (unresolvedReferences == 0 || referencesAcknowledged);
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
                    Policy.value(l,0x00080060),Attribute.getSingleIntegerValueOrDefault(l,TagFromName.InstanceNumber,0),
                    Math.max(1,Attribute.getSingleIntegerValueOrDefault(l,TagFromName.NumberOfFrames,1)),
                    Policy.value(l,0x0008103e),problem,date));
            } catch (Exception e) {
                if (e instanceof CancellationException) throw e;
                // Show names locally to let the operator locate failures, never include these in reports.
                issues.add("Could not parse: " + path.getFileName());
            }
        }
        return new Scan(List.copyOf(instances),skipped,List.copyOf(issues));
    }
    public static Batch prepare(List<Instance> inputs, Policy.Options options, String reason,
                                Progress progress, BooleanSupplier cancel) throws Exception {
        return prepare(inputs,options,reason,new IdentifierPolicy(),Map.of(),progress,cancel);
    }
    static Batch prepare(List<Instance> inputs, Policy.Options options, String reason,IdentifierPolicy identifiers,
                         Map<String,ScanLabels.Labels> labels,Progress progress,BooleanSupplier cancel)throws Exception {
        if (inputs.isEmpty()) throw new IllegalArgumentException("Select a subject or images to clean.");
        Set<String> patients = new HashSet<>(), sopUids = new HashSet<>();
        for (Instance i : inputs) {
            if (!i.supported()) throw new IllegalArgumentException("Selection contains an unsupported or invalid instance. Inspect its status and remove it from the selection.");
            if(options.minimal()&&!Set.of(SOPClass.CTImageStorage,SOPClass.MRImageStorage,SOPClass.SpatialRegistrationStorage,SOPClass.DeformableSpatialRegistrationStorage).contains(i.sopClass))
                throw new IllegalArgumentException("Essential research metadata currently supports classic CT/MR and registration objects. Turn it off for enhanced or secondary-capture objects so required structures are retained.");
            if (!i.patientId.isBlank() || !i.patientName.isBlank()) patients.add(i.patientKey);
            if (!sopUids.add(i.sop)) throw new IllegalArgumentException("Duplicate SOP Instance UIDs found. Select only one copy of each instance.");
        }
        if (patients.size() > 1) throw new IllegalArgumentException("The selection has different patient identifiers or names. Select one subject at a time; identities will not be silently merged.");
        if (options.dateMode() == Policy.DateMode.SHIFT && (reason == null || reason.strip().isEmpty()))
            throw new IllegalArgumentException("Date shifting needs a documented exception; it is not the Safe Harbor default.");
        LocalDate earliest = inputs.stream().map(Instance::studyDate).filter(Objects::nonNull).min(LocalDate::compareTo).orElse(null);
        Policy policy = new Policy(options,earliest,identifiers);
        ResidualGuard guard=new ResidualGuard();
        int scanned=0;
        // Collect from EVERY instance before cleaning any instance: late-merged files may identify it differently.
        for(Instance input:inputs) {
            cancelled(cancel);progress.update("Checking identifiers in every file",scanned++,inputs.size());
            if(!DicomIO.hash(input.source).equals(input.sha256)) throw new IOException("A source changed after import. Import it again.");
            guard.collect(DicomIO.read(input.source).list());
        }
        guard.validateReplacement(options);
        Path stage = Files.createTempDirectory("bnel-cleaner-");
        Batch batch = new Batch(stage, options, reason == null ? "" : reason.strip(),policy.counts());
        Map<String,Integer> seriesNumbers = new LinkedHashMap<>();
        Set<String> newSops = new HashSet<>(), newImageFrames = new HashSet<>(), referenced = new HashSet<>();
        List<Set<String>> registrationFrames = new ArrayList<>();
        try {
            int n = 0;
            for (Instance input : inputs) {
                cancelled(cancel); progress.update("Cleaning metadata", n, inputs.size());
                if (!DicomIO.hash(input.source).equals(input.sha256)) throw new IOException("A source file changed after import. Import it again before cleaning.");
                DicomIO.Loaded source = DicomIO.read(input.source); AttributeList l = source.list();
                PixelSafety.inspect(source);
                Map<String,String> geometry = DicomIO.geometry(l), pixels = DicomIO.pixels(l);
                Map<String,String> uidExpected = new HashMap<>();
                for (DicomIO.Row row : DicomIO.rows(l)) if (row.attribute().getTag().getGroup()!=2 && row.vr().equals("UI") && UniqueIdentifierAttribute.isTransient(row.attribute().getTag()) && !row.value().isBlank()) {
                    String[] old = row.value().split("\\\\"); List<String> mapped = new ArrayList<>();
                    for (String uid : old) mapped.add(policy.mapUid(uid));
                    uidExpected.put(row.path(),String.join("\\",mapped));
                }
                policy.clean(l);
                ScanLabels.Labels label=labels.get(input.series);if(label!=null)label.apply(l);
                int series = seriesNumbers.computeIfAbsent(input.series,k -> seriesNumbers.size()+1);
                String relative = String.format(Locale.ROOT,"DATA/S%07d/I%07d",series,++n);
                Path out = stage.resolve(relative); Files.createDirectories(out.getParent());
                DicomIO.write(l,out,source.syntax());
                DicomIO.Loaded verified = DicomIO.read(out);
                if (!DicomIO.geometry(verified.list()).equals(geometry) || !DicomIO.pixels(verified.list()).equals(pixels))
                    throw new IOException("Pixel or geometry verification failed. No output will be exported.");
                validateMetadata(verified.list(),options,uidExpected);
                guard.verify(verified.list());
                if (!source.syntax().equals(verified.syntax())) throw new IOException("Transfer syntax changed unexpectedly.");
                if (!DicomIO.hash(input.source).equals(input.sha256)) throw new IOException("A source changed during cleaning. No output will be exported.");
                batch.outputs.add(new Output(input,out,DicomIO.hash(out),relative));
                newSops.add(Policy.value(verified.list(),0x00080018));
                referenced.addAll(DicomIO.referencedInstances(verified.list()));
                if (DicomIO.isRegistration(input.sopClass)) registrationFrames.add(DicomIO.frameUids(verified.list()));
                else newImageFrames.add(Policy.value(verified.list(),0x00200052));
                progress.update("Cleaning metadata", n, inputs.size());
            }
            progress.update("Validating references",0,1);
            for (Set<String> frames : registrationFrames) if (!newImageFrames.containsAll(frames))
                throw new IllegalArgumentException("Registration refers to a frame of reference without matching imported images. Import the related CT and MRI together.");
            referenced.removeAll(newSops); batch.unresolvedReferences = referenced.size();
            if(!registrationFrames.isEmpty() && batch.unresolvedReferences>0)
                throw new IllegalArgumentException("Registration references images outside this selection. Import and select the complete CT/MRI registration set.");
            progress.update("Metadata ready — image review required",1,1);
            return batch;
        } catch (Throwable e) {
            try { batch.close(); } catch (IOException cleanup) { e.addSuppressed(cleanup); }
            throw e;
        }
    }
    private static void validateMetadata(AttributeList l, Policy.Options options, Map<String,String> expectedUids) throws Exception {
        if (!Policy.value(l,0x00100010).equals(options.patientName()) || !Policy.value(l,0x00100020).equals(options.patientId()))
            throw new IOException("Replacement identity verification failed.");
        for (DicomIO.Row row : DicomIO.rows(l)) {
            AttributeTag t = row.attribute().getTag(); int group = t.getGroup();
            if (t.isPrivate() || group == 0x0400 || group == 0xfffa || (group & 0xff00) == 0x6000 || row.vr().equals("UN"))
                throw new IOException("A disallowed metadata element survived cleaning.");
            if (expectedUids.containsKey(row.path()) && !expectedUids.get(row.path()).equals(row.value()))
                throw new IOException("UID reference validation failed.");
            if (options.dateMode() == Policy.DateMode.FIXED && !row.value().isEmpty() && Set.of("DA","DT","TM").contains(row.vr())) {
                String expected = row.vr().equals("TM") ? "000000" : options.targetDate().toString().replace("-","") + (row.vr().equals("DT") ? "000000" : "");
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
            Policy.put(l,0x00120062,"NO");
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
        if (!batch.exportReady()) throw new IllegalArgumentException("Review all selected instances and resolve reference warnings before exporting.");
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
                if (!DicomIO.hash(o.file).equals(o.sha256)) throw new IOException("Cleaned data changed after review. Clean and review again.");
                DicomIO.Loaded loaded = DicomIO.read(o.file);
                Map<String,String> pixels = DicomIO.pixels(loaded.list());
                Policy.put(loaded.list(),0x00120062,"YES");
                if (loaded.list().get(TagFromName.PixelData)!=null) {
                    Policy.put(loaded.list(),0x00280301,"NO");
                    Policy.put(loaded.list(),0x00280302,"NO");
                }
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
            }
            writeReport(batch,transaction.resolve("BNEL-Review-Report.txt"));
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
    public static void writeReport(Batch b,Path path) throws IOException {
        StringBuilder s = new StringBuilder("BNEL Dicom Cleaner "+Policy.VERSION+"\n");
        s.append("Profile: ").append(b.options.dateMode()==Policy.DateMode.FIXED ? "Safe Harbor metadata defaults" : "CUSTOM — date-shift exception").append('\n');
        s.append("Generated: ").append(Instant.now()).append("\nInstances: ").append(b.outputs.size());
        s.append("\nReviewed instances: ").append(b.outputs.stream().filter(o->o.reviewed).count());
        s.append("\nRedacted instances: ").append(b.outputs.stream().filter(o->o.redacted).count());
        s.append("\nReferences outside this export: ").append(b.unresolvedReferences);
        s.append("\nException recorded: ").append(!b.exceptionReason.isEmpty());
        s.append("\nEssential research metadata: ").append(b.options.minimal());
        // Free-form exception notes stay in memory: they can themselves contain PHI.
        s.append("\nNo original names, paths, dates, UIDs, or linkage map are included.\n");
        s.append("Geometry and pixel payloads checked after metadata cleaning; intentional blackouts recorded separately.\n");
        s.append("Image review is the operator's assessment, not automatic detection or HIPAA certification.\n");
        s.append("Head CT/MRI with recognizable facial anatomy requires appropriate defacing or a separately established disclosure basis.\n");
        for (var e:b.operations.entrySet()) s.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        Files.writeString(path,s.toString(),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
    }
}
