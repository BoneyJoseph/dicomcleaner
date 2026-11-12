package org.bnel.dicom;

import com.pixelmed.dicom.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.tree.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class Main extends JFrame {
    static final String CREDIT="Boney Joseph, M.B.B.S. | Bioelectronics Neurophysiology and Engineering Lab";
    final AppSettings settings=new AppSettings();
    final List<Cleaner.Instance> instances=new ArrayList<>();
    final SourceTree tree=new SourceTree();
    final MetadataPanel original=new MetadataPanel(), cleaned=new MetadataPanel();
    final MetadataUnion allOriginal=new MetadataUnion(),allCleaned=new MetadataUnion();
    final MetadataCompare allMetadata=new MetadataCompare(allOriginal,allCleaned);
    final JToggleButton linkedIdentity=new JToggleButton("Linked",true);
    boolean syncingIdentity;
    final JTabbedPane metadataTabs=new JTabbedPane();
    final JLabel originalName=new JLabel("Select any individual DICOM instance"), cleanedName=new JLabel("Clean a selection to compare"),
        status=new JLabel(" "), counts=new JLabel("No files imported"), percent=new JLabel(" ");
    final JTextField patientName=new JTextField("",14),patientId=new JTextField("",14),date=new JTextField("2000-01-01",10);
    final JCheckBox minimal=new JCheckBox("Essential research metadata");
    final JCheckBox siemens=new JCheckBox("Keep Siemens diffusion/fMRI tags");
    final JCheckBox hierarchy=new JCheckBox("Preserve imported hierarchy",true),originalNames=new JCheckBox("Retain original folder/file names"),
        zip=new JCheckBox("ZIP export"),dicomdir=new JCheckBox("Include DICOMDIR");
    final JComboBox<String> dateMode=new JComboBox<>(new String[]{"Fixed date","Random per folder"});
    final Map<Path,LocalDate> folderDates=new HashMap<>();
    final JProgressBar progress=new JProgressBar(0,100);
    final JButton importFolder,addFiles,remove,clean,review,export,cancel,differences,clear,scanLabels,identifiers;
    final RatioSplit outerSplit,metadataSplit;
    final SettingsStrip settingsStrip;
    final SourceBrowser sourceBrowser;
    final StatusFooter statusFooter;
    Cleaner.Batch batch;
    List<Cleaner.Instance> selection=List.of();
    Cleaner.Instance current;
    SwingWorker<?,?> active;
    final AtomicBoolean cancellation=new AtomicBoolean();
    long displayGeneration;
    long unionGeneration,cleanUnionGeneration;
    IdentifierPolicy identifierPolicy=IdentifierPolicy.load(settings);
    Map<String,ScanLabels.Labels> labels=new HashMap<>();
    boolean initializing=true;

    public Main(){
        super("BNEL Dicom Cleaner");setName("BNEL Dicom Cleaner");setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(Theme.u(1024),Theme.u(680)));setResizable(true);Theme.installWindow(this);
        Dimension screen=Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(settings.integer("width",Theme.u(1600)),screen.width),Math.min(settings.integer("height",Theme.u(1000)),screen.height-30));
        setLocationRelativeTo(null);
        Theme.Background body=new Theme.Background(new BorderLayout(Theme.u(12),Theme.u(12)));body.setBorder(BorderFactory.createEmptyBorder(Theme.u(12),Theme.u(12),0,Theme.u(12)));setContentPane(body);
        sourceBrowser=new SourceBrowser(tree,false);
        sourceBrowser.setMinimumSize(new Dimension(Theme.u(176),180));
        JPanel op=new JPanel(new BorderLayout(0,8));op.setOpaque(false);op.add(originalName,BorderLayout.NORTH);op.add(original);
        JPanel cp=new JPanel(new BorderLayout(0,8));cp.setOpaque(false);cp.add(cleanedName,BorderLayout.NORTH);cp.add(cleaned);
        metadataSplit=new RatioSplit(JSplitPane.HORIZONTAL_SPLIT,Theme.card("Original",op),Theme.card("Cleaned",cp),.5);metadataSplit.setResizeWeight(.5);metadataSplit.setBorder(null);metadataSplit.setContinuousLayout(true);
        metadataSplit.getLeftComponent().setMinimumSize(new Dimension(Theme.u(240),160));metadataSplit.getRightComponent().setMinimumSize(new Dimension(Theme.u(240),160));
        metadataSplit.restore(settings,"layout.instance.ratio");
        ((RatioSplit)allMetadata.split).restore(settings,"layout.aggregate.ratio");
        ((RatioSplit)allOriginal.split).restore(settings,"layout.detail.ratio");((RatioSplit)allCleaned.split).restore(settings,"layout.detail.ratio");
        metadataTabs.addTab("All metadata",allMetadata);metadataTabs.addTab("Instance comparison",metadataSplit);
        JPanel navigation=Theme.navigation(null,sourceBrowser);navigation.setMinimumSize(new Dimension(Theme.u(200),180));
        outerSplit=new RatioSplit(JSplitPane.HORIZONTAL_SPLIT,navigation,metadataTabs,.255);outerSplit.restore(settings,"layout.source.ratio");
        body.add(outerSplit,BorderLayout.CENTER);
        JPanel lower=new JPanel();lower.setOpaque(false);lower.setLayout(new BoxLayout(lower,BoxLayout.Y_AXIS));
        importFolder=Theme.button("Import folder",false,()->chooseImport(true));addFiles=Theme.button("Add files",false,()->chooseImport(false));
        remove=Theme.button("Remove selected",false,this::removeSelection);clean=Theme.button("Clean selected",true,this::cleanSelection);
        review=Theme.button("Review images",false,this::reviewImages);export=Theme.button("Export cleaned",false,this::exportBatch);
        cancel=Theme.button("Cancel",false,()->{cancellation.set(true);status.setText("Cancelling safely…");});
        cancel.setToolTipText("Stop the current import, cleaning or export operation.");
        clear=Theme.button("Clear session",false,this::clearSession);differences=Theme.button("Compare files",false,this::showDifferences);
        scanLabels=Theme.button("Scan labels…",false,this::scanLabelsDialog);identifiers=Theme.button("Identifier rules…",false,this::identifierDialog);
        Theme.equalButtons(36,importFolder,addFiles,remove);Theme.equalButtons(36,clean,export);Theme.equalButtons(36,review);Theme.equalButtons(36,clear);
        JPanel actions=Theme.bar(Theme.line(importFolder,addFiles,remove,review),Theme.line(clean,export,clear));actions.setName("Main commands");actions.setBorder(BorderFactory.createEmptyBorder(12,0,12,0));
        settingsStrip=new SettingsStrip(this);lower.add(settingsStrip);lower.add(actions);
        statusFooter=new StatusFooter(status,percent,progress,cancel);lower.add(statusFooter);body.add(lower,BorderLayout.SOUTH);
        hierarchy.setSelected(settings.bool("hierarchy",true));originalNames.setSelected(settings.bool("originalNames",false));zip.setSelected(settings.bool("zip",false));dicomdir.setSelected(settings.bool("dicomdir",false));
        dateMode.setSelectedIndex(settings.integer("dateMode",0)==1?1:0);date.setText(settings.text("date","2000-01-01"));
        minimal.setOpaque(false);minimal.setSelected(settings.bool("minimal",true));minimal.setToolTipText("<html><b>Additional removal beyond the default cleaner</b><br>Uses an allowlist for classic CT/MR and registration objects.<br>Also drops optional tags outside that list, including extra acquisition,<br>workflow, presentation and descriptive metadata—even if not identifying.<br>Examples: Acquisition Duration (0018,9073), Exposure in µAs<br>(0018,1153), and Detector Temperature (0018,7001).<br>Keeps pixels, dimensions, geometry, registration matrices, scan options,<br>MR acquisition type, key timing parameters and your chosen scan labels.<br>Enhanced/secondary-capture images use the broader cleaner to preserve<br>their required structures. This does not inspect or change image pixels.</html>");
        minimal.addActionListener(e->{invalidateBatch();persist(false);});
        siemens.setOpaque(false);siemens.setSelected(settings.bool("siemensDiffusion",false));
        siemens.setToolTipText("<html><b>Keep a short, vetted list of Siemens private tags</b><br>Needed to analyse classic Siemens DWI and fMRI (e.g. with dcm2niix):<br>b-value, gradient direction, B-matrix, mosaic layout, slice timing<br>and the CSA image header. All other private tags are still removed.<br>The CSA series header (full protocol text) is never kept. Text inside<br>kept tags is scanned by the identifier check. Off by default.</html>");
        siemens.addActionListener(e->{invalidateBatch();persist(false);});
        hierarchy.setToolTipText("Preserve the imported folder nesting in the export.");
        originalNames.setToolTipText(ActionHelp.tip("Keep the original folder and file names; leave off to use neutral names while preserving nesting."));
        zip.setToolTipText("Export the cleaned folder tree as a ZIP archive.");
        dicomdir.setToolTipText("Include a DICOMDIR index for compatible DICOM media readers.");
        dateMode.setToolTipText("Use one fixed date, or one random synthetic date per imported folder.");
        date.setToolTipText("Synthetic date for fixed-date mode, in YYYY-MM-DD format.");

        patientName.setToolTipText("Replacement name for the selected folders");patientId.setToolTipText("Replacement ID for the selected folders");
        for(JCheckBox c:new JCheckBox[]{hierarchy,originalNames,zip,dicomdir}){c.setOpaque(false);c.addActionListener(e->{originalNames.setEnabled(hierarchy.isSelected()&&active==null);persist(false);});}
        tree.addTreeSelectionListener(e->{
            if(tree.filtering)return;
            selection=Arrays.stream(tree.getSelectedIndices()).mapToObj(instances::get).toList();
            if(!selection.isEmpty())display(selection.get(0));updateActions();
        });
        original.removeTag=this::addTagRule;cleaned.removeTag=this::addTagRule;
        linkedIdentity.setIcon(new UiIcons("link"));linkedIdentity.setIconTextGap(5);linkedIdentity.setName("Link replacement identity");linkedIdentity.setToolTipText("Mirror Patient Name and Patient ID while linked. Turn off for different values. Values are not saved between sessions.");
        linkedIdentity.addActionListener(e->{if(linkedIdentity.isSelected()){syncingIdentity=true;patientId.setText(patientName.getText());syncingIdentity=false;invalidateBatch();}});
        DocumentListener changed=new DocumentListener(){public void insertUpdate(DocumentEvent e){changed(e);}public void removeUpdate(DocumentEvent e){changed(e);}public void changedUpdate(DocumentEvent e){changed(e);}
            void changed(DocumentEvent e){if(initializing||syncingIdentity)return;
                if(linkedIdentity.isSelected()&&(e.getDocument()==patientName.getDocument()||e.getDocument()==patientId.getDocument())){
                    JTextField source=e.getDocument()==patientName.getDocument()?patientName:patientId,target=source==patientName?patientId:patientName;
                    syncingIdentity=true;try{target.setText(source.getText());}finally{syncingIdentity=false;}
                }
                invalidateBatch();persist(false);
            }};
        patientName.getDocument().addDocumentListener(changed);patientId.getDocument().addDocumentListener(changed);date.getDocument().addDocumentListener(changed);
        dateMode.addActionListener(e->{if(!initializing){invalidateBatch();persist(false);updateActions();}});
        TransferHandler drop=new TransferHandler(){
            public boolean canImport(TransferSupport support){return active==null&&support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);}
            public boolean importData(TransferSupport support){
                if(!canImport(support))return false;
                try { @SuppressWarnings("unchecked") List<File> files=(List<File>)support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);importPaths(files.stream().map(File::toPath).toList());return true; }
                catch(Exception e){Theme.error(Main.this,e);return false;}
            }
        };tree.setTransferHandler(drop);body.setTransferHandler(drop);
        addWindowListener(new WindowAdapter(){@Override public void windowClosing(WindowEvent e){closeWindow();}});
        settingsStrip.summary();
        body.addComponentListener(new ComponentAdapter(){public void componentResized(ComponentEvent e){
            int width=body.getWidth()-Theme.u(24);boolean compact=settingsStrip.compact?width<settingsStrip.wideMinimum+Theme.u(40):width<settingsStrip.wideMinimum;
            if(compact!=settingsStrip.compact){settingsStrip.arrange(compact);statusFooter.arrange(compact);}
            remove.setText(compact?"Remove":"Remove selected");clear.setText(compact?"Clear":"Clear session");
            for(JButton b:new JButton[]{importFolder,addFiles,remove,clear}){b.setPreferredSize(null);b.setMinimumSize(null);b.setMaximumSize(null);}
            Theme.equalButtons(36,importFolder,addFiles,remove);Theme.equalButtons(36,clear);actions.revalidate();
        }});

        JPopupMenu layoutMenu=new JPopupMenu();JMenuItem resetLayout=new JMenuItem("Reset layout");resetLayout.addActionListener(e->{outerSplit.ratio=.255;metadataSplit.ratio=.5;((RatioSplit)allMetadata.split).ratio=.5;((RatioSplit)allOriginal.split).ratio=.75;((RatioSplit)allCleaned.split).ratio=.75;body.revalidate();});layoutMenu.add(resetLayout);actions.setComponentPopupMenu(layoutMenu);
        initializing=false;updateActions();
    }
    static JPanel field(String title,Component... controls){JPanel p=new JPanel(new FlowLayout(FlowLayout.LEFT,6,0));p.setOpaque(false);p.add(new JLabel(title));for(Component c:controls)p.add(c);return p;}
    static JPanel optionRow(String title,Component... controls){JPanel p=new JPanel(new BorderLayout(8,0));p.setOpaque(false);JLabel label=Theme.label(title,12,true);label.setPreferredSize(new Dimension(72,28));label.setBorder(BorderFactory.createEmptyBorder(0,2,0,0));p.add(label,BorderLayout.WEST);p.add(Theme.row(controls));return p;}
    void updateActions(){
        boolean idle=active==null;
        for(JButton b:new JButton[]{importFolder,addFiles,clear,identifiers})b.setEnabled(idle);
        scanLabels.setEnabled(idle&&!instances.isEmpty());
        remove.setEnabled(idle&&!selection.isEmpty());clean.setEnabled(idle&&!selection.isEmpty());
        review.setEnabled(idle&&(batch!=null||current!=null));export.setEnabled(idle&&batch!=null&&batch.exportReady());cancel.setEnabled(!idle);if(cancel.isVisible()==idle){cancel.setVisible(!idle);Theme.reflow((JComponent)getContentPane());}
        differences.setEnabled(idle&&instances.size()>1);
        for(JComponent c:new JComponent[]{patientName,patientId,linkedIdentity,dateMode,hierarchy,zip,dicomdir,minimal,siemens})c.setEnabled(idle);
        date.setEnabled(idle&&dateMode.getSelectedIndex()==0);originalNames.setEnabled(idle&&hierarchy.isSelected());
    }
    void chooseImport(boolean folder){
        JFileChooser c=new JFileChooser();c.setDialogTitle(folder?"Import a DICOM folder":"Add DICOM files");
        c.setFileSelectionMode(folder?JFileChooser.DIRECTORIES_ONLY:JFileChooser.FILES_ONLY);c.setMultiSelectionEnabled(true);
        if(c.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        importPaths(Arrays.stream(c.getSelectedFiles()).map(File::toPath).toList());
    }
    void importPaths(List<Path> paths){
        linkedIdentity.setSelected(true);patientName.setText("");patientId.setText("");labels.clear();
        invalidateBatch();runTask("Importing",()->Cleaner.scan(paths,this::progress,cancellation::get),scan->{
            Set<Path> existing=new HashSet<>();for(Cleaner.Instance i:instances)existing.add(i.source());
            for(Cleaner.Instance i:scan.instances())if(existing.add(i.source())){instances.add(i);folderDates.computeIfAbsent(i.importRoot(),k->LocalDate.of(2000,1,1).plusDays(new java.security.SecureRandom().nextInt(36525)));}
            rebuildTree();refreshUnion(false);metadataTabs.setSelectedIndex(0);status.setText("Imported "+scan.instances().size()+" instances; enter a replacement name and ID");
            if(!scan.issues().isEmpty())showText("Import issues — inspect these files",String.join("\n",scan.issues()));
        });
    }
    void rebuildTree(){
        tree.setInstances(instances);
        long subjects=instances.stream().map(Cleaner.Instance::patientKey).distinct().count();
        counts.setText(subjects+" subject group(s) · "+instances.size()+" files");
        if(!instances.isEmpty())tree.setSelectedIndices(java.util.stream.IntStream.range(0,instances.size()).toArray());
        else {selection=List.of();current=null;original.setData(null);cleaned.setData(null);}
        updateActions();
    }
    Cleaner.Output outputFor(Cleaner.Instance i){return batch==null?null:batch.outputs.stream().filter(o->o.input.source().equals(i.source())).findFirst().orElse(null);}
    void display(Cleaner.Instance instance){
        current=instance;long generation=++displayGeneration;Cleaner.Output output=outputFor(instance);
        originalName.setText(instance.source().getFileName().toString());originalName.setToolTipText(instance.source()+"\n"+instance.problem());
        cleanedName.setText(output==null?"Not cleaned":output.reviewed?"Reviewed instance":"Metadata cleaned · ready to export");
        new SwingWorker<AttributeList[],Void>(){
            protected AttributeList[] doInBackground()throws Exception{return new AttributeList[]{DicomIO.read(instance.source()).list(),output==null?null:DicomIO.read(output.file).list()};}
            protected void done(){if(generation!=displayGeneration)return;try{AttributeList[] lists=get();original.setData(lists[0]);cleaned.setData(lists[1]);}catch(Exception e){status.setText("Could not read this instance; reimport if it changed.");original.setData(null);cleaned.setData(null);}}
        }.execute();
    }
    void invalidateBatch(){
        displayGeneration++;cleanUnionGeneration++;allCleaned.setIndex(new MetadataUnion.Index(List.of(),List.of()));
        if(batch==null)return;Cleaner.Batch old=batch;batch=null;cleaned.setData(null);cleanedName.setText("Settings/files changed — clean again");
        new SwingWorker<Void,Void>(){protected Void doInBackground()throws Exception{old.close();return null;}}.execute();updateActions();
    }
    void removeSelection(){instances.removeAll(selection);labels.clear();invalidateBatch();rebuildTree();refreshUnion(false);}
    void clearSession(){invalidateBatch();instances.clear();labels.clear();folderDates.clear();selection=List.of();patientName.setText("");patientId.setText("");rebuildTree();refreshUnion(false);progress.setValue(0);percent.setText(" ");status.setText(" ");}
    void cleanSelection(){
        try{
            Policy.Options options=new Policy.Options(patientName.getText(),patientId.getText(),dateMode.getSelectedIndex()==0?Policy.DateMode.FIXED:Policy.DateMode.RANDOM,LocalDate.parse(date.getText().strip()),minimal.isSelected(),folderDates,siemens.isSelected());
            List<Cleaner.Instance> chosen=List.copyOf(selection),imported=List.copyOf(instances);invalidateBatch();
            IdentifierPolicy selectedPolicy=identifierPolicy;Map<String,ScanLabels.Labels> selectedLabels=Map.copyOf(labels);
            runTask("Cleaning metadata",()->Cleaner.prepare(chosen,options,"",selectedPolicy,selectedLabels,imported,this::progress,cancellation::get),result->{batch=result;if(current!=null)display(current);refreshUnion(true);metadataTabs.setSelectedIndex(0);status.setText(batch.outputs.size()+" files cleaned · "+batch.skipped.size()+" skipped · "+(batch.identifierFindings.isEmpty()?"":batch.identifierFindings.size()+" identifier warning(s) · ")+(batch.outputs.isEmpty()?"No files to export":"Ready to export"));
                if(!batch.identifierFindings.isEmpty()||!batch.skipped.isEmpty()||!batch.warnings.isEmpty()||!batch.referenceDetails.isEmpty())showText("Cleaning results",cleaningResults(batch));});

        }catch(Exception e){Theme.error(this,e);}
    }
    void reviewImages(){
        if(instances.isEmpty())return;
        ReviewDialog dialog=batch!=null&&(current==null||outputFor(current)!=null)?new ReviewDialog(this,batch):new ReviewDialog(this,List.copyOf(instances),current);dialog.showReview();
        if(!dialog.excluded.isEmpty()||(batch!=null&&batch.requiresReclean)){
            instances.removeIf(i->dialog.excluded.contains(i.source()));labels.clear();invalidateBatch();rebuildTree();refreshUnion(false);
            status.setText(dialog.excluded.size()+" files removed from this session; clean the remaining selection again");
        }else{if(current!=null)display(current);refreshUnion(true);}updateActions();
    }
    void exportBatch(){
        if(batch==null)return;
        JFileChooser c=new JFileChooser();c.setDialogTitle("Choose a NEW output "+(zip.isSelected()?"ZIP file":"folder name"));c.setFileSelectionMode(JFileChooser.FILES_ONLY);
        c.setSelectedFile(new File("BNEL-Export-"+System.currentTimeMillis()+(zip.isSelected()?".zip":"")));
        if(c.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        Cleaner.ExportOptions options=new Cleaner.ExportOptions(zip.isSelected(),dicomdir.isSelected(),hierarchy.isSelected(),originalNames.isSelected());
        Cleaner.Batch selected=batch;Path target=c.getSelectedFile().toPath();
        runTask("Exporting",()->Cleaner.export(selected,target,options,this::progress,cancellation::get),path->{
            status.setText("Export complete: "+path.getFileName());

        });
    }
    <T> void runTask(String title,Callable<T> task,Consumer<T> success){
        if(active!=null)return;cancellation.set(false);status.setText(title);progress.setValue(0);percent.setText(" ");
        SwingWorker<T,Void> worker=new SwingWorker<>(){
            protected T doInBackground()throws Exception{return task.call();}
            protected void done(){active=null;try{success.accept(get());}catch(Exception e){Throwable cause=e instanceof ExecutionException?e.getCause():e;if(cause instanceof CancellationException)status.setText("Cancelled — no source files changed");else{status.setText("Operation stopped — inspect the message");Theme.error(Main.this,cause);}}finally{updateActions();}}
        };active=worker;updateActions();worker.execute();
    }
    void progress(String stage,int done,int total){SwingUtilities.invokeLater(()->{status.setText(stage);int p=total==0?0:(int)(done*100L/total);progress.setValue(p);percent.setText(done+" / "+total+" · "+p+"%");});}
    void persist(boolean reportErrors){
        settings.set("width",getWidth());settings.set("height",getHeight());outerSplit.remember(settings,"layout.source.ratio");metadataSplit.remember(settings,"layout.instance.ratio");((RatioSplit)allMetadata.split).remember(settings,"layout.aggregate.ratio");((RatioSplit)allOriginal.split).remember(settings,"layout.detail.ratio");
        settings.set("hierarchy",hierarchy.isSelected());settings.set("originalNames",originalNames.isSelected());settings.set("zip",zip.isSelected());settings.set("dicomdir",dicomdir.isSelected());settings.values.remove("profile");settings.set("dateMode",dateMode.getSelectedIndex());settings.set("date",date.getText());
        settings.set("minimal",minimal.isSelected());settings.set("siemensDiffusion",siemens.isSelected());identifierPolicy.save(settings);
        try{settings.save();}catch(IOException e){if(reportErrors)Theme.error(this,new IOException("Preferences could not be saved. Extract the app to a writable folder."));}
    }
    void closeWindow(){
        if(active!=null){cancellation.set(true);status.setText("Cancelling — close again when processing stops");return;}
        persist(true);if(batch!=null)try{batch.close();}catch(IOException e){Theme.error(this,new IOException("Some temporary files could not be removed. Close other viewers and clear this session."));return;}
        dispose();
    }
    static String cleaningResults(Cleaner.Batch batch){
        StringBuilder text=new StringBuilder(batch.outputs.size()+" files cleaned · "+batch.skipped.size()+" skipped\n");
        if(!batch.identifierFindings.isEmpty()){
            text.append("\nPossible identifiers in retained metadata (warning only; export is not blocked).\nEdit the series label (Scan labels) or add a tag-removal rule, then clean again:\n");
            Map<String,List<ResidualGuard.Finding>> bySeries=new LinkedHashMap<>();
            for(ResidualGuard.Finding f:batch.identifierFindings)bySeries.computeIfAbsent(f.series(),k->new ArrayList<>()).add(f);
            bySeries.forEach((series,list)->{text.append("\n  ").append(series).append('\n');for(ResidualGuard.Finding f:list)text.append("    • ").append(f).append('\n');});
        }
        if(!batch.skipped.isEmpty())text.append("\nSkipped files are not included in export:\n").append(String.join("\n",batch.skipped)).append('\n');
        if(!batch.warnings.isEmpty())text.append("\nProcessing notes:\n").append(String.join("\n",batch.warnings)).append('\n');
        if(!batch.referenceDetails.isEmpty()){
            text.append("\n").append(batch.referenceDetails.size()).append(batch.referenceDetails.size()==1?" referenced instance is":" referenced instances are").append(" outside this export.\n");
            text.append("An included file contains a link to another DICOM instance that is not included. The image itself is still exported. Details below identify the source of the link and the target where known.\n");
            for(Cleaner.ReferenceDetail reference:batch.referenceDetails){
                text.append("\nReferenced SOP Instance UID (original): ").append(reference.originalUid()).append("\nStatus: ").append(reference.status()).append('\n');
                for(Path target:reference.knownTargets())text.append("Referenced file: ").append(target).append('\n');
                for(Cleaner.Referrer source:reference.referringFiles())text.append("Link found in: ").append(source.source()).append("\n  Tag / sequence path: ").append(source.tagPath()).append('\n');
            }
        }return text.toString();
    }
    void showText(String title,String text){
        JDialog dialog=new JDialog(this,title,Dialog.ModalityType.APPLICATION_MODAL);Theme.installWindow(dialog);
        JTextArea area=new JTextArea(text);area.setEditable(false);area.setLineWrap(true);area.setWrapStyleWord(true);area.setMargin(new Insets(12,12,12,12));area.setCaretPosition(0);dialog.add(Theme.scroll(area));
        dialog.add(Theme.footer(dialog,Theme.row(Theme.button("Copy details",false,()->Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new java.awt.datatransfer.StringSelection(area.getText()),null)),Theme.button("Close",true,dialog::dispose))),BorderLayout.SOUTH);
        int lines=text.split("\\R").length;dialog.setSize(Math.min(1000,getWidth()-40),Math.min(660,Math.max(220,120+lines*18)));dialog.setMinimumSize(new Dimension(600,220));dialog.setLocationRelativeTo(this);dialog.setVisible(true);
    }
    void showDifferences(){
        List<Cleaner.Instance> chosen=selection.size()>1?List.copyOf(selection):List.copyOf(instances);
        runTask("Comparing every instance",()->MetadataDifferences.compute(chosen,this::progress,cancellation::get),result->MetadataDifferences.show(this,chosen,result));
    }
    void addTagRule(int tag){try{identifierPolicy=identifierPolicy.withRemovedTag(tag);invalidateBatch();persist(true);status.setText(String.format("Tag (%04X,%04X) will be removed from future cleaning jobs",tag>>>16,tag&65535));}catch(Exception e){Theme.error(this,e);}}
    void identifierDialog(){IdentifierPolicy changed=IdentifierPolicy.edit(this,identifierPolicy);if(changed!=null){identifierPolicy=changed;invalidateBatch();persist(true);}}
    void scanLabelsDialog(){List<Cleaner.Instance> all=List.copyOf(instances);runTask("Reading scan labels",()->ScanLabels.collect(all,this::progress,cancellation::get),series->{Map<String,ScanLabels.Labels> changed=ScanLabels.edit(this,series,labels);if(changed!=null){labels=changed;invalidateBatch();}});}
    void refreshUnion(boolean cleanedIndex){
        long generation=cleanedIndex?++cleanUnionGeneration:++unionGeneration;
        List<MetadataUnion.FileEntry> files=cleanedIndex?(batch==null?List.of():batch.outputs.stream().map(o->new MetadataUnion.FileEntry(o.file,o.input.relativePath().toString())).toList()):instances.stream().map(i->new MetadataUnion.FileEntry(i.source(),i.relativePath().toString())).toList();
        MetadataUnion panel=cleanedIndex?allCleaned:allOriginal;panel.status.setText("Indexing every file and nested metadata path…");
        new SwingWorker<MetadataUnion.Index,Void>(){
            protected MetadataUnion.Index doInBackground()throws Exception{return MetadataUnion.compute(files,()->generation!=(cleanedIndex?cleanUnionGeneration:unionGeneration));}
            protected void done(){if(generation!=(cleanedIndex?cleanUnionGeneration:unionGeneration))return;try{panel.setIndex(get());allMetadata.refreshLink();}catch(Exception e){panel.setIndex(new MetadataUnion.Index(List.of(),List.of()));panel.status.setText("Metadata index could not complete. Reimport changed files.");}}
        }.execute();
    }
    public static void main(String[] args){
        // Third-party parser diagnostics can contain PHI. The UI uses sanitized error messages.
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel","off");
        System.setErr(new PrintStream(OutputStream.nullOutputStream()));
        Theme.install();SwingUtilities.invokeLater(()->{Main app=new Main();app.setVisible(true);if(args.length>0)app.importPaths(Arrays.stream(args).map(Path::of).toList());});
    }
}
