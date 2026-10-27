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

/** Explicit per-instance review. No automatic PHI or facial-feature detection is claimed. */
final class ReviewDialog extends JDialog {
    final Cleaner.Batch batch;
    final List<Cleaner.Instance> inputs;
    final JList<String> files=new JList<>();
    final JLabel status=new JLabel("Loading…"),frameText=new JLabel(" ");
    final JLabel fileText=new JLabel(" ");
    final JTextArea reportText=new JTextArea();
    final JCheckBox markedCurrent=new JCheckBox("Mark file (M)");
    final Set<Path> marked=new LinkedHashSet<>(),excluded=new LinkedHashSet<>(),trashed=new LinkedHashSet<>();
    final JButton removeMarked,trashMarked,restore,maximize;
    Rectangle restoreBounds;
    final javax.swing.Timer scrollTimer;
    int pendingSteps;
    double wheelRemainder;
    ReportPreview report;
    final MetadataPanel sourceTags=new MetadataPanel(),cleanTags=new MetadataPanel();
    final Canvas canvas=new Canvas();
    final JSlider frame=new JSlider(1,1,1),zoom=new JSlider(25,400,100);
    final JSpinner width=new JSpinner(new SpinnerNumberModel(4096d,1d,1e12,10d)),level=new JSpinner(new SpinnerNumberModel(2048d,-1e12,1e12,10d));
    final JCheckBox textClear=new JCheckBox("No identifying text remains in any frame"),faceClear=new JCheckBox("Recognizable facial features are absent or have been removed");
    final JToggleButton draw=new JToggleButton("Draw blackout");
    final JButton approve,redact,reset,close,previous,next;
    final Map<Integer,BitSet> visited=new HashMap<>();
    SourceImage image;
    AttributeList attributes;
    boolean busy,setting,failed;
    int index=-1,frameCount;
    boolean closed;
    ReviewDialog(Window owner,Cleaner.Batch batch){this(owner,batch,batch.outputs.stream().map(o->o.input).toList());}
    ReviewDialog(Window owner,Cleaner.Instance input){this(owner,null,List.of(input));}
    ReviewDialog(Window owner,List<Cleaner.Instance> inputs,Cleaner.Instance selected){this(owner,null,inputs);if(selected!=null){int i=inputs.indexOf(selected);if(i>0)SwingUtilities.invokeLater(()->navigateTo(i));}}
    private ReviewDialog(Window owner,Cleaner.Batch batch,List<Cleaner.Instance> inputs){
        super(owner,batch==null?"Inspect original images and reports":"Review cleaned images",ModalityType.APPLICATION_MODAL);
        this.batch=batch;this.inputs=inputs;setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);setMinimumSize(new Dimension(1000,720));
        scrollTimer=new javax.swing.Timer(35,e->drainNavigation());scrollTimer.setRepeats(false);
        Theme.Background root=new Theme.Background(new BorderLayout(10,10));root.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));setContentPane(root);
        JPanel heading=new JPanel(new BorderLayout(6,6));heading.setOpaque(false);heading.add(Theme.label(batch==null?"Original images & reports":"Image & metadata review",23,true),BorderLayout.NORTH);heading.add(fileText);maximize=Theme.button("Maximize",false,this::maximizeView);heading.add(maximize,BorderLayout.EAST);root.add(heading,BorderLayout.NORTH);
        files.setFixedCellHeight(29);files.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);refreshList();
        files.addListSelectionListener(e->{if(!e.getValueIsAdjusting()&&!setting&&!busy)load(files.getSelectedIndex());});
        JScrollPane left=Theme.scroll(files);left.setPreferredSize(new Dimension(235,400));left.setMinimumSize(new Dimension(180,200));
        JPanel preview=new JPanel(new BorderLayout(8,8));preview.setOpaque(false);
        JScrollPane imageScroll=new JScrollPane(canvas);imageScroll.setWheelScrollingEnabled(false);imageScroll.getViewport().setBackground(Color.BLACK);canvas.scroll=imageScroll;preview.add(imageScroll);imageScroll.addMouseWheelListener(this::wheel);
        JPanel controls=new JPanel();controls.setLayout(new BoxLayout(controls,BoxLayout.Y_AXIS));controls.setOpaque(false);
        frame.setPaintTicks(false);frame.setPreferredSize(new Dimension(300,24));
        previous=Theme.button("Previous image",false,()->queueStep(-1));
        next=Theme.button("Next image",false,()->queueStep(1));
        controls.add(Theme.row(previous,frame,next,frameText));
        zoom.setPreferredSize(new Dimension(150,24));
        reset=Theme.button("Auto window / Fit",false,this::autoWindow);
        controls.add(Theme.row(new JLabel("Zoom"),zoom,new JLabel("Width"),width,new JLabel("Level"),level,reset));
        draw.addActionListener(e->canvas.setCursor(Cursor.getPredefinedCursor(draw.isSelected()?Cursor.CROSSHAIR_CURSOR:Cursor.DEFAULT_CURSOR)));
        redact=Theme.button("Apply blackout to all frames",true,this::redact);
        if(batch!=null)controls.add(Theme.row(draw,Theme.button("Clear rectangles",false,()->{canvas.areas.clear();canvas.repaint();}),redact));preview.add(controls,BorderLayout.SOUTH);
        JTabbedPane tabs=new JTabbedPane();tabs.addTab("Image review",preview);
        reportText.setEditable(false);reportText.setLineWrap(true);reportText.setWrapStyleWord(true);reportText.setMargin(new Insets(12,12,12,12));tabs.addTab("Report / text",Theme.scroll(reportText));
        JSplitPane tags=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,Theme.card("Original · every tag",sourceTags),Theme.card("Cleaned · every tag",cleanTags));tags.setResizeWeight(.5);tabs.addTab("Full metadata",tags);
        tabs.setMinimumSize(new Dimension(520,300));JSplitPane main=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,left,tabs);main.setResizeWeight(.18);root.add(main);
        SwingUtilities.invokeLater(()->main.setDividerLocation(235));
        addComponentListener(new ComponentAdapter(){public void componentResized(ComponentEvent e){int direction=getHeight()>getWidth()?JSplitPane.VERTICAL_SPLIT:JSplitPane.HORIZONTAL_SPLIT;if(tags.getOrientation()!=direction){tags.setOrientation(direction);tags.setDividerLocation(.5);}root.revalidate();}});
        JPanel bottom=new JPanel();bottom.setLayout(new BoxLayout(bottom,BoxLayout.Y_AXIS));bottom.setOpaque(false);
        markedCurrent.setOpaque(false);markedCurrent.addActionListener(e->{if(index>=0){Path p=inputs.get(index).source();if(markedCurrent.isSelected())marked.add(p);else marked.remove(p);refreshList();}});
        removeMarked=Theme.button("Exclude marked from export",false,this::excludeMarked);
        trashMarked=Theme.button("Move originals to Recycle Bin…",false,this::recycleMarked);
        restore=Theme.button("Undo exclusion",false,()->{if(index>=0&&!trashed.contains(inputs.get(index).source())){excluded.remove(inputs.get(index).source());refreshList();update();}});
        bottom.add(Theme.row(markedCurrent,removeMarked,restore,trashMarked));
        textClear.setOpaque(false);faceClear.setOpaque(false);if(batch!=null){bottom.add(textClear);bottom.add(faceClear);}
        if(batch!=null)bottom.add(new JLabel("Review every frame at useful window settings. Head CT/MRI may need external 3D defacing before import."));
        approve=Theme.button("Approve this instance & next",true,this::approve);
        close=Theme.button("Close",false,this::closeReview);
        JPanel buttons=Theme.row(approve,Theme.button("Undo approval",false,()->{if(batch!=null&&index>=0){batch.outputs.get(index).reviewed=false;refreshList();}}),close);
        if(batch!=null&&batch.unresolvedReferences>0)buttons.add(Theme.button("Reference warning",false,()->{
            batch.referencesAcknowledged=JOptionPane.showConfirmDialog(this,batch.unresolvedReferences+" referenced instances are outside this selection.\nTheir UIDs were remapped but they are not included in the export. Continue with an incomplete image set?","External references",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE)==JOptionPane.YES_OPTION;
        }));
        if(batch==null){buttons.remove(0);buttons.remove(0);}bottom.add(buttons);bottom.add(status);for(Component c:bottom.getComponents())if(c instanceof JComponent j)j.setAlignmentX(0f);for(Component c:controls.getComponents())if(c instanceof JComponent j)j.setAlignmentX(0f);root.add(bottom,BorderLayout.SOUTH);
        frame.addChangeListener(e->{if(!setting&&!frame.getValueIsAdjusting()&&(image!=null||report!=null)&&!busy)showFrame();});
        zoom.addChangeListener(e->{canvas.revalidate();canvas.repaint();});width.addChangeListener(e->{if(!setting)canvas.render();});level.addChangeListener(e->{if(!setting)canvas.render();});
        textClear.addActionListener(e->update());faceClear.addActionListener(e->update());
        addWindowListener(new WindowAdapter(){public void windowClosing(WindowEvent e){closeReview();}});
        bind("RIGHT",()->queueStep(1));bind("LEFT",()->queueStep(-1));bind("PAGE_DOWN",()->navigateFile(1));bind("PAGE_UP",()->navigateFile(-1));bind("M",()->{if(markedCurrent.isEnabled())markedCurrent.doClick();});
        Dimension screen=Toolkit.getDefaultToolkit().getScreenSize();setSize(Math.min(1480,screen.width),Math.min(970,screen.height-40));setLocationRelativeTo(owner);
        files.setSelectedIndex(0);if(index<0)load(0);
    }
    private void refreshList(){
        int selected=files.getSelectedIndex();setting=true;
        String[] labels=new String[inputs.size()];for(int i=0;i<labels.length;i++){Cleaner.Instance x=inputs.get(i);labels[i]=(marked.contains(x.source())?"☑ ":"☐ ")+(excluded.contains(x.source())?"[Excluded] ":batch!=null&&batch.outputs.get(i).reviewed?"✓ ":"")+(i+1)+" · "+x.modality()+" · "+x.source().getFileName();}
        files.setListData(labels);if(selected>=0)files.setSelectedIndex(selected);setting=false;
    }
    private void releaseImage(){if(image!=null)try{image.close();}catch(Throwable ignored){}if(report!=null)try{report.close();}catch(Exception ignored){}image=null;report=null;canvas.raw=null;canvas.display=null;}
    private void load(int selected){
        if(selected<0||busy)return;index=selected;busy=true;failed=false;releaseImage();frameCount=0;attributes=null;sourceTags.setData(null);cleanTags.setData(null);reportText.setText("");frameText.setText(" ");canvas.areas.clear();canvas.repaint();textClear.setSelected(false);faceClear.setSelected(false);markedCurrent.setSelected(marked.contains(inputs.get(index).source()));update();status.setText("Loading instance "+(index+1));
        fileText.setText("File "+(index+1)+" / "+inputs.size()+" · "+inputs.get(index).relativePath());fileText.setToolTipText(inputs.get(index).source().toString());
        if(trashed.contains(inputs.get(index).source())){busy=false;failed=true;canvas.repaint();status.setText("This original was moved to Recycle Bin.");update();return;}
        new SwingWorker<LoadedView,Void>(){
            protected LoadedView doInBackground()throws Exception {
                Cleaner.Instance input=inputs.get(index);AttributeList original=DicomIO.read(input.source()).list(),cleaned=null;
                if(batch!=null){Cleaner.Output output=batch.outputs.get(index);if(!DicomIO.hash(output.file).equals(output.sha256))throw new java.io.IOException("Cleaned data changed. Clean this selection again.");cleaned=DicomIO.read(output.file).list();}
                AttributeList selected=cleaned==null?original:cleaned;SourceImage pixels=null;ReportPreview document=null;
                try{
                    if(selected.getPixelData()!=null){pixels=new SourceImage(selected);if(pixels.getNumberOfBufferedImages()<1||pixels.getBufferedImage(0)==null)throw new java.io.IOException("No frames could be decoded.");}
                    else{document=ReportPreview.open(selected);if(document!=null)document.page(0);}
                    return new LoadedView(original,cleaned,pixels,document,false);
                }catch(Exception e){if(pixels!=null)try{pixels.close();}catch(Throwable ignored){}if(document!=null)try{document.close();}catch(Exception ignored){}return new LoadedView(original,cleaned,null,null,true);}
            }
            protected void done(){
                try{
                    LoadedView loaded=get();sourceTags.setData(loaded.original);cleanTags.setData(loaded.cleaned);attributes=loaded.cleaned==null?loaded.original:loaded.cleaned;
                    image=loaded.image;report=loaded.report;failed=loaded.failed;frameCount=image!=null?image.getNumberOfBufferedImages():report==null?0:report.pages();
                    reportText.setText(image!=null?"This file contains image pixels. Use Image review.\n\n"+inputs.get(index).problem():ReportPreview.text(attributes));reportText.setCaretPosition(0);
                    setting=true;frame.setMaximum(Math.max(1,frameCount));frame.setValue(1);setting=false;
                    busy=false;if(failed){canvas.repaint();status.setText("This preview could not be decoded. Inspect Report / text or Full metadata; marking and exclusion are available.");}else if(image!=null||report!=null){autoWindow();showFrame();}else{canvas.repaint();status.setText(DicomIO.isRegistration(inputs.get(index).sopClass())?"Registration object: inspect metadata and related images.":"Non-image report/object: inspect Report / text and Full metadata; mark unwanted files for exclusion.");}
                }catch(Exception e){busy=false;failed=true;releaseImage();status.setText("Unable to display this instance. Approval blocked; you can still mark and exclude it.");Theme.error(ReviewDialog.this,e);}
                update();if(pendingSteps!=0&&!busy)scrollTimer.restart();
            }
        }.execute();
    }
    record LoadedView(AttributeList original,AttributeList cleaned,SourceImage image,ReportPreview report,boolean failed){}
    private void autoWindow(){
        setting=true;
        if(image!=null){double slope=image.getModalityTransform().getRescaleSlope(frame.getValue()-1),intercept=image.getModalityTransform().getRescaleIntercept(frame.getValue()-1);width.setValue(Math.max(1d,Math.abs((image.getMaximum()-image.getMinimum())*slope)));level.setValue((image.getMaximum()+image.getMinimum())/2d*slope+intercept);}
        zoom.setValue(100);setting=false;canvas.render();
    }
    private void showFrame(){
        if(busy)return;busy=true;update();final int selectedFrame=frame.getValue()-1;
        new SwingWorker<BufferedImage,Void>(){
            protected BufferedImage doInBackground()throws Exception{return report!=null?report.page(selectedFrame):image.getBufferedImage(selectedFrame);}
            protected void done(){busy=false;try{canvas.raw=get();if(canvas.raw==null)throw new IllegalArgumentException("Frame could not be decoded.");canvas.render();visited.computeIfAbsent(index,k->new BitSet()).set(selectedFrame);
                frameText.setText((report!=null?"Page ":"Frame ")+(selectedFrame+1)+" / "+frameCount);status.setText("Wheel / ← →: next image or frame · Page Up/Down: next file · Ctrl+wheel: zoom · M: mark file");
            }catch(Exception e){failed=true;Theme.error(ReviewDialog.this,e);}update();if(pendingSteps!=0)scrollTimer.restart();}
        }.execute();
    }
    private void update(){
        width.setEnabled(!busy&&image!=null);level.setEnabled(!busy&&image!=null);reset.setEnabled(!busy&&(image!=null||report!=null));files.setEnabled(!busy);frame.setEnabled(!busy&&(image!=null||report!=null));draw.setEnabled(!busy&&batch!=null&&image!=null);
        redact.setEnabled(!busy&&batch!=null&&image!=null);previous.setEnabled(inputs.size()>1||frameCount>1);next.setEnabled(inputs.size()>1||frameCount>1);close.setEnabled(!busy);
        markedCurrent.setEnabled(!busy&&index>=0&&!trashed.contains(inputs.get(index).source()));removeMarked.setEnabled(!busy);restore.setEnabled(!busy&&index>=0&&excluded.contains(inputs.get(index).source())&&!trashed.contains(inputs.get(index).source()));trashMarked.setEnabled(!busy&&FileDisposal.supported());
        trashMarked.setToolTipText(FileDisposal.supported()?"Moves explicitly selected original .dcm files to Windows Recycle Bin":"Recycle Bin is unavailable; use exclusion. Permanent deletion is not performed.");
        boolean allFrames=frameCount==0||visited.getOrDefault(index,new BitSet()).cardinality()==frameCount;
        approve.setEnabled(batch!=null&&!busy&&!failed&&index>=0&&!excluded.contains(inputs.get(index).source())&&allFrames&&textClear.isSelected()&&faceClear.isSelected());
    }
    private void approve(){
        if(!approve.isEnabled())return;
        if(!canvas.areas.isEmpty()){Theme.error(this,new IllegalArgumentException("Drawn rectangles are only a preview. Apply the blackout, then review the result."));return;}
        Cleaner.Output output=batch.outputs.get(index);output.reviewed=true;refreshList();
        for(int i=1;i<=inputs.size();i++){int n=(index+i)%inputs.size();if(!batch.outputs.get(n).reviewed){files.setSelectedIndex(n);return;}}
        status.setText("All "+inputs.size()+" instances approved. Close review to export.");update();
    }
    private void redact(){
        if(canvas.areas.isEmpty()){Theme.error(this,new IllegalArgumentException("Enable Draw blackout and drag a rectangle over the image first."));return;}
        if(JOptionPane.showConfirmDialog(this,"Apply these rectangles to ALL "+frameCount+" frames in this instance?\nThe source file stays unchanged. Review the resulting pixels again.","Apply blackout",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
        List<Shape> areas=new ArrayList<>(canvas.areas);Cleaner.Output output=batch.outputs.get(index);releaseImage();busy=true;update();status.setText("Applying blackout…");
        new SwingWorker<Void,Void>(){protected Void doInBackground()throws Exception{Cleaner.redact(output,areas);return null;}
            protected void done(){busy=false;try{get();visited.remove(index);load(index);}catch(Exception e){failed=true;Theme.error(ReviewDialog.this,e);update();}}
        }.execute();
    }
    private void closeReview(){if(busy)return;closed=true;scrollTimer.stop();releaseImage();dispose();}
    private void maximizeView(){
        if(restoreBounds!=null){setBounds(restoreBounds);restoreBounds=null;maximize.setText("Maximize");return;}
        restoreBounds=getBounds();GraphicsConfiguration configuration=getGraphicsConfiguration();Rectangle bounds=new Rectangle(configuration.getBounds());Insets insets=Toolkit.getDefaultToolkit().getScreenInsets(configuration);
        bounds.x+=insets.left;bounds.y+=insets.top;bounds.width-=insets.left+insets.right;bounds.height-=insets.top+insets.bottom;setBounds(bounds);maximize.setText("Restore");
    }
    private void bind(String key,Runnable action){getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key),key);getRootPane().getActionMap().put(key,new AbstractAction(){public void actionPerformed(ActionEvent e){if(!(KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner() instanceof javax.swing.text.JTextComponent))action.run();}});files.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key),key);files.getActionMap().put(key,getRootPane().getActionMap().get(key));}
    private void wheel(MouseWheelEvent e){if(e.isControlDown()){zoom.setValue(Math.max(25,Math.min(400,zoom.getValue()-(int)(e.getPreciseWheelRotation()*12))));}else{wheelRemainder+=e.getPreciseWheelRotation();int steps=(int)wheelRemainder;wheelRemainder-=steps;if(steps!=0)queueStep(steps);}e.consume();}
    void queueStep(int direction){pendingSteps=Math.max(-50,Math.min(50,pendingSteps+direction));scrollTimer.restart();}
    private void drainNavigation(){if(busy)return;if(pendingSteps==0)return;int step=Integer.signum(pendingSteps);pendingSteps-=step;
        int target=frame.getValue()+step;if((image!=null||report!=null)&&!excluded.contains(inputs.get(index).source())&&frameCount>0&&target>=1&&target<=frameCount)frame.setValue(target);else navigateFile(step);
        if(pendingSteps!=0&&!busy)scrollTimer.restart();}
    void navigateFile(int step){if(busy){pendingSteps+=step;return;}int target=index+step;while(target>=0&&target<inputs.size()&&excluded.contains(inputs.get(target).source()))target+=step;if(target>=0&&target<inputs.size())files.setSelectedIndex(target);}
    private void navigateTo(int target){if(closed)return;if(busy){javax.swing.Timer retry=new javax.swing.Timer(50,e->navigateTo(target));retry.setRepeats(false);retry.start();}else files.setSelectedIndex(target);}
    private List<Cleaner.Instance> markedOrCurrent(){return inputs.stream().filter(i->!trashed.contains(i.source())&&(marked.isEmpty()?index>=0&&i.source().equals(inputs.get(index).source()):marked.contains(i.source()))).toList();}
    void excludeMarked(){if(busy)return;for(var file:markedOrCurrent())excluded.add(file.source());marked.clear();markedCurrent.setSelected(false);if(batch!=null)batch.requiresReclean=true;refreshList();update();status.setText(excluded.size()+" file(s) excluded. Originals remain in place. Close the viewer to apply and clean the remaining selection again.");navigateFile(1);}
    private void recycleMarked(){
        List<Cleaner.Instance> chosen=markedOrCurrent();if(chosen.isEmpty())return;
        JTextArea list=new JTextArea("Move these "+chosen.size()+" ORIGINAL file(s) to Recycle Bin?\n\n"+String.join("\n",chosen.stream().map(i->i.source().toString()).toList())+"\n\nWhole files (all frames/pages) leave their source folders. Source DICOMDIR indexes are not rewritten.\nThere is no permanent-delete fallback. Choose Exclude from export to keep the originals.",12,75);list.setEditable(false);list.setLineWrap(true);list.setWrapStyleWord(true);
        if(JOptionPane.showConfirmDialog(this,Theme.scroll(list),"Move selected originals to Recycle Bin",JOptionPane.OK_CANCEL_OPTION,JOptionPane.WARNING_MESSAGE)!=JOptionPane.OK_OPTION)return;
        releaseImage();busy=true;update();
        new SwingWorker<FileDisposal.Result,Void>(){protected FileDisposal.Result doInBackground()throws Exception{return FileDisposal.recycle(chosen);}
            protected void done(){busy=false;try{FileDisposal.Result r=get();trashed.addAll(r.moved());excluded.addAll(r.moved());marked.removeAll(r.moved());if(batch!=null&&!r.moved().isEmpty())batch.requiresReclean=true;if(!r.failed().isEmpty()){StringBuilder message=new StringBuilder("Some files were not moved:\n");r.failed().forEach((p,reason)->message.append(p.getFileName()).append(": ").append(reason).append('\n'));JOptionPane.showMessageDialog(ReviewDialog.this,message.toString(),"Recycle Bin result",JOptionPane.WARNING_MESSAGE);}refreshList();load(index);status.setText(r.moved().size()+" originals moved to Recycle Bin");}catch(Exception e){Theme.error(ReviewDialog.this,e);load(index);}update();}
        }.execute();
    }
    final class Canvas extends JPanel {
        BufferedImage raw,display;final List<Rectangle2D.Double> areas=new ArrayList<>();Point2D start;Rectangle2D.Double pending;JScrollPane scroll;
        Canvas(){setBackground(Color.BLACK);setFocusable(true);MouseAdapter mouse=new MouseAdapter(){
            public void mousePressed(MouseEvent e){requestFocusInWindow();if(draw.isSelected()&&raw!=null){start=toImage(e.getPoint());pending=null;}}
            public void mouseDragged(MouseEvent e){if(start!=null){Point2D end=toImage(e.getPoint());pending=new Rectangle2D.Double(Math.min(start.getX(),end.getX()),Math.min(start.getY(),end.getY()),Math.abs(start.getX()-end.getX()),Math.abs(start.getY()-end.getY()));repaint();}}
            public void mouseReleased(MouseEvent e){if(pending!=null&&pending.width>=1&&pending.height>=1)areas.add(pending);start=null;pending=null;repaint();}
            public void mouseWheelMoved(MouseWheelEvent e){wheel(e);}
        };addMouseListener(mouse);addMouseMotionListener(mouse);addMouseWheelListener(mouse);}
        double scale(){if(raw==null||scroll==null)return 1;Dimension v=scroll.getViewport().getExtentSize();return Math.max(.01,Math.min((v.width-4d)/raw.getWidth(),(v.height-4d)/raw.getHeight())*zoom.getValue()/100d);}
        double ox(){return raw==null?0:Math.max(0,(getWidth()-raw.getWidth()*scale())/2);}
        double oy(){return raw==null?0:Math.max(0,(getHeight()-raw.getHeight()*scale())/2);}
        Point2D toImage(Point p){return new Point2D.Double(Math.max(0,Math.min(raw.getWidth(),(p.x-ox())/scale())),Math.max(0,Math.min(raw.getHeight(),(p.y-oy())/scale())));}
        public Dimension getPreferredSize(){if(raw==null)return new Dimension(600,400);return new Dimension((int)(raw.getWidth()*scale()),(int)(raw.getHeight()*scale()));}
        void render(){
            if(raw==null)return;
            if(image==null||!image.isGrayscale())display=raw;
            else {
                double w=((Number)width.getValue()).doubleValue(),c=((Number)level.getValue()).doubleValue();
                ModalityTransform modality=image.getModalityTransform();int currentFrame=frame.getValue()-1;
                display=WindowCenterAndWidth.applyWindowCenterAndWidthLinear(raw,c,w,image.isSigned(),image.isInverted(),
                    modality.getRescaleSlope(currentFrame),modality.getRescaleIntercept(currentFrame),image.isPadded(),image.getPadValue(),image.getPadRangeLimit(),false);
            }revalidate();repaint();
        }
        protected void paintComponent(Graphics g){super.paintComponent(g);if(display==null){g.setColor(Color.LIGHT_GRAY);g.drawString("No image preview · use Report / text or Full metadata",22,40);return;}
            Graphics2D q=(Graphics2D)g.create();q.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            q.translate(ox(),oy());q.scale(scale(),scale());q.drawImage(display,0,0,null);q.setStroke(new BasicStroke((float)(2/scale())));
            for(Rectangle2D area:areas){q.setColor(new Color(50,120,255,120));q.fill(area);q.setColor(Color.CYAN);q.draw(area);}if(pending!=null){q.setColor(Color.CYAN);q.draw(pending);}q.dispose();
        }
    }
}
