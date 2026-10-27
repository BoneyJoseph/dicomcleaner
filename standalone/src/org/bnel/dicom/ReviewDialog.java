package org.bnel.dicom;

import com.pixelmed.dicom.*;
import com.pixelmed.display.SourceImage;
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
    ReviewDialog(Window owner,Cleaner.Batch batch){this(owner,batch,batch.outputs.stream().map(o->o.input).toList());}
    ReviewDialog(Window owner,Cleaner.Instance input){this(owner,null,List.of(input));}
    private ReviewDialog(Window owner,Cleaner.Batch batch,List<Cleaner.Instance> inputs){
        super(owner,batch==null?"Review original images":"Review cleaned images",ModalityType.APPLICATION_MODAL);
        this.batch=batch;this.inputs=inputs;setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);setMinimumSize(new Dimension(1000,720));
        Theme.Background root=new Theme.Background(new BorderLayout(10,10));root.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));setContentPane(root);
        root.add(Theme.label(batch==null?"Original images · read only":"Image & metadata review",23,true),BorderLayout.NORTH);
        files.setFixedCellHeight(29);files.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);refreshList();
        files.addListSelectionListener(e->{if(!e.getValueIsAdjusting()&&!setting&&!busy)load(files.getSelectedIndex());});
        JScrollPane left=Theme.scroll(files);left.setPreferredSize(new Dimension(235,400));left.setMinimumSize(new Dimension(180,200));
        JPanel preview=new JPanel(new BorderLayout(8,8));preview.setOpaque(false);
        JScrollPane imageScroll=new JScrollPane(canvas);imageScroll.getViewport().setBackground(Color.BLACK);canvas.scroll=imageScroll;preview.add(imageScroll);
        JPanel controls=new JPanel();controls.setLayout(new BoxLayout(controls,BoxLayout.Y_AXIS));controls.setOpaque(false);
        frame.setPaintTicks(false);frame.setPreferredSize(new Dimension(300,24));
        previous=Theme.button("Previous frame",false,()->frame.setValue(Math.max(1,frame.getValue()-1)));
        next=Theme.button("Next frame",false,()->frame.setValue(Math.min(frameCount,frame.getValue()+1)));
        controls.add(Theme.row(previous,frame,next,frameText));
        zoom.setPreferredSize(new Dimension(150,24));
        reset=Theme.button("Auto window / Fit",false,()->{if(image!=null){setting=true;width.setValue(Math.max(1d,image.getMaximum()-image.getMinimum()));level.setValue((image.getMaximum()+image.getMinimum())/2d);zoom.setValue(100);setting=false;canvas.render();}});
        controls.add(Theme.row(new JLabel("Zoom"),zoom,new JLabel("Width"),width,new JLabel("Level"),level,reset));
        draw.addActionListener(e->canvas.setCursor(Cursor.getPredefinedCursor(draw.isSelected()?Cursor.CROSSHAIR_CURSOR:Cursor.DEFAULT_CURSOR)));
        redact=Theme.button("Apply blackout to all frames",true,this::redact);
        controls.add(Theme.row(draw,Theme.button("Clear rectangles",false,()->{canvas.areas.clear();canvas.repaint();}),redact));preview.add(controls,BorderLayout.SOUTH);
        JTabbedPane tabs=new JTabbedPane();tabs.addTab("Image review",preview);
        JSplitPane tags=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,Theme.card("Original · every tag",sourceTags),Theme.card("Cleaned · every tag",cleanTags));tags.setResizeWeight(.5);tabs.addTab("Full metadata",tags);
        tabs.setMinimumSize(new Dimension(520,300));JSplitPane main=new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,left,tabs);main.setResizeWeight(.18);root.add(main);
        SwingUtilities.invokeLater(()->main.setDividerLocation(235));
        addComponentListener(new ComponentAdapter(){public void componentResized(ComponentEvent e){int direction=getHeight()>getWidth()?JSplitPane.VERTICAL_SPLIT:JSplitPane.HORIZONTAL_SPLIT;if(tags.getOrientation()!=direction){tags.setOrientation(direction);tags.setDividerLocation(.5);}root.revalidate();}});
        JPanel bottom=new JPanel();bottom.setLayout(new BoxLayout(bottom,BoxLayout.Y_AXIS));bottom.setOpaque(false);
        textClear.setOpaque(false);faceClear.setOpaque(false);bottom.add(textClear);bottom.add(faceClear);
        bottom.add(new JLabel("Review every frame at useful window settings. Head CT/MRI may need external 3D defacing before import."));
        approve=Theme.button("Approve this instance & next",true,this::approve);
        close=Theme.button("Close",false,this::closeReview);
        JPanel buttons=Theme.row(approve,Theme.button("Undo approval",false,()->{if(batch!=null&&index>=0){batch.outputs.get(index).reviewed=false;refreshList();}}),close);
        if(batch!=null&&batch.unresolvedReferences>0)buttons.add(Theme.button("Reference warning",false,()->{
            batch.referencesAcknowledged=JOptionPane.showConfirmDialog(this,batch.unresolvedReferences+" referenced instances are outside this selection.\nTheir UIDs were remapped but they are not included in the export. Continue with an incomplete image set?","External references",JOptionPane.YES_NO_OPTION,JOptionPane.WARNING_MESSAGE)==JOptionPane.YES_OPTION;
        }));
        bottom.add(buttons);bottom.add(status);root.add(bottom,BorderLayout.SOUTH);
        frame.addChangeListener(e->{if(!setting&&!frame.getValueIsAdjusting()&&image!=null&&!busy)showFrame();});
        zoom.addChangeListener(e->{canvas.revalidate();canvas.repaint();});width.addChangeListener(e->{if(!setting)canvas.render();});level.addChangeListener(e->{if(!setting)canvas.render();});
        textClear.addActionListener(e->update());faceClear.addActionListener(e->update());
        addWindowListener(new WindowAdapter(){public void windowClosing(WindowEvent e){closeReview();}});
        Dimension screen=Toolkit.getDefaultToolkit().getScreenSize();setSize(Math.min(1480,screen.width),Math.min(970,screen.height-40));setLocationRelativeTo(owner);
        files.setSelectedIndex(0);if(index<0)load(0);
    }
    private void refreshList(){
        int selected=files.getSelectedIndex();setting=true;
        String[] labels=new String[inputs.size()];for(int i=0;i<labels.length;i++){Cleaner.Instance x=inputs.get(i);labels[i]=(batch!=null&&batch.outputs.get(i).reviewed?"✓ ":"○ ")+(i+1)+" · "+x.modality()+" · "+x.frames()+" frame(s)";}
        files.setListData(labels);if(selected>=0)files.setSelectedIndex(selected);setting=false;
    }
    private void releaseImage(){if(image!=null)try{image.close();}catch(Throwable ignored){}image=null;canvas.raw=null;canvas.display=null;}
    private void load(int selected){
        if(selected<0||busy)return;index=selected;busy=true;failed=false;releaseImage();canvas.areas.clear();textClear.setSelected(false);faceClear.setSelected(false);update();status.setText("Loading instance "+(index+1));
        new SwingWorker<AttributeList[],Void>(){
            protected AttributeList[] doInBackground()throws Exception {
                Cleaner.Instance input=inputs.get(index);AttributeList original=DicomIO.read(input.source()).list();
                if(batch==null)return new AttributeList[]{original,null};
                Cleaner.Output output=batch.outputs.get(index);
                if(!DicomIO.hash(output.file).equals(output.sha256))throw new java.io.IOException("Cleaned data changed. Clean this selection again.");
                return new AttributeList[]{original,DicomIO.read(output.file).list()};
            }
            protected void done(){
                try{AttributeList[] lists=get();sourceTags.setData(lists[0]);cleanTags.setData(lists[1]);attributes=lists[1]==null?lists[0]:lists[1];
                    boolean pixels=attributes.getPixelData()!=null;
                    image=pixels?new SourceImage(attributes):null;frameCount=image==null?0:image.getNumberOfBufferedImages();
                    if(pixels&&frameCount<1)throw new java.io.IOException("No frames could be decoded. This instance cannot be approved.");
                    setting=true;frame.setMaximum(Math.max(1,frameCount));frame.setValue(1);setting=false;
                    busy=false;if(image!=null){showFrame();reset.doClick();}else{canvas.repaint();status.setText("Registration object: inspect the full metadata and referenced CT/MRI images.");}
                }catch(Exception e){busy=false;failed=true;releaseImage();status.setText("Unable to display this instance. Approval blocked.");Theme.error(ReviewDialog.this,e);}
                update();
            }
        }.execute();
    }
    private void showFrame(){
        try{canvas.raw=image.getBufferedImage(frame.getValue()-1);if(canvas.raw==null)throw new IllegalArgumentException("Frame could not be decoded.");
            canvas.render();visited.computeIfAbsent(index,k->new BitSet()).set(frame.getValue()-1);
            frameText.setText(frame.getValue()+" / "+frameCount);status.setText("Viewed "+visited.get(index).cardinality()+" / "+frameCount+" frames · Drag a rectangle to mark pixels for blackout");
        }catch(Exception e){failed=true;Theme.error(this,e);}update();
    }
    private void update(){
        files.setEnabled(!busy);frame.setEnabled(!busy&&image!=null);draw.setEnabled(!busy&&batch!=null&&image!=null);
        redact.setEnabled(!busy&&batch!=null&&image!=null);previous.setEnabled(!busy&&image!=null);next.setEnabled(!busy&&image!=null);close.setEnabled(!busy);
        boolean allFrames=frameCount==0||visited.getOrDefault(index,new BitSet()).cardinality()==frameCount;
        approve.setEnabled(batch!=null&&!busy&&!failed&&index>=0&&allFrames&&textClear.isSelected()&&faceClear.isSelected());
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
    private void closeReview(){if(busy)return;releaseImage();dispose();}
    final class Canvas extends JPanel {
        BufferedImage raw,display;final List<Rectangle2D.Double> areas=new ArrayList<>();Point2D start;Rectangle2D.Double pending;JScrollPane scroll;
        Canvas(){setBackground(Color.BLACK);MouseAdapter mouse=new MouseAdapter(){
            public void mousePressed(MouseEvent e){if(draw.isSelected()&&raw!=null){start=toImage(e.getPoint());pending=null;}}
            public void mouseDragged(MouseEvent e){if(start!=null){Point2D end=toImage(e.getPoint());pending=new Rectangle2D.Double(Math.min(start.getX(),end.getX()),Math.min(start.getY(),end.getY()),Math.abs(start.getX()-end.getX()),Math.abs(start.getY()-end.getY()));repaint();}}
            public void mouseReleased(MouseEvent e){if(pending!=null&&pending.width>=1&&pending.height>=1)areas.add(pending);start=null;pending=null;repaint();}
            public void mouseWheelMoved(MouseWheelEvent e){if(!busy)frame.setValue(Math.max(1,Math.min(frameCount,frame.getValue()+e.getWheelRotation())));}
        };addMouseListener(mouse);addMouseMotionListener(mouse);addMouseWheelListener(mouse);}
        double scale(){if(raw==null||scroll==null)return 1;Dimension v=scroll.getViewport().getExtentSize();return Math.max(.01,Math.min((v.width-4d)/raw.getWidth(),(v.height-4d)/raw.getHeight())*zoom.getValue()/100d);}
        double ox(){return raw==null?0:Math.max(0,(getWidth()-raw.getWidth()*scale())/2);}
        double oy(){return raw==null?0:Math.max(0,(getHeight()-raw.getHeight()*scale())/2);}
        Point2D toImage(Point p){return new Point2D.Double(Math.max(0,Math.min(raw.getWidth(),(p.x-ox())/scale())),Math.max(0,Math.min(raw.getHeight(),(p.y-oy())/scale())));}
        public Dimension getPreferredSize(){if(raw==null)return new Dimension(600,400);return new Dimension((int)(raw.getWidth()*scale()),(int)(raw.getHeight()*scale()));}
        void render(){
            if(raw==null)return;
            if(raw.getRaster().getNumBands()>1)display=raw;
            else {
                display=new BufferedImage(raw.getWidth(),raw.getHeight(),BufferedImage.TYPE_BYTE_GRAY);
                double w=((Number)width.getValue()).doubleValue(),c=((Number)level.getValue()).doubleValue();
                int bits=Attribute.getSingleIntegerValueOrDefault(attributes,TagFromName.BitsStored,16);boolean signed=image!=null&&image.isSigned(),inverse=image!=null&&image.isInverted();
                for(int y=0;y<raw.getHeight();y++)for(int x=0;x<raw.getWidth();x++){
                    double v=raw.getRaster().getSampleDouble(x,y,0);if(signed&&bits>0&&bits<=16&&v>=(1<<(bits-1)))v-=(1<<bits);
                    int b=(int)Math.max(0,Math.min(255,((v-c)/w+.5)*255));display.getRaster().setSample(x,y,0,inverse?255-b:b);
                }
            }revalidate();repaint();
        }
        protected void paintComponent(Graphics g){super.paintComponent(g);if(display==null){g.setColor(Color.LIGHT_GRAY);g.drawString("No image pixels · use Full metadata for registration objects",22,40);return;}
            Graphics2D q=(Graphics2D)g.create();q.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            q.translate(ox(),oy());q.scale(scale(),scale());q.drawImage(display,0,0,null);q.setStroke(new BasicStroke((float)(2/scale())));
            for(Rectangle2D area:areas){q.setColor(new Color(50,120,255,120));q.fill(area);q.setColor(Color.CYAN);q.draw(area);}if(pending!=null){q.setColor(Color.CYAN);q.draw(pending);}q.dispose();
        }
    }
}
