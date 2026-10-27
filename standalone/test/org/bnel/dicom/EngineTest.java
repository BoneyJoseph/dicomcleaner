package org.bnel.dicom;

import com.pixelmed.dicom.*;
import java.awt.geom.Rectangle2D;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.zip.*;

/** Synthetic-only integration tests; no patient information or clinical fixtures. */
public final class EngineTest {
    static int assertions;
    static final Cleaner.Progress PROGRESS=(s,n,t)->{};
    static final Policy.Options OPTIONS=new Policy.Options("Research^001","BNEL-001",Policy.DateMode.FIXED,LocalDate.of(2000,1,1));
    static void check(boolean result,String message){assertions++;if(!result)throw new AssertionError(message);}
    static void expect(Class<? extends Throwable> type,Throwing action,String message)throws Exception{
        try{action.run();}catch(Throwable e){check(type.isInstance(e),message+" (wrong error: "+e+")");return;}throw new AssertionError(message+" (did not fail)");
    }
    interface Throwing{void run()throws Exception;}
    static void value(AttributeList l,int tag,String value)throws Exception{Policy.put(l,tag,value);}
    static void number(AttributeList l,int tag,int... values)throws Exception{Attribute a=AttributeFactory.newAttribute(Policy.tag(tag));for(int v:values)a.addValue(v);l.put(a);}
    static void decimals(AttributeList l,int tag,String... values)throws Exception{Attribute a=AttributeFactory.newAttribute(Policy.tag(tag));for(String v:values)a.addValue(v);l.put(a);}
    static SequenceAttribute sequence(AttributeList l,int tag,AttributeList... items){SequenceAttribute s=new SequenceAttribute(Policy.tag(tag));for(AttributeList item:items)s.addItem(item);l.put(s);return s;}
    public static AttributeList image(int n,boolean mr)throws Exception{
        AttributeList l=new AttributeList();
        value(l,0x00080016,mr?SOPClass.MRImageStorage:SOPClass.CTImageStorage);value(l,0x00080018,"2.25.100001234567890"+n);
        value(l,0x0020000d,"2.25.1000022345678901");value(l,0x0020000e,mr?"2.25.1000032345678902":"2.25.1000032345678901");value(l,0x00200052,mr?"2.25.1000042345678902":"2.25.1000042345678901");
        value(l,0x00100010,"Synthetic^Zebra");value(l,0x00100020,"TESTMRN778899");value(l,0x00100030,"19770718");value(l,0x00100040,"M");
        value(l,0x00080020,"20240317");value(l,0x00080021,"20240318");value(l,0x00080022,"20240319");value(l,0x00080030,"124522.123456");value(l,0x0008002a,"20240319124522.123456-0500");
        value(l,0x00080050,"ACC99887766");value(l,0x00200010,"STUDY554433");value(l,0x00080060,mr?"MR":"CT");number(l,0x00200013,n);number(l,0x00200011,mr?2:1);
        decimals(l,0x00080008,"ORIGINAL","PRIMARY","CODE_TESTMRN778899_LONG");
        value(l,0x0008103e,"Spiral protocol / subject=Synthetic Zebra; MRN: TESTMRN778899; DOB 1977-07-18; merged 2024/03/19");
        value(l,0x00181030,"T1 with free text on "+"other text ".repeat(100)+"zebra@example.test C:\\Patients\\TESTMRN778899\\study");
        value(l,0x0040a160,"long nested-looking XML <Name>Synthetic^Zebra</Name> "+"trailing padding words ".repeat(500));
        value(l,0x00100032,"081234");value(l,0x00101040,"987 Example Road");value(l,0x00102154,"+1-555-987-6543");value(l,0x001021a0,"YES");
        decimals(l,0x00180020,"IR","SE");value(l,0x00180021,"SK");value(l,0x00180022,"FS");value(l,0x00180023,"3D");value(l,0x00180024,"tfl3d1");value(l,0x00180080,"2000");value(l,0x00180081,"3.5");value(l,0x00180082,"900");
        value(l,0x00080070,"SyntheticScannerCo");value(l,0x00080090,"Doctor^Synthetic");value(l,0x00080080,"Synthetic Institution");value(l,0x00181000,"SERIAL_9988776655");
        LongStringAttribute privateText=new LongStringAttribute(new AttributeTag(0x0019,0x1010));privateText.addValue("TESTMRN778899");l.put(privateText);
        UnlimitedTextAttribute unknown=new UnlimitedTextAttribute(new AttributeTag(0x7776,0x9001));unknown.addValue("Unknown public text: zebra@example.test");l.put(unknown);
        OtherByteAttribute opaque=new OtherByteAttribute(new AttributeTag(0x7776,0x9002));opaque.setValues("TESTMRN778899".getBytes(StandardCharsets.UTF_8));l.put(opaque);
        number(l,0x00280002,1);value(l,0x00280004,"MONOCHROME2");number(l,0x00280010,32);number(l,0x00280011,32);
        number(l,0x00280100,16);number(l,0x00280101,12);number(l,0x00280102,11);number(l,0x00280103,0);
        decimals(l,0x00200032,"0","0",String.valueOf(n));decimals(l,0x00200037,"1","0","0","0","1","0");decimals(l,0x00280030,"0.5","0.5");
        value(l,0x00180050,"1");value(l,0x00281052,"-1024");value(l,0x00281053,"1");value(l,0x00281054,"HU");
        short[] px=new short[1024];for(int i=0;i<px.length;i++)px[i]=(short)(1000+i);OtherWordAttribute pixels=new OtherWordAttribute(TagFromName.PixelData);pixels.setValues(px);l.put(pixels);
        AttributeList nested=new AttributeList();value(nested,0x00081150,l.get(TagFromName.SOPClassUID).getSingleStringValueOrEmptyString());value(nested,0x00081155,"2.25.1000012345678901");value(nested,0x0040a160,"Injected nested note: TESTMRN778899 and Synthetic^Zebra");sequence(l,0x00082112,nested);
        return l;
    }
    static AttributeList registration()throws Exception{
        AttributeList r=new AttributeList();value(r,0x00080016,SOPClass.SpatialRegistrationStorage);value(r,0x00080018,"2.25.1000092345678901");
        value(r,0x0020000d,"2.25.1000022345678901");value(r,0x0020000e,"2.25.1000032345678903");value(r,0x00100010,"Synthetic^Zebra");value(r,0x00100020,"TESTMRN778899");
        value(r,0x00080020,"20240317");value(r,0x00080030,"124500");value(r,0x00080060,"REG");value(r,0x00700080,"ORIGINAL_LABEL");number(r,0x00200013,1);number(r,0x00200011,3);
        List<AttributeList> items=new ArrayList<>();for(int n=1;n<=2;n++){
            AttributeList item=new AttributeList();value(item,0x00200052,"2.25.100004234567890"+n);
            AttributeList ref=new AttributeList();value(ref,0x00081150,n==1?SOPClass.CTImageStorage:SOPClass.MRImageStorage);value(ref,0x00081155,"2.25.100001234567890"+n);sequence(item,0x00081140,ref);
            AttributeList matrix=new AttributeList();decimals(matrix,0x300600c6,"1","0","0","0","0","1","0","0","0","0","1","0","0","0","0","1");value(matrix,0x0070030c,"RIGID");
            AttributeList matrixRegistration=new AttributeList();sequence(matrixRegistration,0x0070030a,matrix);sequence(item,0x00700309,matrixRegistration);items.add(item);
        }sequence(r,0x00700308,items.toArray(AttributeList[]::new));return r;
    }
    static AttributeList reportFixture()throws Exception{
        byte[] bytes;
        try(org.apache.pdfbox.pdmodel.PDDocument document=new org.apache.pdfbox.pdmodel.PDDocument();java.io.ByteArrayOutputStream buffer=new java.io.ByteArrayOutputStream()){
            for(int n=1;n<=2;n++){
                org.apache.pdfbox.pdmodel.PDPage page=new org.apache.pdfbox.pdmodel.PDPage();document.addPage(page);
                try(org.apache.pdfbox.pdmodel.PDPageContentStream content=new org.apache.pdfbox.pdmodel.PDPageContentStream(document,page)){
                    content.beginText();content.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),18);content.newLineAtOffset(55,700);content.showText("SYNTHETIC TREATMENT REPORT - page "+n);content.newLineAtOffset(0,-40);content.showText("Patient: Synthetic Zebra / TESTMRN778899");content.endText();
                }
            }
            document.save(buffer);bytes=buffer.toByteArray();
        }
        AttributeList l=image(3,false);l.remove(TagFromName.PixelData);value(l,0x00080016,SOPClass.EncapsulatedPDFStorage);value(l,0x00080060,"DOC");value(l,0x0020000e,"2.25.1000032345678909");number(l,0x00200011,9);
        OtherByteAttribute doc=new OtherByteAttribute(Policy.tag(0x00420011));doc.setValues(bytes);l.put(doc);value(l,0x00420012,"application/pdf");return l;
    }
    static void write(AttributeList l,Path p)throws Exception{Files.createDirectories(p.getParent());DicomIO.write(l,p,TransferSyntax.ExplicitVRLittleEndian);}
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("bnel-synthetic-tests-");
        try{
            Path input=root.resolve("source/Synthetic Zebra");Path ct=input.resolve("CT/site/file1.dcm"),mr=input.resolve("MR/later-file2.dcm"),reg=input.resolve("REG/reg.dcm");
            write(image(1,false),ct);AttributeList later=image(2,true);value(later,0x00324000,"File merged later; DOB=1977-07-18; email=zebra@example.test; SSN 123-45-6789");write(later,mr);write(registration(),reg);
            Cleaner.Scan scan=Cleaner.scan(List.of(input),PROGRESS,()->false);check(scan.instances().size()==3,"scan every image and REG");check(scan.issues().isEmpty(),"scan without errors");
            List<MetadataDifferences.Difference> differences=MetadataDifferences.compute(scan.instances(),PROGRESS,()->false);
            check(differences.stream().anyMatch(d->d.name().equals("StudyComments")&&d.missing()==2),"detect late-merged metadata missing from earlier files");
            try(Cleaner.Batch batch=Cleaner.prepare(scan.instances(),OPTIONS,"",PROGRESS,()->false)){
                check(batch.outputs.size()==3,"all files cleaned");check(!batch.exportReady(),"export blocked before review");check(batch.unresolvedReferences==0,"registration links resolved");
                Set<String> frameUids=new HashSet<>();String study=null;for(Cleaner.Output o:batch.outputs){
                    AttributeList l=DicomIO.read(o.file).list();check(DicomIO.hash(o.input.source()).equals(o.input.sha256()),"source unchanged");
                    for(DicomIO.Row row:DicomIO.rows(l)){
                        String v=row.value();check(!v.contains("TESTMRN778899")&&!v.contains("Synthetic")&&!v.contains("zebra@example")&&(row.vr().equals("UI")||(!v.contains("1977")&&!v.contains("2024"))),"no identifiers embedded in long or nested text: "+row.path());
                        check(!row.attribute().getTag().isPrivate(),"private field removed");
                    }
                    check(Policy.value(l,0x00120062).equals("NO"),"no premature identity removal assertion");
                    String s=Policy.value(l,0x0020000d);if(study==null)study=s;else check(study.equals(s),"consistent study remap");
                    if(!DicomIO.isRegistration(o.input.sopClass()))frameUids.add(Policy.value(l,0x00200052));
                    check(DicomIO.geometry(l).equals(DicomIO.geometry(DicomIO.read(o.input.source()).list())),"geometry preserved");
                }
                for(Cleaner.Output o:batch.outputs)if(DicomIO.isRegistration(o.input.sopClass()))check(frameUids.containsAll(DicomIO.frameUids(DicomIO.read(o.file).list())),"REG frame references match CT/MR");
                expect(IllegalArgumentException.class,()->Cleaner.export(batch,root.resolve("blocked"),new Cleaner.ExportOptions(false,false,true,false),PROGRESS,()->false),"unreviewed export denied");
                for(Cleaner.Output o:batch.outputs)o.reviewed=true;
                batch.requiresReclean=true;expect(IllegalArgumentException.class,()->Cleaner.export(batch,root.resolve("removed-instance"),new Cleaner.ExportOptions(false,false,true,false),PROGRESS,()->false),"file exclusion blocks a previously approved batch");batch.requiresReclean=false;
                Path out=Cleaner.export(batch,root.resolve("export"),new Cleaner.ExportOptions(false,true,true,false),PROGRESS,()->false);
                check(Files.isRegularFile(out.resolve("DICOMDIR")),"DICOMDIR written");AttributeList dir=DicomIO.read(out.resolve("DICOMDIR")).list();
                int fileReferences=0;for(DicomIO.Row row:DicomIO.rows(dir))if(Policy.key(row.attribute().getTag())==0x00041500){check(Files.isRegularFile(out.resolve(row.value().replace('\\','/'))),"DICOMDIR points to exported file");fileReferences++;}check(fileReferences==3,"all instances included in DICOMDIR");
                Map<Cleaner.Output,String> names=Cleaner.exportNames(batch,new Cleaner.ExportOptions(false,true,true,false));
                for(var e:names.entrySet()){
                    check(Path.of(e.getValue()).getNameCount()==e.getKey().input.relativePath().getNameCount(),"hierarchy depth retained");check(!e.getValue().contains("Synthetic"),"neutral names");
                    AttributeList l=DicomIO.read(out.resolve(e.getValue())).list();check(Policy.value(l,0x00120062).equals("YES"),"reviewed export marked");
                }
                String report=Files.readString(out.resolve("BNEL-Review-Report.txt"));check(!report.contains("TESTMRN")&&!report.contains(input.toString()),"report contains no PHI");
                expect(java.io.IOException.class,()->Cleaner.export(batch,out,new Cleaner.ExportOptions(false,false,true,false),PROGRESS,()->false),"do not overwrite");
                Path zip=Cleaner.export(batch,root.resolve("export.zip"),new Cleaner.ExportOptions(true,true,true,false),PROGRESS,()->false);
                try(ZipFile z=new ZipFile(zip.toFile())){check(z.getEntry("DICOMDIR")!=null&&z.size()==5,"ZIP has all files, index and report");}
                expect(CancellationException.class,()->Cleaner.export(batch,root.resolve("cancelled"),new Cleaner.ExportOptions(false,false,true,false),PROGRESS,()->true),"cancelled export");check(!Files.exists(root.resolve("cancelled")),"no partial destination");
                Cleaner.Output target=batch.outputs.stream().filter(o->o.input.modality().equals("CT")).findFirst().orElseThrow();String before=DicomIO.pixels(DicomIO.read(target.file).list()).toString();
                Cleaner.redact(target,List.of(new Rectangle2D.Double(0,0,8,8)));check(target.redacted&&!target.reviewed,"blackout invalidates approval");check(!DicomIO.pixels(DicomIO.read(target.file).list()).toString().equals(before),"blackout alters actual exported pixels");
            }
            expect(java.io.IOException.class,()->Cleaner.prepare(scan.instances(),new Policy.Options("Synthetic^Zebra","BNEL-001",Policy.DateMode.FIXED,LocalDate.of(2000,1,1)),"",PROGRESS,()->false),"replacement must not repeat original identifiers");
            expect(IllegalArgumentException.class,()->Cleaner.prepare(List.of(scan.instances().get(0),scan.instances().get(0)),OPTIONS,"",PROGRESS,()->false),"duplicate SOP rejection");
            expect(IllegalArgumentException.class,()->Cleaner.prepare(scan.instances(),new Policy.Options("Research^001","BNEL-001",Policy.DateMode.SHIFT,LocalDate.of(2000,1,1)),"",PROGRESS,()->false),"shifting requires exception");
            try(Cleaner.Batch shifted=Cleaner.prepare(scan.instances(),new Policy.Options("Research^001","BNEL-001",Policy.DateMode.SHIFT,LocalDate.of(2000,1,1)),"synthetic test exception",PROGRESS,()->false)){
                Cleaner.Output c=shifted.outputs.stream().filter(o->o.input.modality().equals("CT")).findFirst().orElseThrow();AttributeList l=DicomIO.read(c.file).list();check(Policy.value(l,0x00080021).equals("20000102"),"shift preserves date interval");check(Policy.value(l,0x00080030).equals("124522.123456"),"shift preserves dynamic timing");
            }
            MetadataUnion.Index union=MetadataUnion.compute(scan.instances().stream().map(i->new MetadataUnion.FileEntry(i.source(),i.source().getFileName().toString())).toList(),()->false);
            check(union.tags().stream().anyMatch(t->t.name().equals("StudyComments")&&t.values().size()==1),"union includes tag found in only one file");
            IdentifierPolicy custom=new IdentifierPolicy(Map.of(IdentifierPolicy.Category.INSTITUTION,new IdentifierPolicy.Rule(IdentifierPolicy.Mode.CUSTOM,"SITE-RESEARCH"),IdentifierPolicy.Category.DEVICE,new IdentifierPolicy.Rule(IdentifierPolicy.Mode.RANDOM,""),IdentifierPolicy.Category.PERSON,new IdentifierPolicy.Rule(IdentifierPolicy.Mode.DEFAULT,"")));
            Map<String,ScanLabels.Labels> labels=Map.of("2.25.1000032345678902",new ScanLabels.Labels("T1-weighted","MPRAGE","tfl3d1"));
            try(Cleaner.Batch minimum=Cleaner.prepare(scan.instances(),new Policy.Options("Research^001","BNEL-001",Policy.DateMode.FIXED,LocalDate.of(2000,1,1),true),"",custom,labels,PROGRESS,()->false)){
                for(Cleaner.Output o:minimum.outputs)if(!DicomIO.isRegistration(o.input.sopClass())){
                    AttributeList l=DicomIO.read(o.file).list();check(Policy.value(l,0x00180022).equals("FS"),"scan options retained");check(Policy.value(l,0x00180023).equals("3D"),"MR acquisition type retained");
                    check(Policy.value(l,0x00180080).equals("2000")&&Policy.value(l,0x00180081).equals("3.5"),"TR TE retained");check(Policy.value(l,0x001021a0).isEmpty(),"smoking status removed");
                    check(Policy.value(l,0x00080070).startsWith("R")&&!Policy.value(l,0x00080070).contains("Synthetic"),"random device placeholder");
                    if(o.input.modality().equals("MR")){check(Policy.value(l,0x0008103e).equals("T1-weighted"),"reviewed scan classification kept");check(Policy.value(l,0x00180024).equals("tfl3d1"),"reviewed sequence name kept");}
                }
            }
            Map<String,ScanLabels.Labels> unsafeLabel=Map.of("2.25.1000032345678902",new ScanLabels.Labels("T1 TESTMRN778899","",""));
            expect(java.io.IOException.class,()->Cleaner.prepare(scan.instances(),OPTIONS,"",new IdentifierPolicy(),unsafeLabel,PROGRESS,()->false),"residual check rejects identifier in reviewed label");
            AttributeList reportDoc=reportFixture();try(ReportPreview preview=ReportPreview.open(reportDoc)){
                check(preview.pages()==2,"embedded PDF page count");check(preview.page(0).getWidth()>500&&preview.page(1).getHeight()>500,"PDF pages render within app");
            }
            Path disposalRoot=root.resolve("disposal"),first=disposalRoot.resolve("first.dcm"),second=disposalRoot.resolve("second.dcm");write(image(1,false),first);write(image(2,true),second);
            List<Cleaner.Instance> disposal=Cleaner.scan(List.of(disposalRoot),PROGRESS,()->false).instances();
            Path fakeBin=root.resolve("test-recycle-bin");Files.createDirectory(fakeBin);
            FileDisposal.Result removed=FileDisposal.recycle(List.of(disposal.get(0)),p->{Files.move(p,fakeBin.resolve(p.getFileName()));return true;});
            check(removed.moved().size()==1&&removed.failed().isEmpty(),"only selected original moved");check(Files.exists(second)&&Files.exists(fakeBin.resolve("first.dcm")),"other files retained and moved file recoverable");
            FileDisposal.Result refused=FileDisposal.recycle(List.of(disposal.get(1)),p->false);check(refused.failed().size()==1&&Files.exists(second),"no permanent deletion when trash refuses");
            Files.writeString(second,"changed after import",StandardOpenOption.APPEND);
            final boolean[] called={false};FileDisposal.Result changed=FileDisposal.recycle(List.of(disposal.get(1)),p->{called[0]=true;return true;});check(!called[0]&&changed.failed().size()==1&&Files.exists(second),"changed source cannot be deleted by stale selection");
            if(System.getProperty("os.name").startsWith("Windows")){
                check(NativeRecycle.AVAILABLE,"bundled native recycler loads");
                Path nativeFile=root.resolve("native-recycle.dcm");write(image(1,false),nativeFile);String hash=DicomIO.hash(nativeFile);
                String recycled=NativeRecycle.move(nativeFile.toString());
                check(recycled!=null&&!Files.exists(nativeFile)&&Files.exists(Path.of(recycled)),"Windows Shell returns an actual recoverable Recycle Bin item");
                check(hash.equals(DicomIO.hash(Path.of(recycled))),"recycled original bytes intact");
                check(NativeRecycle.move(root.toString())==null&&Files.exists(root),"recycler refuses directories");
                check(NativeRecycle.move("\\\\unavailable-bnel-test\\share\\file.dcm")==null,"recycler refuses network paths");
            }
            // Changes after import must invalidate the snapshot.
            Path compressed=root.resolve("compressed/jpeg.dcm");AttributeList jpeg=image(7,false);
            value(jpeg,0x00080016,SOPClass.SecondaryCaptureImageStorage);number(jpeg,0x00280100,8);number(jpeg,0x00280101,8);number(jpeg,0x00280102,7);
            java.awt.image.BufferedImage gray=new java.awt.image.BufferedImage(32,32,java.awt.image.BufferedImage.TYPE_BYTE_GRAY);
            for(int y=0;y<32;y++)for(int x=0;x<32;x++)gray.getRaster().setSample(x,y,0,(x+y)*4);
            java.io.ByteArrayOutputStream encoded=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(gray,"jpeg",encoded);byte[] frame=encoded.toByteArray();
            PixelSafety.inspectJpeg(frame);
            jpeg.put(new OtherByteAttributeMultipleCompressedFrames(TagFromName.PixelData,new byte[][]{frame}));Files.createDirectories(compressed.getParent());DicomIO.write(jpeg,compressed,TransferSyntax.JPEGBaseline);
            Cleaner.Scan compressedScan=Cleaner.scan(List.of(compressed),PROGRESS,()->false);
            try(Cleaner.Batch b=Cleaner.prepare(compressedScan.instances(),OPTIONS,"",PROGRESS,()->false)){
                DicomIO.Loaded l=DicomIO.read(b.outputs.get(0).file);check(l.syntax().equals(TransferSyntax.JPEGBaseline),"compressed transfer syntax preserved");
                com.pixelmed.display.SourceImage img=new com.pixelmed.display.SourceImage(l.list());try{check(img.getBufferedImage(0)!=null,"bundled JPEG decoder");}finally{try{img.close();}catch(Throwable e){throw new Exception(e);}}
            }
            byte[] text="patient TESTMRN778899".getBytes(StandardCharsets.US_ASCII);java.io.ByteArrayOutputStream injected=new java.io.ByteArrayOutputStream();injected.write(frame,0,2);injected.write(255);injected.write(254);injected.write((text.length+2)>>8);injected.write((text.length+2)&255);injected.write(text);injected.write(frame,2,frame.length-2);
            expect(java.io.IOException.class,()->PixelSafety.inspectJpeg(injected.toByteArray()),"reject PHI in a JPEG comment inside PixelData");
            expect(java.io.IOException.class,()->PixelSafety.inspectJpeg(new byte[]{(byte)255,79,0,0}),"uninspected compressed format blocked");
            write(image(99,false),ct);expect(java.io.IOException.class,()->Cleaner.prepare(scan.instances(),OPTIONS,"",PROGRESS,()->false),"reject modified source");
            Path settingsDir=root.resolve("settings");System.setProperty("bnel.settingsDir",settingsDir.toString());AppSettings a=new AppSettings();a.set("dicomdir",true);a.set("width",1440);a.save();AppSettings b=new AppSettings();check(b.bool("dicomdir",false)&&b.integer("width",0)==1440,"settings remembered");check(!Files.readString(b.path).contains("TESTMRN"),"no identifiers in settings");
            System.out.println("PASS: "+assertions+" assertions (synthetic CT/MR/REG, mixed text, references, redaction, folder/ZIP/DICOMDIR, cancellation, settings)");
        }finally{DicomIO.deleteTree(root);}
    }
}
