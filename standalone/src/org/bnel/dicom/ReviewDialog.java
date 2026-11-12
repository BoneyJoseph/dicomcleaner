package org.bnel.dicom;

import com.pixelmed.dicom.*;
import com.pixelmed.display.SourceImage;
import com.pixelmed.display.WindowCenterAndWidth;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;

/** Optional stack inspection with responsive scrubbing, cine playback and bulk selection. */
final class ReviewDialog extends JFrame {
    final Window reviewOwner;
    SecondaryLoop reviewLoop;
    final Cleaner.Batch batch;
    final List<Cleaner.Instance> inputs;
    final SourceTree files=new SourceTree();
    final JLabel status=new JLabel("Loading…"),frameText=new JLabel(" ");
    final JLabel fileText=new JLabel(" ");
    final JTextArea reportText=new JTextArea();
    final JCheckBox markedCurrent=new JCheckBox("Mark file (M)");
    final Set<Path> marked=new LinkedHashSet<>(),excluded=new LinkedHashSet<>(),trashed=new LinkedHashSet<>();
    final JButton removeMarked,trashMarked,restore,play,selectUnmarked,markSelected,unmarkSelected;
    final JSpinner fps=new JSpinner(new SpinnerNumberModel(15,1,60,1));
    final javax.swing.Timer cine;
    final JTabbedPane tabs=new JTabbedPane();
    final ViewerFrames frames;
    ViewerFrames.Frame shown;
    final int[] lengths;
    int[] starts, orderedStarts, timelineOrder, timelinePositions;
    int position,targetPosition,shownFrame;
    long metadataGeneration;
    boolean playing,followPlayback=true,compactTransport;
    final AppSettings viewSettings;
    final RatioSplit mainSplit,tagsSplit;
    final SourceBrowser sourceBrowser;
    final javax.swing.Timer scrollTimer;
    int pendingSteps;
    double wheelRemainder;
    final MetadataPanel sourceTags=new MetadataPanel(),cleanTags=new MetadataPanel();
    final Canvas canvas=new Canvas();
    final JSlider frame=new JSlider(1,1,1),zoom=new JSlider(25,400,100);
    final JSpinner width=new JSpinner(new SpinnerNumberModel(4096d,1d,1e12,10d)),level=new JSpinner(new SpinnerNumberModel(2048d,-1e12,1e12,10d));
    final JToggleButton draw=new JToggleButton("Draw blackout");
    final JButton redact,reset,close,previous,next,more;
    /** CT window presets (Hounsfield units). Entry 0 is the menu title; selecting applies and returns to it. */
    static final String[][] CT_PRESETS={{"Window presets","",""},{"File default","",""},{"Brain","80","40"},{"Subdural","200","75"},{"Stroke","40","40"},{"Soft tissue","400","50"},{"Bone","2800","600"},{"Lung","1500","-600"}};
    final JComboBox<String> presets=new JComboBox<>(java.util.Arrays.stream(CT_PRESETS).map(p->p[0]+(p[1].isEmpty()?"":"  "+p[1]+" / "+p[2])).toArray(String[]::new));
    JPanel fpsBox;
    Point wlStart;double wlWidth,wlLevel;
    boolean busy,setting,failed;
    int index=-1,frameCount;
    boolean closed,mutating;
    ReviewDialog(Window owner,Cleaner.Batch batch){this(owner,batch,batch.outputs.stream().map(o->o.input).toList());}
    ReviewDialog(Window owner,Cleaner.Instance input){this(owner,null,List.of(input));}
    ReviewDialog(Window owner,List<Cleaner.Instance> inputs,Cleaner.Instance selected){this(owner,null,inputs);if(selected!=null){int i=inputs.indexOf(selected);if(i>0)SwingUtilities.invokeLater(()->navigateTo(i));}}
    private ReviewDialog(Window owner,Cleaner.Batch batch,List<Cleaner.Instance> inputs){
        super(batch==null?"Inspect original images and reports":"Review cleaned images");reviewOwner=owner;setResizable(true);
        this.batch=batch;this.inputs=List.copyOf(inputs);viewSettings=owner instanceof Main m?m.settings:new AppSettings();setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);setMinimumSize(new Dimension(Theme.u(960),Theme.u(640)));Theme.installWindow(this);
        frames=new ViewerFrames(java.util.stream.IntStream.range(0,inputs.size()).mapToObj(i->batch==null?inputs.get(i).source():batch.outputs.get(i).file).toList());
        files.setInstances(this.inputs);timelineOrder=files.order.clone();timelinePositions=new int[inputs.size()];for(int n=0;n<timelineOrder.length;n++)timelinePositions[timelineOrder[n]]=n;
        lengths=inputs.stream().mapToInt(i->Math.max(1,i.frames())).toArray();rebuildTimeline();
        cine=new javax.swing.Timer(1000/15,e->{if(!busy){if(targetPosition>=frame.getMaximum()-1){setPlaying(false);return;}step(1);}});
        scrollTimer=new javax.swing.Timer(12,e->drainNavigation());scrollTimer.setRepeats(false);
        Theme.Background root=new Theme.Background(new BorderLayout(Theme.u(12),Theme.u(12)));root.setBorder(BorderFactory.createEmptyBorder(Theme.u(12),Theme.u(12),Theme.u(8),Theme.u(12)));setContentPane(root);
        fileText.setFont(Theme.bodyFont);fileText.setForeground(Theme.MUTED);root.add(fileText,BorderLayout.NORTH);
        refreshList();
        files.addTreeSelectionListener(e->{if(!setting&&!files.filtering){pendingSteps=0;scrollTimer.stop();setPlaying(false);followPlayback=false;navigateTo(files.getLeadSelectionIndex());update();}});
        sourceBrowser=new SourceBrowser(files,true);
        sourceBrowser.scroll.addMouseWheelListener(e->followPlayback=false);
        sourceBrowser.scroll.getVerticalScrollBar().addMouseListener(new MouseAdapter(){public void mousePressed(MouseEvent e){followPlayback=false;}});
        sourceBrowser.scroll.getVerticalScrollBar().addAdjustmentListener(e->{if(e.getValueIsAdjusting())followPlayback=false;});
        JPanel left=Theme.navigation(null,sourceBrowser);left.setMinimumSize(new Dimension(Theme.u(200),200));
        JPanel preview=new JPanel(new BorderLayout(8,8));preview.setOpaque(false);
        JScrollPane imageScroll=new JScrollPane(canvas);imageScroll.setWheelScrollingEnabled(false);imageScroll.getViewport().setBackground(Color.BLACK);canvas.scroll=imageScroll;preview.add(imageScroll);imageScroll.addMouseWheelListener(this::wheel);

