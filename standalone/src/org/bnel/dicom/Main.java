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
    final JTree tree=new JTree(new DefaultMutableTreeNode("Import a folder or files"));
    final MetadataPanel original=new MetadataPanel(), cleaned=new MetadataPanel();
    final MetadataUnion allOriginal=new MetadataUnion(),allCleaned=new MetadataUnion();
    final JTabbedPane metadataTabs=new JTabbedPane();
    final JLabel originalName=new JLabel("Select any individual DICOM instance"), cleanedName=new JLabel("Clean a selection to compare"),
        status=new JLabel(" "), counts=new JLabel("No files imported"), percent=new JLabel(" ");
    final JTextField patientName=new JTextField("",14),patientId=new JTextField("",12),date=new JTextField("2000-01-01",10);
    final JCheckBox minimal=new JCheckBox("Essential research metadata");
    final JCheckBox hierarchy=new JCheckBox("Preserve imported hierarchy",true),originalNames=new JCheckBox("Retain original folder/file names"),
        zip=new JCheckBox("ZIP export"),dicomdir=new JCheckBox("Include DICOMDIR");
    final JComboBox<String> profile=new JComboBox<>(new String[]{"Safe Harbor metadata defaults","Custom: shift dates together"});
    final JProgressBar progress=new JProgressBar(0,100);
    final JButton importFolder,addFiles,remove,clean,review,export,cancel,differences,clear,exceptions,scanLabels,identifiers;
    final JSplitPane outerSplit,metadataSplit;
    Cleaner.Batch batch;
    List<Cleaner.Instance> selection=List.of();
    Cleaner.Instance current;
    String exceptionReason="";
    SwingWorker<?,?> active;
    final AtomicBoolean cancellation=new AtomicBoolean();
    long displayGeneration;
    long unionGeneration,cleanUnionGeneration;
    IdentifierPolicy identifierPolicy=IdentifierPolicy.load(settings);
    Map<String,ScanLabels.Labels> labels=new HashMap<>();
    boolean initializing=true;
    record Group(String title,List<Cleaner.Instance> items){@Override public String toString(){return title;}}

    public Main(){
        super("BNEL Dicom Cleaner");setName("BNEL Dicom Cleaner");setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(900,740));setResizable(true);
        Dimension screen=Toolkit.getDefaultToolkit().getScreenSize();
        setSize(Math.min(settings.integer("width",1480),screen.width),Math.min(settings.integer("height",960),screen.height-30));
        setLocationRelativeTo(null);
        Theme.Background body=new Theme.Background(new BorderLayout(12,12));body.setBorder(BorderFactory.createEmptyBorder(14,18,0,18));setContentPane(body);
        JPanel header=new JPanel(new BorderLayout());header.setOpaque(false);
        JLabel brand=Theme.label("BNEL Dicom Cleaner",28,true);header.add(brand);
        header.add(Theme.row(Theme.button("Maximize / Restore",false,()->setExtendedState((getExtendedState()&MAXIMIZED_BOTH)!=0?NORMAL:MAXIMIZED_BOTH)),Theme.button("Settings",false,this::settingsDialog)),BorderLayout.EAST);header.setBorder(BorderFactory.createEmptyBorder(2,4,8,4));body.add(header,BorderLayout.NORTH);
        JPanel source=new JPanel(new BorderLayout(0,8));source.setOpaque(false);tree.setRowHeight(27);tree.setRootVisible(true);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        source.add(new JScrollPane(tree));counts.setFont(counts.getFont().deriveFont(12f));source.add(counts,BorderLayout.SOUTH);
        source.setPreferredSize(new Dimension(270,400));source.setMinimumSize(new Dimension(180,180));
        JPanel op=new JPanel(new BorderLayout(0,8));op.setOpaque(false);op.add(originalName,BorderLayout.NORTH);op.add(original);
        JPanel cp=new JPanel(new BorderLayout(0,8));cp.setOpaque(false);cp.add(cleanedName,BorderLayout.NORTH);cp.add(cleaned);
        metadataSplit=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,Theme.card("Original",op),Theme.card("Cleaned",cp));metadataSplit.setResizeWeight(.5);metadataSplit.setBorder(null);metadataSplit.setContinuousLayout(true);
        metadataSplit.getLeftComponent().setMinimumSize(new Dimension(220,160));metadataSplit.getRightComponent().setMinimumSize(new Dimension(220,160));
        metadataSplit.setDividerLocation(settings.integer("metadataDivider",560));
        metadataTabs.addTab("Instance comparison",metadataSplit);metadataTabs.addTab("All original metadata",allOriginal);metadataTabs.addTab("All cleaned metadata",allCleaned);
        outerSplit=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,Theme.card("Source",source),metadataTabs);outerSplit.setResizeWeight(.20);outerSplit.setBorder(null);outerSplit.setContinuousLayout(true);outerSplit.setDividerLocation(settings.integer("sourceDivider",275));
        body.add(outerSplit,BorderLayout.CENTER);
        JPanel lower=new JPanel();lower.setOpaque(false);lower.setLayout(new BoxLayout(lower,BoxLayout.Y_AXIS));
        importFolder=Theme.button("Import folder",false,()->chooseImport(true));addFiles=Theme.button("Add files",false,()->chooseImport(false));
        remove=Theme.button("Remove selected",false,this::removeSelection);clean=Theme.button("Clean selected",true,this::cleanSelection);
        review=Theme.button("Review images",false,this::reviewImages);export=Theme.button("Export cleaned",true,this::exportBatch);
        cancel=Theme.button("Cancel",false,()->{cancellation.set(true);status.setText("Cancelling safely…");});
        clear=Theme.button("Clear session",false,this::clearSession);differences=Theme.button("Compare files",false,this::showDifferences);
        exceptions=Theme.button("Exceptions…",false,this::exceptionsDialog);
        scanLabels=Theme.button("Scan labels…",false,this::scanLabelsDialog);identifiers=Theme.button("Identifier rules…",false,this::identifierDialog);
        JPanel actions=Theme.row(importFolder,addFiles,remove,clean,review,export,cancel,clear);lower.add(actions);lower.add(Box.createVerticalStrut(7));
        JPanel options=new JPanel();options.setOpaque(false);options.setLayout(new BoxLayout(options,BoxLayout.Y_AXIS));
        options.add(Theme.row(Theme.label("Profile",14,true),profile,minimal,identifiers,scanLabels,exceptions,differences));
        options.add(Theme.row(Theme.label("Replace",14,true),new JLabel("Patient Name"),patientName,new JLabel("Patient ID"),patientId,new JLabel("Synthetic date / shift anchor"),date));
        options.add(Theme.row(new JLabel("Nested metadata is checked in every file. Reviewed scan labels and acquisition parameters can be retained.")));
        options.add(Theme.row(Theme.label("Export",14,true),hierarchy,originalNames,zip,dicomdir));
        lower.add(Theme.card(null,options));lower.add(Box.createVerticalStrut(8));
        JPanel progressCard=new JPanel(new BorderLayout(10,6));progressCard.setOpaque(false);
        JPanel progressTitle=new JPanel(new BorderLayout());progressTitle.setOpaque(false);progressTitle.add(status);progressTitle.add(percent,BorderLayout.EAST);
        progress.setForeground(Theme.BLUE);progress.setPreferredSize(new Dimension(100,13));progress.setBorderPainted(false);
        progressCard.add(progressTitle,BorderLayout.NORTH);progressCard.add(progress);lower.add(Theme.card(null,progressCard));
        JLabel credit=Theme.label(CREDIT,12,false);credit.setHorizontalAlignment(SwingConstants.RIGHT);credit.setBorder(BorderFactory.createEmptyBorder(10,0,9,2));JPanel footer=new JPanel(new BorderLayout());footer.setOpaque(false);footer.add(credit);lower.add(footer);body.add(lower,BorderLayout.SOUTH);
        hierarchy.setSelected(settings.bool("hierarchy",true));originalNames.setSelected(settings.bool("originalNames",false));zip.setSelected(settings.bool("zip",false));dicomdir.setSelected(settings.bool("dicomdir",false));
        profile.setSelectedIndex(settings.integer("profile",0)==1?1:0);date.setText(settings.text("date","2000-01-01"));
        minimal.setOpaque(false);minimal.setSelected(settings.bool("minimal",false));minimal.setToolTipText("Allowlist for classic CT/MR and registration objects. Enhanced/secondary capture use the broader policy.");
        minimal.addActionListener(e->{invalidateBatch();persist(false);});
        patientName.setToolTipText("Enter an unrelated research name for this import");patientId.setToolTipText("Enter a new research ID for this import");
        for(JCheckBox c:new JCheckBox[]{hierarchy,originalNames,zip,dicomdir}){c.setOpaque(false);c.addActionListener(e->{originalNames.setEnabled(hierarchy.isSelected()&&active==null);persist(false);});}
        tree.addTreeSelectionListener(e->{
            Object n=tree.getLastSelectedPathComponent();if(!(n instanceof DefaultMutableTreeNode node))return;
            Object u=node.getUserObject();selection=u instanceof Group g?g.items:u instanceof Cleaner.Instance i?List.of(i):List.of();
            if(!selection.isEmpty())display(selection.get(0));updateActions();
        });
        DocumentListener changed=new DocumentListener(){public void insertUpdate(DocumentEvent e){changed();}public void removeUpdate(DocumentEvent e){changed();}public void changedUpdate(DocumentEvent e){changed();}
            void changed(){if(!initializing){invalidateBatch();persist(false);}}};
        patientName.getDocument().addDocumentListener(changed);patientId.getDocument().addDocumentListener(changed);date.getDocument().addDocumentListener(changed);
        profile.addActionListener(e->{if(!initializing){invalidateBatch();persist(false);}});
        TransferHandler drop=new TransferHandler(){
            public boolean canImport(TransferSupport support){return active==null&&support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);}
            public boolean importData(TransferSupport support){
                if(!canImport(support))return false;
                try { @SuppressWarnings("unchecked") List<File> files=(List<File>)support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);importPaths(files.stream().map(File::toPath).toList());return true; }
                catch(Exception e){Theme.error(Main.this,e);return false;}
            }
        };tree.setTransferHandler(drop);body.setTransferHandler(drop);
        addWindowListener(new WindowAdapter(){@Override public void windowClosing(WindowEvent e){closeWindow();}});
        addComponentListener(new ComponentAdapter(){public void componentResized(ComponentEvent e){
            int orientation=getHeight()>getWidth()&&getWidth()<1550?JSplitPane.VERTICAL_SPLIT:JSplitPane.HORIZONTAL_SPLIT;
            if(metadataSplit.getOrientation()!=orientation){metadataSplit.setOrientation(orientation);metadataSplit.setDividerLocation(.5);}
            else if(metadataSplit.getDividerLocation()<200||metadataSplit.getDividerLocation()>metadataSplit.getWidth()-220)metadataSplit.setDividerLocation(.5);
            body.revalidate();
        }});
        SwingUtilities.invokeLater(()->metadataSplit.setDividerLocation(.5));
        initializing=false;updateActions();
    }
    void updateActions(){
        boolean idle=active==null;
        for(JButton b:new JButton[]{importFolder,addFiles,clear,exceptions,identifiers})b.setEnabled(idle);
        scanLabels.setEnabled(idle&&!instances.isEmpty());
        remove.setEnabled(idle&&!selection.isEmpty());clean.setEnabled(idle&&!selection.isEmpty());
        review.setEnabled(idle&&(batch!=null||current!=null));export.setEnabled(idle&&batch!=null&&batch.exportReady());cancel.setEnabled(!idle);
        differences.setEnabled(idle&&instances.size()>1);
        for(JComponent c:new JComponent[]{patientName,patientId,date,profile,hierarchy,zip,dicomdir,minimal})c.setEnabled(idle);
        originalNames.setEnabled(idle&&hierarchy.isSelected());
    }
    void chooseImport(boolean folder){
        JFileChooser c=new JFileChooser();c.setDialogTitle(folder?"Import a DICOM folder":"Add DICOM files");
        c.setFileSelectionMode(folder?JFileChooser.DIRECTORIES_ONLY:JFileChooser.FILES_ONLY);c.setMultiSelectionEnabled(!folder);
        if(c.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        importPaths(folder?List.of(c.getSelectedFile().toPath()):Arrays.stream(c.getSelectedFiles()).map(File::toPath).toList());
    }
    void importPaths(List<Path> paths){
        patientName.setText("");patientId.setText("");labels.clear();
        invalidateBatch();runTask("Importing",()->Cleaner.scan(paths,this::progress,cancellation::get),scan->{
            Set<Path> existing=new HashSet<>();for(Cleaner.Instance i:instances)existing.add(i.source());
            for(Cleaner.Instance i:scan.instances())if(existing.add(i.source()))instances.add(i);
            rebuildTree();refreshUnion(false);metadataTabs.setSelectedIndex(1);status.setText("Imported "+scan.instances().size()+" instances; enter a new research name and ID");
            if(!scan.issues().isEmpty())showText("Import issues — inspect these files",String.join("\n",scan.issues()));
        });
    }
    void rebuildTree(){
        DefaultMutableTreeNode root=new DefaultMutableTreeNode(new Group("Imported files ("+instances.size()+")",List.copyOf(instances)));
        Map<String,List<Cleaner.Instance>> patients=new LinkedHashMap<>();for(Cleaner.Instance i:instances)patients.computeIfAbsent(i.patientKey(),k->new ArrayList<>()).add(i);
        for(List<Cleaner.Instance> p:patients.values()){
            Cleaner.Instance first=p.get(0);DefaultMutableTreeNode patient=new DefaultMutableTreeNode(new Group((first.patientName().isBlank()?"Unnamed":first.patientName())+" · "+first.patientId(),List.copyOf(p)));root.add(patient);
            Map<String,List<Cleaner.Instance>> studies=new LinkedHashMap<>();for(Cleaner.Instance i:p)studies.computeIfAbsent(i.study(),k->new ArrayList<>()).add(i);
            int studyNo=0;for(List<Cleaner.Instance> study:studies.values()){
                DefaultMutableTreeNode st=new DefaultMutableTreeNode(new Group("Study "+(++studyNo)+" · "+study.size()+" files",List.copyOf(study)));patient.add(st);
                Map<String,List<Cleaner.Instance>> series=new LinkedHashMap<>();for(Cleaner.Instance i:study)series.computeIfAbsent(i.series(),k->new ArrayList<>()).add(i);
                for(List<Cleaner.Instance> s:series.values()){
                    s.sort(Comparator.comparingInt(Cleaner.Instance::instanceNumber).thenComparing(i->i.source().toString()));
                    Cleaner.Instance f=s.get(0);DefaultMutableTreeNode se=new DefaultMutableTreeNode(new Group(f.modality()+" · "+(f.description().isBlank()?"Series":f.description())+" ("+s.size()+")",List.copyOf(s)));st.add(se);
                    for(Cleaner.Instance i:s)se.add(new DefaultMutableTreeNode(i));
                }
            }
        }
        tree.setModel(new DefaultTreeModel(root));for(int r=0;r<tree.getRowCount();r++)if(tree.getPathForRow(r).getPathCount()<4)tree.expandRow(r);
        counts.setText(patients.size()+" subject group(s) · "+instances.size()+" files");
        if(root.getChildCount()>0)tree.setSelectionPath(new TreePath(((DefaultMutableTreeNode)root.getChildAt(0)).getPath()));else {selection=List.of();current=null;original.setData(null);cleaned.setData(null);}
        updateActions();
    }
    Cleaner.Output outputFor(Cleaner.Instance i){return batch==null?null:batch.outputs.stream().filter(o->o.input.source().equals(i.source())).findFirst().orElse(null);}
    void display(Cleaner.Instance instance){
        current=instance;long generation=++displayGeneration;Cleaner.Output output=outputFor(instance);
        originalName.setText(instance.source().getFileName().toString());originalName.setToolTipText(instance.source()+"\n"+instance.problem());
        cleanedName.setText(output==null?"Not cleaned":output.reviewed?"Reviewed instance":"Metadata cleaned · review pending");
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
    void clearSession(){invalidateBatch();instances.clear();labels.clear();selection=List.of();patientName.setText("");patientId.setText("");rebuildTree();refreshUnion(false);progress.setValue(0);percent.setText(" ");status.setText(" ");}
    void cleanSelection(){
        try{
            if(profile.getSelectedIndex()==1&&exceptionReason.isBlank()){exceptionsDialog();if(exceptionReason.isBlank())return;}
            Policy.Options options=new Policy.Options(patientName.getText(),patientId.getText(),profile.getSelectedIndex()==0?Policy.DateMode.FIXED:Policy.DateMode.SHIFT,LocalDate.parse(date.getText().strip()),minimal.isSelected());
            List<Cleaner.Instance> chosen=List.copyOf(selection);invalidateBatch();
            IdentifierPolicy selectedPolicy=identifierPolicy;Map<String,ScanLabels.Labels> selectedLabels=Map.copyOf(labels);
            runTask("Cleaning metadata",()->Cleaner.prepare(chosen,options,exceptionReason,selectedPolicy,selectedLabels,this::progress,cancellation::get),result->{batch=result;if(current!=null)display(current);refreshUnion(true);metadataTabs.setSelectedIndex(0);status.setText("Metadata verified · Review all "+batch.outputs.size()+" instances before export");});
        }catch(Exception e){Theme.error(this,e);}
    }
    void reviewImages(){
        if(batch!=null){new ReviewDialog(this,batch).setVisible(true);if(current!=null)display(current);refreshUnion(true);updateActions();}
        else if(current!=null)new ReviewDialog(this,current).setVisible(true);
    }
    void exportBatch(){
        if(batch==null)return;
        if(hierarchy.isSelected()&&originalNames.isSelected()){
            if(JOptionPane.showConfirmDialog(this,"Original folder and file names may identify a patient.\nHave you reviewed every retained name and confirmed it contains no identifying information?","Review export names",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.YES_OPTION)return;
        }
        JFileChooser c=new JFileChooser();c.setDialogTitle("Choose a NEW output "+(zip.isSelected()?"ZIP file":"folder name"));c.setFileSelectionMode(JFileChooser.FILES_ONLY);
        c.setSelectedFile(new File("BNEL-Export-"+System.currentTimeMillis()+(zip.isSelected()?".zip":"")));
        if(c.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        Cleaner.ExportOptions options=new Cleaner.ExportOptions(zip.isSelected(),dicomdir.isSelected(),hierarchy.isSelected(),originalNames.isSelected());
        Cleaner.Batch selected=batch;Path target=c.getSelectedFile().toPath();
        runTask("Exporting",()->Cleaner.export(selected,target,options,this::progress,cancellation::get),path->{
            status.setText("Export complete: "+path.getFileName());
            if(JOptionPane.showConfirmDialog(this,"Export verified. Open the output location?","Export complete",JOptionPane.YES_NO_OPTION)==JOptionPane.YES_OPTION)
                try{Desktop.getDesktop().open((Files.isDirectory(path)?path:path.getParent()).toFile());}catch(Exception e){Theme.error(this,e);}
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
    void exceptionsDialog(){
        JTextArea note=new JTextArea(exceptionReason,5,55);note.setLineWrap(true);note.setWrapStyleWord(true);
        JPanel panel=new JPanel(new BorderLayout(5,10));panel.add(new JLabel("<html>Date shifting retains timing information and is outside the default profile.<br>Record the approved research basis here. This note stays in memory; do not enter patient details.<br>It is not an Expert Determination or HIPAA approval.</html>"),BorderLayout.NORTH);panel.add(new JScrollPane(note));
        if(JOptionPane.showConfirmDialog(this,panel,"Document date-shift exception",JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION){exceptionReason=note.getText().strip();invalidateBatch();}
    }
    void persist(boolean reportErrors){
        settings.set("width",getWidth());settings.set("height",getHeight());settings.set("sourceDivider",outerSplit.getDividerLocation());settings.set("metadataDivider",metadataSplit.getDividerLocation());
        settings.set("hierarchy",hierarchy.isSelected());settings.set("originalNames",originalNames.isSelected());settings.set("zip",zip.isSelected());settings.set("dicomdir",dicomdir.isSelected());settings.set("profile",profile.getSelectedIndex());settings.set("date",date.getText());
        settings.set("minimal",minimal.isSelected());identifierPolicy.save(settings);
        try{settings.save();}catch(IOException e){if(reportErrors)Theme.error(this,new IOException("Preferences could not be saved. Extract the app to a writable folder."));}
    }
    void closeWindow(){
        if(active!=null){cancellation.set(true);status.setText("Cancelling — close again when processing stops");return;}
        persist(true);if(batch!=null)try{batch.close();}catch(IOException e){Theme.error(this,new IOException("Some temporary files could not be removed. Close other viewers and clear this session."));return;}
        dispose();
    }
    void settingsDialog(){
        showText("Settings & information","BNEL Dicom Cleaner "+Policy.VERSION+"\n\nOptions and window size are remembered in:\n"+settings.path+"\n\nYour original files are never overwritten. Import additions and policy changes invalidate prior cleaning.\n\nExport hierarchy: neutral names preserve directory nesting without carrying source identifiers. Retaining original names requires a name review. DICOMDIR has short uppercase path limits.\n\nDefault policy: original identifiers/dates are removed or replaced with synthetic values; private attributes and free text are scrubbed.\n\nReview every image/frame for burned-in text and recognizable anatomy. This version includes manual monochrome blackout, not automatic facial defacing or OCR. Use a validated defacing workflow where needed before import.\n\nPixelMed 20260916; DICOM PS3.15 2026d policy data. See bundled notices and user guide. Research validation release; no HIPAA certification claim.");
    }
    void showText(String title,String text){JTextArea area=new JTextArea(text,24,90);area.setEditable(false);area.setLineWrap(true);area.setWrapStyleWord(true);area.setCaretPosition(0);JOptionPane.showMessageDialog(this,new JScrollPane(area),title,JOptionPane.INFORMATION_MESSAGE);}
    void showDifferences(){
        List<Cleaner.Instance> chosen=selection.size()>1?List.copyOf(selection):List.copyOf(instances);
        runTask("Comparing every instance",()->MetadataDifferences.compute(chosen,this::progress,cancellation::get),result->MetadataDifferences.show(this,chosen,result));
    }
    void identifierDialog(){IdentifierPolicy changed=IdentifierPolicy.edit(this,identifierPolicy);if(changed!=null){identifierPolicy=changed;invalidateBatch();persist(true);}}
    void scanLabelsDialog(){List<Cleaner.Instance> all=List.copyOf(instances);runTask("Reading scan labels",()->ScanLabels.collect(all,this::progress,cancellation::get),series->{Map<String,ScanLabels.Labels> changed=ScanLabels.edit(this,series,labels);if(changed!=null){labels=changed;invalidateBatch();}});}
    void refreshUnion(boolean cleanedIndex){
        long generation=cleanedIndex?++cleanUnionGeneration:++unionGeneration;
        List<MetadataUnion.FileEntry> files=cleanedIndex?(batch==null?List.of():batch.outputs.stream().map(o->new MetadataUnion.FileEntry(o.file,o.input.source().getFileName().toString())).toList()):instances.stream().map(i->new MetadataUnion.FileEntry(i.source(),i.relativePath().toString())).toList();
        MetadataUnion panel=cleanedIndex?allCleaned:allOriginal;panel.status.setText("Indexing every file and nested metadata path…");
        new SwingWorker<MetadataUnion.Index,Void>(){
            protected MetadataUnion.Index doInBackground()throws Exception{return MetadataUnion.compute(files,()->generation!=(cleanedIndex?cleanUnionGeneration:unionGeneration));}
            protected void done(){if(generation!=(cleanedIndex?cleanUnionGeneration:unionGeneration))return;try{panel.setIndex(get());}catch(Exception e){panel.setIndex(new MetadataUnion.Index(List.of(),List.of()));panel.status.setText("Metadata index could not complete. Reimport changed files.");}}
        }.execute();
    }
    public static void main(String[] args){
        // Third-party parser diagnostics can contain PHI. The UI uses sanitized error messages.
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel","off");
        System.setErr(new PrintStream(OutputStream.nullOutputStream()));
        Theme.install();SwingUtilities.invokeLater(()->{Main app=new Main();app.setVisible(true);if(args.length>0)app.importPaths(Arrays.stream(args).map(Path::of).toList());});
    }
}