        frame.setPaintTicks(false);frame.setPreferredSize(new Dimension(300,24));frame.setToolTipText("Drag to move through the entire stack, including frames in multi-frame files");
        previous=Theme.button("Previous image",false,()->queueStep(-1));
        next=Theme.button("Next image",false,()->queueStep(1));
        play=Theme.button("Play",false,()->setPlaying(!playing));
        fps.addChangeListener(e->cine.setDelay(Math.max(1,1000/(Integer)fps.getValue())));
        fps.setToolTipText("Target playback rate in frames per second");
        zoom.setPreferredSize(new Dimension(Theme.u(76),Theme.u(28)));zoom.setMaximumSize(zoom.getPreferredSize());SettingsStrip.fixed(width,84);SettingsStrip.fixed(level,84);SettingsStrip.fixed(fps,64);
        zoom.setToolTipText("Zoom the image; Ctrl+mouse wheel also changes zoom");width.setToolTipText("Window width: adjust contrast");level.setToolTipText("Window level: adjust brightness");
        reset=Theme.button("Auto / Fit",false,this::autoWindow);
        draw.setIcon(new UiIcons("rectangle"));draw.setToolTipText("Draw rectangles over text or identifiers in the current cleaned image");
        draw.addActionListener(e->canvas.setCursor(Cursor.getPredefinedCursor(draw.isSelected()?Cursor.CROSSHAIR_CURSOR:Cursor.DEFAULT_CURSOR)));
        redact=Theme.button("Apply blackout to all frames",true,this::redact);
        JButton clearRectangles=Theme.button("Clear rectangles",false,()->{canvas.areas.clear();canvas.repaint();});
        JPanel blackout=Theme.line(draw,clearRectangles,redact);blackout.setVisible(batch!=null);
        JPanel scrub=new JPanel(new BorderLayout(Theme.u(8),0));scrub.setOpaque(false);scrub.add(new JLabel("Stack"),BorderLayout.WEST);scrub.add(frame);scrub.add(frameText,BorderLayout.EAST);
        frameText.setPreferredSize(new Dimension(Theme.u(140),Theme.u(24)));frameText.setHorizontalAlignment(SwingConstants.RIGHT);frameText.setFont(Theme.bodyFont.deriveFont((float)Theme.u(12)));
        // Compact transport labels keep the functional name and explanatory tooltip intact.
        previous.setText("");next.setText("");play.setText("");compactTransport=true;Theme.equalButtons(32,previous,play,next);   // icon-only media controls; names/tooltips unchanged
        markedCurrent.setOpaque(false);markedCurrent.setToolTipText("Flag this whole DICOM file for later exclusion or removal. Shortcut: M.");
        markedCurrent.addActionListener(e->{if(index>=0){Path p=inputs.get(index).source();if(markedCurrent.isSelected()){marked.add(p);reviewed.remove(p);if(batch!=null)batch.outputs.get(index).reviewed=false;}else marked.remove(p);refreshList();update();}});
        removeMarked=Theme.button("Exclude marked from export",false,this::excludeMarked);
        trashMarked=Theme.button("Move originals to Recycle Bin…",false,this::recycleMarked);
        restore=Theme.button("Undo exclusion",false,()->{if(index>=0&&!trashed.contains(inputs.get(index).source())){excluded.remove(inputs.get(index).source());refreshList();update();}});
        // Image adjustment and file disposition remain available when changing review tabs.
        presets.setName("Window presets");presets.setToolTipText("CT window presets (W / L in Hounsfield units). Right-drag on the image also adjusts window/level.");SettingsStrip.fixed(presets,170);
        presets.addActionListener(e->{int i=presets.getSelectedIndex();if(i<=0||shown==null)return;
            setting=true;if(i==1){width.setValue(Math.max(1d,shown.window));level.setValue(shown.center);}else{width.setValue(Double.parseDouble(CT_PRESETS[i][1]));level.setValue(Double.parseDouble(CT_PRESETS[i][2]));}
            presets.setSelectedIndex(0);setting=false;canvas.render();});
        JPanel adjustments=Theme.line(new JLabel("Zoom"),zoom,new JLabel("Width"),width,new JLabel("Level"),level,presets,reset);
        fpsBox=Theme.line(new JLabel("fps"),fps);fpsBox.setVisible(false);
        ViewerTools controls=new ViewerTools(Theme.line(previous,play,next,fpsBox),scrub,adjustments,blackout);
        preview.add(controls,BorderLayout.SOUTH);
        // Dark image stage: image, transport, scrubber and adjustments on dark surfaces.
        preview.setOpaque(true);preview.setBackground(Theme.STAGE);preview.setBorder(BorderFactory.createEmptyBorder(Theme.u(10),Theme.u(10),Theme.u(10),Theme.u(10)));
        imageScroll.setBorder(BorderFactory.createLineBorder(Theme.STAGE_BORDER));Theme.darkStage(controls);
        tabs.addTab("Image review",preview);
        reportText.setEditable(false);reportText.setLineWrap(true);reportText.setWrapStyleWord(true);reportText.setMargin(new Insets(12,12,12,12));tabs.addTab("Report / text",Theme.scroll(reportText));
        RatioSplit tags=tagsSplit=new RatioSplit(JSplitPane.HORIZONTAL_SPLIT,Theme.card("Original · every tag",sourceTags),Theme.card("Cleaned · every tag",cleanTags),.5);tags.restore(viewSettings,"layout.viewer.tags.ratio");tags.setResizeWeight(.5);tags.setBorder(null);tags.setContinuousLayout(true);tabs.addTab("Full metadata",tags);tabs.addChangeListener(e->{if(tabs.getSelectedIndex()>0){setPlaying(false);loadDetails();}});
        tabs.setMinimumSize(new Dimension(Theme.u(500),240));mainSplit=new RatioSplit(JSplitPane.HORIZONTAL_SPLIT,left,tabs,.255);mainSplit.restore(viewSettings,"layout.viewer.source.ratio");root.add(mainSplit);
        JPanel bottom=new JPanel(new BorderLayout(0,Theme.u(8)));bottom.setOpaque(false);
        selectUnmarked=Theme.button("Select all unmarked",false,this::selectUnmarked);
        close=Theme.button("Close",false,this::closeReview);
        markSelected=Theme.button("Mark all selected",false,()->markSelection(true));
        unmarkSelected=Theme.button("Unmark selected",false,()->markSelection(false));
        selectUnmarked.setText("Select unmarked");markSelected.setText("Mark selected");unmarkSelected.setText("Unmark selected");
        JPopupMenu moreMenu=new JPopupMenu();
        JMenuItem recycleItem=new JMenuItem("Move originals to Recycle Bin…",new UiIcons("trash"));recycleItem.setName("Move originals to Recycle Bin…");
        recycleItem.setForeground(new Color(0xA33A2F));recycleItem.addActionListener(e->trashMarked.doClick());moreMenu.add(recycleItem);
        JButton moreButton=new JButton("More",new UiIcons("more"));moreButton.setName("More");moreButton.setIconTextGap(Theme.u(6));
        moreButton.setToolTipText("Less common actions, including moving original files to the Recycle Bin");
        moreButton.addActionListener(e->{recycleItem.setEnabled(trashMarked.isEnabled());recycleItem.setToolTipText(trashMarked.getToolTipText());moreMenu.show(moreButton,0,-moreMenu.getPreferredSize().height-Theme.u(4));});
        more=moreButton;
        ViewerActions actionStrip=new ViewerActions(Theme.line(markedCurrent,Theme.group(selectUnmarked,markSelected,unmarkSelected)),Theme.line(removeMarked,restore),Theme.line(more),Theme.line(close));bottom.add(actionStrip);
        Runnable density=()->{
            int available=controls.getWidth();if(available<=0)return;
            int toolsMinimum=adjustments.getPreferredSize().width+(blackout.isVisible()?blackout.getPreferredSize().width+Theme.u(24):0);
            int actionsMinimum=actionStrip.marking.getPreferredSize().width+actionStrip.exclude.getPreferredSize().width+actionStrip.recycle.getPreferredSize().width+actionStrip.close.getPreferredSize().width+Theme.u(48);
            int hysteresis=actionStrip.compact?Theme.u(40):0;
            boolean compact=available<Theme.u(700)||available<toolsMinimum+hysteresis||actionStrip.getWidth()<actionsMinimum+hysteresis;
            if(compact!=actionStrip.compact){actionStrip.arrange(compact);controls.arrange(compact);}else if(controls.stackBelow!=(available<Theme.u(680)))controls.arrange(compact);
            boolean shortTransport=true;   // media controls are always icon-only
            if(shortTransport!=compactTransport){compactTransport=shortTransport;previous.setText(shortTransport?"":"Previous");next.setText(shortTransport?"":"Next");play.setText(shortTransport?"":playing?"Pause":"Play");
                for(JButton b:new JButton[]{previous,next,play}){b.setPreferredSize(null);b.setMinimumSize(null);b.setMaximumSize(null);}Theme.equalButtons(32,previous,next);Theme.equalButtons(32,play);controls.revalidate();}
        };
        root.addComponentListener(new ComponentAdapter(){public void componentResized(ComponentEvent e){SwingUtilities.invokeLater(density);}});
        controls.addComponentListener(new ComponentAdapter(){public void componentResized(ComponentEvent e){SwingUtilities.invokeLater(density);}});
        status.setFont(Theme.bodyFont.deriveFont((float)Theme.u(12)));status.setForeground(Theme.MUTED);status.addPropertyChangeListener("text",e->status.setToolTipText(status.getText()));
        bottom.add(Theme.footer(this,status),BorderLayout.SOUTH);root.add(bottom,BorderLayout.SOUTH);
        frame.addChangeListener(e->{if(!setting){pendingSteps=0;scrollTimer.stop();setPlaying(false);followPlayback=true;requestPosition(frame.getValue()-1);}});
        zoom.addChangeListener(e->{canvas.revalidate();canvas.repaint();});width.addChangeListener(e->{if(!setting)canvas.render();});level.addChangeListener(e->{if(!setting)canvas.render();});
        addWindowListener(new WindowAdapter(){public void windowClosing(WindowEvent e){closeReview();}});
        bind("RIGHT",()->queueStep(1));bind("LEFT",()->queueStep(-1));bind("PAGE_DOWN",()->navigateFile(1));bind("PAGE_UP",()->navigateFile(-1));bind("SPACE",()->setPlaying(!playing));bind("M",()->{if(markedCurrent.isEnabled())markedCurrent.doClick();});
        Dimension screen=Toolkit.getDefaultToolkit().getScreenSize();setSize(Math.min(viewSettings.integer("viewerWidth",Theme.u(1600)),screen.width),Math.min(viewSettings.integer("viewerHeight",Theme.u(1000)),screen.height-40));setLocationRelativeTo(owner);
        setting=true;files.setSelectedIndex(0);setting=false;requestPosition(0);
    }
    private void refreshList(){
        files.refreshStates(i->{Path p=inputs.get(i).source();return (marked.contains(p)?SourceTree.MARKED:0)|(excluded.contains(p)?SourceTree.EXCLUDED:0)|((reviewed.contains(p)||batch!=null&&batch.outputs.get(i).reviewed)?SourceTree.REVIEWED:0);});
    }
    final Set<Path> reviewed=new HashSet<>();
    void rebuildTimeline(){
        starts=new int[lengths.length+1];orderedStarts=new int[lengths.length+1];
        for(int n=0;n<timelineOrder.length;n++){int i=timelineOrder[n];starts[i]=orderedStarts[n];orderedStarts[n+1]=Math.addExact(orderedStarts[n],lengths[i]);}
        starts[lengths.length]=orderedStarts[lengths.length];boolean old=setting;setting=true;frame.setMaximum(Math.max(1,orderedStarts[lengths.length]));setting=old;
    }
    ViewerFrames.Key keyAt(int position){int n=Arrays.binarySearch(orderedStarts,position);if(n<0)n=-n-2;n=Math.max(0,Math.min(n,inputs.size()-1));return new ViewerFrames.Key(timelineOrder[n],position-orderedStarts[n]);}
    void requestPosition(int wanted){
        if(closed||mutating||inputs.isEmpty())return;targetPosition=Math.max(0,Math.min(frame.getMaximum()-1,wanted));
        ViewerFrames.Key key=keyAt(targetPosition);int target=targetPosition;busy=true;metadataGeneration++;
        fileText.setText("Loading slice "+(target+1)+" · "+inputs.get(key.file()).source().getFileName()+(shown==null?"":" · Previous image remains visible"));
        canvas.start=null;canvas.pending=null;
        if(key.file()!=index){sourceTags.setData(null);cleanTags.setData(null);reportText.setText("Loading selected file…");}
        setting=true;frame.setValue(target+1);setting=false;
        List<ViewerFrames.Key> ahead=new ArrayList<>();for(int delta=1;delta<=12;delta++)if(target+delta<frame.getMaximum())ahead.add(keyAt(target+delta));for(int delta=1;delta<=3;delta++)if(target-delta>=0)ahead.add(keyAt(target-delta));
        status.setText("Loading slice "+(target+1)+"…");
        if(trashed.contains(inputs.get(key.file()).source())){showResult(new ViewerFrames.Result(key,null,"This original was moved to Recycle Bin."),target);return;}
        frames.request(key,ahead,result->showResult(result,target));update();
    }
    private void showResult(ViewerFrames.Result result,int target){
        if(closed||target!=targetPosition)return;
        int old=index;boolean seriesChanged=index<0||!inputs.get(index).series().equals(inputs.get(result.key().file()).series());
        index=result.key().file();shownFrame=result.key().frame();shown=result.frame();position=target;busy=false;failed=shown==null;
        if(shown!=null&&shown.pages>0&&lengths[index]!=shown.pages){lengths[index]=shown.pages;rebuildTimeline();targetPosition=position=starts[index]+shownFrame;}
        frameCount=lengths[index];
        if(old!=index){canvas.areas.clear();draw.setSelected(false);metadataGeneration++;sourceTags.setData(null);cleanTags.setData(null);reportText.setText("Select Report / text to load this file's contents.");}
        canvas.raw=shown==null?null:shown.raw;
        if(seriesChanged&&shown!=null){setting=true;width.setValue(Math.max(1d,shown.window));level.setValue(shown.center);setting=false;}
        canvas.render();
        setting=true;frame.setValue(position+1);files.setCurrent(index);if(followPlayback&&files.getSelectedIndices().length<=1){files.setSelectedIndex(index);files.ensureIndexIsVisible(index);}markedCurrent.setSelected(marked.contains(inputs.get(index).source()));setting=false;
        fileText.setText(inputs.get(index).importRoot().getFileName()+" › "+inputs.get(index).modality()+" · "+(inputs.get(index).description().isBlank()?"Series "+inputs.get(index).seriesNumber():inputs.get(index).description())+" · File "+(timelinePositions[index]+1)+" / "+inputs.size()+" · "+inputs.get(index).relativePath());fileText.setToolTipText(inputs.get(index).source().toString());
        frameText.setText("Slice "+(position+1)+" / "+frame.getMaximum()+(frameCount>1?" · Frame "+(shownFrame+1)+" / "+frameCount:""));
        frameText.setToolTipText(frameText.getText());int counterWidth=frameText.getFontMetrics(frameText.getFont()).stringWidth(frameText.getText())+Theme.u(8);if(counterWidth>frameText.getPreferredSize().width){frameText.setPreferredSize(new Dimension(counterWidth,Theme.u(24)));frameText.revalidate();}
        status.setText(failed?result.error():shown.message.isEmpty()?(playing?"Playing · "+fps.getValue()+" fps target":"Image wheel / arrows: slices · Tree arrows: folders · Space: play / pause · Ctrl+wheel: zoom · M: mark"):shown.message);
        if(tabs.getSelectedIndex()>0)loadDetails();update();
    }
    private void loadDetails(){
        if(index<0||busy||closed)return;final int selected=index;long generation=++metadataGeneration;
        new SwingWorker<AttributeList[],Void>(){
            protected AttributeList[] doInBackground()throws Exception{return new AttributeList[]{DicomIO.read(inputs.get(selected).source()).list(),batch==null?null:DicomIO.read(batch.outputs.get(selected).file).list()};}
            protected void done(){if(closed||generation!=metadataGeneration||selected!=index)return;try{AttributeList[] data=get();sourceTags.setData(data[0]);cleanTags.setData(data[1]);reportText.setText(ReportPreview.text(data[1]==null?data[0]:data[1]));reportText.setCaretPosition(0);}catch(Exception e){reportText.setText("Could not read this file's report or metadata.");}}
        }.execute();
    }
    private void autoWindow(){if(shown==null)return;setting=true;width.setValue(Math.max(1d,shown.window));level.setValue(shown.center);zoom.setValue(100);setting=false;canvas.render();}
    private void update(){
        boolean pixels=shown!=null&&shown.raw!=null;
        width.setEnabled(!busy&&pixels&&shown.gray);level.setEnabled(!busy&&pixels&&shown.gray);presets.setEnabled(!busy&&pixels&&shown.gray&&index>=0&&"CT".equals(inputs.get(index).modality()));reset.setEnabled(!busy&&pixels);zoom.setEnabled(!busy&&pixels);files.setEnabled(!mutating);frame.setEnabled(!mutating&&frame.getMaximum()>1);
        draw.setEnabled(!busy&&batch!=null&&pixels&&shown.gray);redact.setEnabled(draw.isEnabled());previous.setEnabled(targetPosition>0);next.setEnabled(targetPosition<frame.getMaximum()-1);play.setEnabled(!mutating&&frame.getMaximum()>1);close.setEnabled(!mutating);
        markedCurrent.setEnabled(!busy&&index>=0&&!trashed.contains(inputs.get(index).source()));removeMarked.setEnabled(!busy&&!mutating&&index>=0);restore.setEnabled(!busy&&index>=0&&excluded.contains(inputs.get(index).source())&&!trashed.contains(inputs.get(index).source()));trashMarked.setEnabled(!busy&&!mutating&&index>=0&&FileDisposal.supported());
        trashMarked.setToolTipText(FileDisposal.supported()?ActionHelp.forButton("Move originals to Recycle Bin…"):"Recycle Bin unavailable; exclusion keeps the originals.");for(JButton b:new JButton[]{selectUnmarked,markSelected,unmarkSelected})b.setEnabled(!mutating);
    }
    void selectUnmarked(){setPlaying(false);setting=true;files.setSelectedIndices(java.util.stream.IntStream.range(0,inputs.size()).filter(i->!marked.contains(inputs.get(i).source())&&!excluded.contains(inputs.get(i).source())).toArray());setting=false;status.setText(files.getSelectedIndices().length+" unmarked files selected");update();}
    void markSelection(boolean value){if(mutating)return;setPlaying(false);int changed=0;for(int i:files.getSelectedIndices()){
        Path path=inputs.get(i).source();if(trashed.contains(path))continue;if(value?marked.add(path):marked.remove(path))changed++;
        if(value){reviewed.remove(path);if(batch!=null)batch.outputs.get(i).reviewed=false;}
    }refreshList();if(index>=0)markedCurrent.setSelected(marked.contains(inputs.get(index).source()));status.setText(changed+" file(s) "+(value?"marked":"unmarked"));update();}
    void setPlaying(boolean value){if(mutating)value=false;playing=value;if(value){followPlayback=true;if(targetPosition>=frame.getMaximum()-1)requestPosition(0);cine.start();}else cine.stop();if(play!=null){play.putClientProperty("FlatLaf.style",value?Theme.STAGE_BUTTON+"; background: #3E5446; foreground: #FFFFFF":Theme.STAGE_BUTTON);if(fpsBox!=null&&fpsBox.isVisible()!=value){fpsBox.setVisible(value);fpsBox.revalidate();}play.setText(compactTransport?"":value?"Pause":"Play");play.setIcon(UiIcons.forAction(value?"Pause":"Play"));}}
    private void redact(){
        if(canvas.areas.isEmpty()){Theme.error(this,new IllegalArgumentException("Enable Draw blackout and drag a rectangle over the image first."));return;}
        if(JOptionPane.showConfirmDialog(this,"Apply these rectangles to ALL "+frameCount+" frames in this instance?\nThe source file stays unchanged. Review the resulting pixels again.","Apply blackout",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        setPlaying(false);List<Shape> areas=new ArrayList<>(canvas.areas);Cleaner.Output output=batch.outputs.get(index);final int selected=index;mutating=true;busy=true;update();status.setText("Applying blackout…");
        new SwingWorker<Void,Void>(){protected Void doInBackground()throws Exception{frames.releaseFile(selected);Cleaner.redact(output,areas);frames.invalidate(selected);return null;}
            protected void done(){mutating=false;busy=false;try{get();requestPosition(starts[selected]);}catch(Exception e){failed=true;Theme.error(ReviewDialog.this,e);update();}}
        }.execute();
    }
    void showReview(){
        if(!SwingUtilities.isEventDispatchThread())throw new IllegalStateException("Open the viewer on the event thread");
        reviewLoop=Toolkit.getDefaultToolkit().getSystemEventQueue().createSecondaryLoop();
        boolean enabled=reviewOwner==null||reviewOwner.isEnabled();
        if(reviewOwner!=null)reviewOwner.setEnabled(false);
        try{setVisible(true);reviewLoop.enter();}
        finally{reviewLoop=null;if(reviewOwner!=null){reviewOwner.setEnabled(enabled);reviewOwner.toFront();reviewOwner.requestFocus();}}
    }
    private void closeReview(){if(mutating)return;dispose();}
    @Override public void dispose(){
        if(!closed){closed=true;setPlaying(false);scrollTimer.stop();frames.close();if(getExtendedState()==Frame.NORMAL){viewSettings.set("viewerWidth",getWidth());viewSettings.set("viewerHeight",getHeight());}mainSplit.remember(viewSettings,"layout.viewer.source.ratio");tagsSplit.remember(viewSettings,"layout.viewer.tags.ratio");try{viewSettings.save();}catch(java.io.IOException ignored){}}
        super.dispose();if(reviewLoop!=null)reviewLoop.exit();
    }
    private void bind(String key,Runnable action){getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key),key);getRootPane().getActionMap().put(key,new AbstractAction(){public void actionPerformed(ActionEvent e){if(!(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() instanceof javax.swing.text.JTextComponent))action.run();}});if(!key.equals("RIGHT")&&!key.equals("LEFT")){files.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key),key);files.getActionMap().put(key,getRootPane().getActionMap().get(key));}}
    private void wheel(MouseWheelEvent e){if(e.isControlDown()){zoom.setValue(Math.max(25,Math.min(400,zoom.getValue()-(int)(e.getPreciseWheelRotation()*12))));}else{wheelRemainder+=e.getPreciseWheelRotation();int steps=(int)wheelRemainder;wheelRemainder-=steps;if(steps!=0)queueStep(steps);}e.consume();}
    void queueStep(int direction){followPlayback=true;setPlaying(false);pendingSteps=Math.max(-1000,Math.min(1000,pendingSteps+direction));scrollTimer.restart();}
    private void drainNavigation(){int steps=pendingSteps;pendingSteps=0;if(steps!=0)step(steps);}
    private void step(int delta){int target=Math.max(0,Math.min(frame.getMaximum()-1,targetPosition+delta)),direction=Integer.signum(delta);while(target>=0&&target<frame.getMaximum()&&excluded.contains(inputs.get(keyAt(target).file()).source()))target+=direction;if(target>=0&&target<frame.getMaximum())requestPosition(target);else setPlaying(false);}
    void navigateFile(int step){followPlayback=true;setPlaying(false);int target=(index<0?0:timelinePositions[index])+step;while(target>=0&&target<inputs.size()&&excluded.contains(inputs.get(timelineOrder[target]).source()))target+=step;if(target>=0&&target<inputs.size())navigateTo(timelineOrder[target]);}
    private void navigateTo(int target){if(target>=0&&target<inputs.size())requestPosition(starts[target]);}
    private List<Cleaner.Instance> markedOrCurrent(){return inputs.stream().filter(i->!trashed.contains(i.source())&&(marked.isEmpty()?index>=0&&i.source().equals(inputs.get(index).source()):marked.contains(i.source()))).toList();}
    void excludeMarked(){if(mutating)return;setPlaying(false);for(var file:markedOrCurrent())excluded.add(file.source());marked.clear();markedCurrent.setSelected(false);if(batch!=null)batch.requiresReclean=true;refreshList();update();status.setText(excluded.size()+" file(s) excluded. Originals remain in place. Close the viewer to apply and clean the remaining selection again.");navigateFile(1);}
    private void recycleMarked(){
        List<Cleaner.Instance> chosen=markedOrCurrent();if(chosen.isEmpty())return;
        JTextArea list=new JTextArea("Move these "+chosen.size()+" ORIGINAL file(s) to Recycle Bin?\n\n"+String.join("\n",chosen.stream().map(i->i.source().toString()).toList())+"\n\nWhole files (all frames/pages) leave their source folders. Source DICOMDIR indexes are not rewritten.\nThere is no permanent-delete fallback. Choose Exclude from export to keep the originals.",12,75);list.setEditable(false);list.setLineWrap(true);list.setWrapStyleWord(true);
        if(JOptionPane.showConfirmDialog(this,Theme.scroll(list),"Move selected originals to Recycle Bin",JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.OK_OPTION)return;
        setPlaying(false);mutating=true;busy=true;update();
        new SwingWorker<FileDisposal.Result,Void>(){protected FileDisposal.Result doInBackground()throws Exception{for(var i:chosen)frames.releaseFile(inputs.indexOf(i));return FileDisposal.recycle(chosen);}
            protected void done(){mutating=false;busy=false;try{FileDisposal.Result r=get();trashed.addAll(r.moved());excluded.addAll(r.moved());marked.removeAll(r.moved());if(batch!=null&&!r.moved().isEmpty())batch.requiresReclean=true;if(!r.failed().isEmpty()){StringBuilder message=new StringBuilder("Some files were not moved:\n");r.failed().forEach((p,reason)->message.append(p.getFileName()).append(": ").append(reason).append('\n'));JOptionPane.showMessageDialog(ReviewDialog.this,message.toString(),"Recycle Bin result",JOptionPane.WARNING_MESSAGE);}refreshList();requestPosition(starts[index]);status.setText(r.moved().size()+" originals moved to Recycle Bin");}catch(Exception e){Theme.error(ReviewDialog.this,e);requestPosition(starts[index]);}update();}
        }.execute();
    }
    final class Canvas extends JPanel {
        BufferedImage raw,display;final List<Rectangle2D.Double> areas=new ArrayList<>();Point2D start;Rectangle2D.Double pending;JScrollPane scroll;
        Canvas(){setBackground(Color.BLACK);setFocusable(true);MouseAdapter mouse=new MouseAdapter(){
            public void mousePressed(MouseEvent e){requestFocusInWindow();
                if(SwingUtilities.isRightMouseButton(e)&&width.isEnabled()){wlStart=e.getPoint();wlWidth=((Number)width.getValue()).doubleValue();wlLevel=((Number)level.getValue()).doubleValue();setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));return;}
                if(SwingUtilities.isLeftMouseButton(e)&&!busy&&draw.isEnabled()&&draw.isSelected()&&raw!=null){start=toImage(e.getPoint());pending=null;}}
            public void mouseDragged(MouseEvent e){
                if(wlStart!=null){double step=Math.max(.5,wlWidth/300d);   // right-drag: horizontal = width (contrast), vertical = level (brightness)
                    setting=true;width.setValue(Math.max(1d,wlWidth+(e.getX()-wlStart.x)*step));level.setValue(wlLevel+(e.getY()-wlStart.y)*step);setting=false;render();return;}
                if(!busy&&start!=null){Point2D end=toImage(e.getPoint());pending=new Rectangle2D.Double(Math.min(start.getX(),end.getX()),Math.min(start.getY(),end.getY()),Math.abs(start.getX()-end.getX()),Math.abs(start.getY()-end.getY()));repaint();}}
            public void mouseReleased(MouseEvent e){if(wlStart!=null){wlStart=null;setCursor(Cursor.getPredefinedCursor(draw.isSelected()?Cursor.CROSSHAIR_CURSOR:Cursor.DEFAULT_CURSOR));return;}if(pending!=null&&pending.width>=1&&pending.height>=1)areas.add(pending);start=null;pending=null;repaint();}
            public void mouseWheelMoved(MouseWheelEvent e){wheel(e);}
        };addMouseListener(mouse);addMouseMotionListener(mouse);addMouseWheelListener(mouse);}
        /** Corner overlays (slice, window/level, zoom). Deliberately no series description or names, so screenshots stay clean. */
        void paintOverlays(Graphics2D g0){
            if(index<0||index>=inputs.size())return;
            Graphics2D g=(Graphics2D)g0.create();g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(Theme.bodyFont.deriveFont((float)Theme.u(12)));FontMetrics m=g.getFontMetrics();Rectangle v=getVisibleRect();int pad=Theme.u(10),lh=m.getHeight();
            Cleaner.Instance in=inputs.get(index);
            String tl=in.modality()+" · Series "+in.seriesNumber()+(in.instanceNumber()>0?" · Image "+in.instanceNumber():"");
            String tr="Slice "+(position+1)+" / "+frame.getMaximum()+(frameCount>1?" · Frame "+(shownFrame+1)+" / "+frameCount:"");
            String bl=shown!=null&&shown.gray?String.format(java.util.Locale.ROOT,"W %.0f  L %.0f",((Number)width.getValue()).doubleValue(),((Number)level.getValue()).doubleValue()):"";
            String br="Zoom "+zoom.getValue()+"%";
            text(g,tl,v.x+pad,v.y+pad+m.getAscent());text(g,tr,v.x+v.width-pad-m.stringWidth(tr),v.y+pad+m.getAscent());
            text(g,bl,v.x+pad,v.y+v.height-pad-lh+m.getAscent());text(g,br,v.x+v.width-pad-m.stringWidth(br),v.y+v.height-pad-lh+m.getAscent());g.dispose();
        }
        private void text(Graphics2D g,String s,int x,int y){if(s.isEmpty())return;g.setColor(new Color(0,0,0,170));g.drawString(s,x+1,y+1);g.setColor(new Color(0xE6E8E3));g.drawString(s,x,y);}
        double scale(){if(raw==null||scroll==null)return 1;Dimension v=scroll.getViewport().getExtentSize();return Math.max(.01,Math.min((v.width-4d)/raw.getWidth(),(v.height-4d)/raw.getHeight())*zoom.getValue()/100d);}
        double ox(){return raw==null?0:Math.max(0,(getWidth()-raw.getWidth()*scale())/2);}
        double oy(){return raw==null?0:Math.max(0,(getHeight()-raw.getHeight()*scale())/2);}
        Point2D toImage(Point p){return new Point2D.Double(Math.max(0,Math.min(raw.getWidth(),(p.x-ox())/scale())),Math.max(0,Math.min(raw.getHeight(),(p.y-oy())/scale())));}
        public Dimension getPreferredSize(){if(raw==null)return new Dimension(600,400);return new Dimension((int)(raw.getWidth()*scale()),(int)(raw.getHeight()*scale()));}
        void render(){
            display=shown==null?null:shown.render(((Number)width.getValue()).doubleValue(),((Number)level.getValue()).doubleValue());revalidate();repaint();
        }
        protected void paintComponent(Graphics g){super.paintComponent(g);if(display==null){g.setColor(Color.LIGHT_GRAY);g.drawString("No image preview · use Report / text or Full metadata",22,40);return;}
            Graphics2D q=(Graphics2D)g.create();q.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            q.translate(ox(),oy());q.scale(scale(),scale());q.drawImage(display,0,0,null);q.setStroke(new BasicStroke((float)(2/scale())));
            for(Rectangle2D area:areas){q.setColor(new Color(50,120,255,120));q.fill(area);q.setColor(Color.CYAN);q.draw(area);}if(pending!=null){q.setColor(Color.CYAN);q.draw(pending);}q.dispose();
            paintOverlays((Graphics2D)g);
        }
    }
}
