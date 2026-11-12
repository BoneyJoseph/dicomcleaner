package org.bnel.dicom;

import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.awt.event.MouseEvent;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;

/** Headful action/layout checks; screenshots contain generated synthetic data only. */
public final class GuiTest {
    static <T>T edt(Callable<T> action)throws Exception{AtomicReference<T> result=new AtomicReference<>();AtomicReference<Throwable> error=new AtomicReference<>();SwingUtilities.invokeAndWait(()->{try{result.set(action.call());}catch(Throwable e){error.set(e);}});if(error.get()!=null)throw new AssertionError(error.get());return result.get();}
    static void waitFor(Callable<Boolean> condition,String message)throws Exception{long end=System.nanoTime()+60_000_000_000L;while(System.nanoTime()<end){if(edt(condition))return;Thread.sleep(80);}throw new AssertionError("Timed out: "+message);}
    // Capture actual app content; native OS caption bars are not painted by Swing.
    static Component find(Container root,Class<?> type){for(Component c:root.getComponents()){if(type.isInstance(c))return c;if(c instanceof Container k){Component f=find(k,type);if(f!=null)return f;}}return null;}
    static void screenshot(Window w,Path target)throws Exception{edt(()->{Container pane=((RootPaneContainer)w).getContentPane();BufferedImage image=new BufferedImage(pane.getWidth(),pane.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();pane.paintAll(g);g.dispose();ImageIO.write(image,"png",target.toFile());return null;});}
    static void visible(JComponent c,String name){Rectangle r=c.getVisibleRect();if(r.width<c.getWidth()-2||r.height<c.getHeight()-2)throw new AssertionError("Clipped control: "+name+" visible="+r+" size="+c.getSize());}
    static boolean buttonExists(Container root,String text){for(Component c:root.getComponents()){if(c instanceof AbstractButton b&&b.getText().equals(text))return true;if(c instanceof Container container&&buttonExists(container,text))return true;}return false;}
    public static void main(String[] args)throws Exception{
        Path screenshots=Path.of(args[0]);Files.createDirectories(screenshots);Path root=Files.createTempDirectory("bnel-ui-synthetic-");Main app=null;
        try{
            System.setProperty("bnel.settingsDir",root.resolve("settings").toString());Theme.install();
            Path source=root.resolve("source");EngineTest.write(EngineTest.image(1,false),source.resolve("CT/1.dcm"));EngineTest.write(EngineTest.image(2,true),source.resolve("MR/2.dcm"));
            app=edt(()->{Main m=new Main();m.setSize(1500,1000);m.setVisible(true);m.importPaths(List.of(source));return m;});final Main a=app;
            waitFor(()->a.active==null&&a.instances.size()==2,"folder import");waitFor(()->a.allOriginal.index.files().size()==2,"complete metadata union");
            EngineTest.check(edt(()->a.patientId.getText().isBlank()&&a.patientName.getText().isBlank()),"new import resets replacement fields");
            EngineTest.check(edt(()->a.minimal.isSelected()),"essential research metadata is enabled for a fresh settings file");
            EngineTest.check(edt(()->a.metadataTabs.getTabCount()==2&&a.metadataTabs.getSelectedIndex()==0&&a.metadataTabs.getTitleAt(0).equals("All metadata")&&a.metadataTabs.getTitleAt(1).equals("Instance comparison")),"merged metadata is first and default; instance comparison second");
            edt(()->{a.patientName.setText("Research^001");return null;});EngineTest.check(edt(()->a.patientId.getText().equals("Research^001")),"Name edits mirror to ID");
            edt(()->{a.patientId.setText("BNEL-001");return null;});EngineTest.check(edt(()->a.patientName.getText().equals("BNEL-001")),"ID edits mirror to Name");
            edt(()->{a.linkedIdentity.doClick();a.patientName.setText("Separate^Name");return null;});EngineTest.check(edt(()->a.patientId.getText().equals("BNEL-001")),"unlink permits a different name and ID");
            edt(()->{a.linkedIdentity.doClick();a.patientName.setText("Research^001");a.persist(false);return null;});
            EngineTest.check(!Files.readString(a.settings.path).contains("Research^001")&&!Files.readString(a.settings.path).contains("BNEL-001"),"mirrored identity is not persisted");
            edt(()->{a.clean.doClick();return null;});
            waitFor(()->a.active==null&&a.batch!=null,"clean button action");waitFor(()->a.allCleaned.index.files().size()==2,"cleaned union");
            // The synthetic Series Description contains the patient's name: the identifier check must report it (warning only).
            waitFor(()->Arrays.stream(Window.getWindows()).anyMatch(w->w instanceof JDialog d&&d.isVisible()&&"Cleaning results".equals(d.getTitle())),"identifier warnings shown after cleaning");
            edt(()->{for(Window w:Window.getWindows())if(w instanceof JDialog d&&d.isVisible()&&"Cleaning results".equals(d.getTitle())){
                JTextArea text=(JTextArea)((JScrollPane)GuiTest.find(d.getContentPane(),JScrollPane.class)).getViewport().getView();
                EngineTest.check(text.getText().contains("Possible identifiers in retained metadata")&&text.getText().contains("SeriesDescription"),"results list the Series Description warning");
                EngineTest.check(!a.cancel.isVisible()&&a.export.isEnabled(),"main window settled and export still available behind the warning");d.dispose();}return null;});
            for(Dimension size:List.of(new Dimension(3840,2160),new Dimension(2560,1440),new Dimension(1920,1080),new Dimension(1600,1000),new Dimension(1280,800))){
                edt(()->{a.setSize(size);a.validate();return null;});Thread.sleep(200);
                edt(()->{for(JButton b:new JButton[]{a.importFolder,a.addFiles,a.clean,a.review,a.export})visible(b,b.getText());if(a.settingsStrip.compact){EngineTest.check(SwingUtilities.isDescendingFrom(a.identifiers,a.settingsStrip.metadataPopup)&&SwingUtilities.isDescendingFrom(a.scanLabels,a.settingsStrip.metadataPopup),"compact metadata options remain available in bottom popover");visible(a.settingsStrip.metadataButton,"Metadata options");}else{visible(a.identifiers,"Identifier rules");visible(a.scanLabels,"Scan labels");}visible(a.patientName,"patient name");visible(a.patientId,"patient ID");
                    EngineTest.check(a.allMetadata.split.getOrientation()==JSplitPane.HORIZONTAL_SPLIT,"all metadata remains side by side in landscape");
                    EngineTest.check(Math.abs(a.allMetadata.split.getLeftComponent().getWidth()-a.allMetadata.split.getRightComponent().getWidth())<4,"metadata panes stay symmetric after resize");return null;});
                screenshot(a,screenshots.resolve("main-"+size.width+"x"+size.height+".png"));
            }
            edt(()->{a.metadataTabs.setSelectedIndex(0);a.allOriginal.filter.setText("ProtocolName");return null;});
            EngineTest.check(edt(()->a.allOriginal.table.getRowCount()>0&&a.allCleaned.table.getRowCount()>0),"shared search filters both metadata panes");
            EngineTest.check(edt(()->a.allCleaned.table.getRowCount()<a.allCleaned.index.tags().size()),"cleaned search listener follows shared document");
            edt(()->{a.allOriginal.table.setRowSelectionInterval(0,0);return null;});EngineTest.check(edt(()->a.allOriginal.selectedPath().equals(a.allCleaned.selectedPath())),"tag selection links corresponding path");
            edt(()->{a.allOriginal.filter.setText("(0x0019,0x1010)");a.allOriginal.table.setRowSelectionInterval(0,0);return null;});
            EngineTest.check(edt(()->a.allCleaned.table.getRowCount()==0&&a.allCleaned.details.getRowCount()==2&&a.allCleaned.details.getValueAt(0,2).toString().contains("absent")),"removed tag has explicit absent values in cleaned pane");
            edt(()->{a.allOriginal.filter.setText("");a.allOriginal.selectPath(a.allOriginal.index.tags().stream().filter(t->t.name().equals("PatientName")).findFirst().orElseThrow().path());a.setSize(1500,1000);a.validate();return null;});Thread.sleep(200);
            edt(()->{a.allOriginal.split.setDividerLocation(330);return null;});EngineTest.check(edt(()->a.allCleaned.split.getDividerLocation()==a.allOriginal.split.getDividerLocation()),"per-file detail splits remain aligned");
            edt(()->{EngineTest.check(!a.cancel.isVisible(),"cancel is hidden while idle");
                EngineTest.check(SwingUtilities.convertPoint(a.review,0,0,a).x<SwingUtilities.convertPoint(a.clean,0,0,a).x,"review precedes clean in the command bar");
                EngineTest.check(SwingUtilities.convertPoint(a.clean,0,0,a).y>SwingUtilities.convertPoint(a.outerSplit,0,a.outerSplit.getHeight()-1,a).y,"main commands sit below metadata workspace");return null;});
            screenshot(a,screenshots.resolve("all-files-metadata.png"));
            edt(()->{a.metadataTabs.setSelectedIndex(1);return null;});screenshot(a,screenshots.resolve("instance-comparison.png"));edt(()->{a.metadataTabs.setSelectedIndex(0);return null;});
            SwingUtilities.invokeLater(a.review::doClick);
            waitFor(()->Arrays.stream(Window.getWindows()).anyMatch(w->w instanceof ReviewDialog&&w.isVisible()),"review action opens viewer");
            ReviewDialog review=edt(()->(ReviewDialog)Arrays.stream(Window.getWindows()).filter(w->w instanceof ReviewDialog&&w.isVisible()).findFirst().orElseThrow());
            waitFor(()->!review.busy&&review.shown!=null&&review.shown.raw!=null,"pixel decoder");
            EngineTest.check(edt(()->((JViewport)review.files.getParent()).getViewPosition().x==0),"viewer tree keeps left alignment with long series labels");
            EngineTest.check(edt(()->a.batch.exportReady()),"export is ready without opening or approving images");
            EngineTest.check(edt(()->review.files.pathFor(0).getPathCount()==6&&review.files.pathFor(0).getParentPath().getLastPathComponent().toString().contains(a.instances.get(0).description())),"viewer uses source/subject/study/series/file hierarchy with series labels");
            edt(()->{for(JButton b:new JButton[]{a.importFolder,a.clean,a.export,a.review,a.scanLabels,a.identifiers,review.selectUnmarked,review.markSelected,review.unmarkSelected,review.reset,review.previous,review.next})EngineTest.check(b.getToolTipText()!=null&&!b.getToolTipText().isBlank(),"hover help: "+b.getText());return null;});
            edt(()->{review.markedCurrent.doClick();return null;});EngineTest.check(edt(()->review.files.fileLabel(0).contains("[MARKED]")),"marked file has visible text indicator");
            edt(()->{review.selectUnmarked.doClick();return null;});EngineTest.check(edt(()->review.files.getSelectedIndices().length==1&&review.files.getSelectedIndex()==1),"select all unmarked excludes marked file");
            edt(()->{review.files.selectFileInterval(0,1);review.unmarkSelected.doClick();return null;});EngineTest.check(edt(()->review.marked.isEmpty()&&review.files.getSelectedIndices().length==2),"unmark mixed selection is idempotent and preserves selection");
            edt(()->{review.unmarkSelected.doClick();review.markSelected.doClick();return null;});EngineTest.check(edt(()->review.marked.size()==2&&review.files.fileLabel(1).contains("[MARKED]")),"mark all selected updates visible flags");
            edt(()->{review.markSelected.doClick();review.unmarkSelected.doClick();return null;});
            EngineTest.check(edt(()->review.marked.isEmpty()&&a.batch.exportReady()),"bulk marking stays idempotent and export never requires approval");
            EngineTest.check(edt(()->!a.isEnabled()&&review.isResizable()&&!review.isUndecorated()),"review has native frame caption controls and isolates the main session");
            if(Toolkit.getDefaultToolkit().isFrameStateSupported(Frame.MAXIMIZED_BOTH)&&edt(()->review.getInsets().top>10)){
                Rectangle normal=edt(review::getBounds);
                Robot robot=new Robot();robot.setAutoDelay(80);
                Point caption=edt(()->new Point(review.getX()+review.getWidth()/2,review.getY()+review.getInsets().top/2));
                robot.mouseMove(caption.x,caption.y);robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
                waitFor(()->(review.getExtendedState()&Frame.MAXIMIZED_BOTH)==Frame.MAXIMIZED_BOTH,"native title double click maximizes viewer");
                Thread.sleep(400);caption=edt(()->new Point(review.getX()+review.getWidth()/2,review.getY()+review.getInsets().top/2));
                robot.mouseMove(caption.x,caption.y);robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
                waitFor(()->review.getExtendedState()==Frame.NORMAL,"native title double click restores viewer");
                waitFor(()->review.getSize().equals(normal.getSize()),"restored viewer retains previous size");
                EngineTest.check(true,"native title-bar double click maximizes and restores size");
            }
            EngineTest.check(edt(()->!buttonExists(review,"Approve selected")&&!buttonExists(review,"Remove all approvals")&&!buttonExists(review,"Remove approval of selected")),"obsolete approval controls are absent");
            edt(()->{java.awt.Component cell=review.files.getCellRenderer().getTreeCellRendererComponent(review.files,review.files.leaves[0].getParent(),false,true,false,0,false);EngineTest.check(!cell.getFont().isBold()&&cell.getFont().getSize()>=13,"source hierarchy uses readable regular-weight text");return null;});
            edt(()->{review.selectUnmarked.doClick();return null;});
            screenshot(review,screenshots.resolve("image-review.png"));
            edt(()->{review.dispatchEvent(new WindowEvent(review,WindowEvent.WINDOW_CLOSING));return null;});waitFor(()->a.export.isEnabled()&&a.isEnabled(),"native viewer close restores main window and keeps export available");
            edt(()->{a.patientId.setText("BNEL-002");return null;});EngineTest.check(edt(()->a.batch==null&&!a.export.isEnabled()),"changed policy invalidates prior approvals");
            // Inspect reports before cleaning; navigation continues across individual files and PDF pages.
            Path reports=root.resolve("reports");EngineTest.write(EngineTest.image(1,false),reports.resolve("1.dcm"));EngineTest.write(EngineTest.image(2,true),reports.resolve("2.dcm"));EngineTest.write(EngineTest.reportFixture(),reports.resolve("report.dcm"));
            edt(()->{a.clear.doClick();a.importPaths(List.of(reports));return null;});waitFor(()->a.active==null&&a.instances.size()==3,"report import");
            EngineTest.check(edt(()->a.patientName.getText().isBlank()&&a.patientId.getText().isBlank()&&a.linkedIdentity.isSelected()),"next import resets identity and link state");
            SwingUtilities.invokeLater(a.review::doClick);waitFor(()->Arrays.stream(Window.getWindows()).anyMatch(w->w instanceof ReviewDialog&&w.isVisible()),"source viewer");
            ReviewDialog sourceReview=edt(()->(ReviewDialog)Arrays.stream(Window.getWindows()).filter(w->w instanceof ReviewDialog&&w.isVisible()).findFirst().orElseThrow());
            waitFor(()->!sourceReview.busy&&sourceReview.shown!=null&&sourceReview.shown.raw!=null,"source image render");edt(()->{sourceReview.canvas.dispatchEvent(new java.awt.event.MouseWheelEvent(sourceReview.canvas,java.awt.event.MouseEvent.MOUSE_WHEEL,System.currentTimeMillis(),0,100,100,0,false,java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL,1,1));return null;});waitFor(()->!sourceReview.busy&&sourceReview.index==1,"wheel-style navigation crosses single-frame files");
            edt(()->{sourceReview.queueStep(1);return null;});waitFor(()->!sourceReview.busy&&sourceReview.shown!=null&&sourceReview.frameCount==2,"embedded PDF renders");
            screenshot(sourceReview,screenshots.resolve("embedded-report-review.png"));edt(()->{sourceReview.queueStep(1);return null;});waitFor(()->!sourceReview.busy&&sourceReview.shownFrame==1,"PDF next page");
            edt(()->{sourceReview.markedCurrent.doClick();sourceReview.removeMarked.doClick();sourceReview.close.doClick();return null;});waitFor(()->a.instances.size()==2,"excluded report removed from session");
            EngineTest.check(Files.exists(reports.resolve("report.dcm")),"exclusion keeps original report intact");EngineTest.check(edt(()->a.batch==null&&!a.export.isEnabled()),"exclusion requires fresh clean and reference checks");
            // A realistic single-frame stack exercises the slider, prefetch and cine path.
            Path stack=root.resolve("stack");
            for(int n=0;n<80;n++){
                var l=EngineTest.image(100+n,false);EngineTest.number(l,0x00280010,256);EngineTest.number(l,0x00280011,256);
                EngineTest.value(l,0x0008103e,"Synthetic CT stack");EngineTest.value(l,0x00281050,"40");EngineTest.value(l,0x00281051,"400");
                short[] px=new short[256*256];double factor=.55+.40*Math.sin(Math.PI*n/90);
                for(int y=0;y<256;y++)for(int x=0;x<256;x++){double r=Math.pow((x-128)/(100*factor),2)+Math.pow((y-128)/(115*factor),2);px[y*256+x]=(short)(r<.76?1050+(int)(18*Math.sin(x*.2)*Math.cos(y*.17)):r<.88?1700:r<1?1080:0);}
                var pixels=new com.pixelmed.dicom.OtherWordAttribute(com.pixelmed.dicom.TagFromName.PixelData);pixels.setValues(px);l.put(pixels);EngineTest.write(l,stack.resolve(String.format("%04d.dcm",n)));
            }
            edt(()->{a.clear.doClick();a.importPaths(List.of(stack));return null;});waitFor(()->a.active==null&&a.instances.size()==80,"80-slice stack import");
            SwingUtilities.invokeLater(a.review::doClick);waitFor(()->Arrays.stream(Window.getWindows()).anyMatch(w->w instanceof ReviewDialog&&w.isVisible()),"stack viewer");
            ReviewDialog cine=edt(()->(ReviewDialog)Arrays.stream(Window.getWindows()).filter(w->w instanceof ReviewDialog&&w.isVisible()).findFirst().orElseThrow());
            waitFor(()->!cine.busy&&cine.shown!=null,"initial stack frame");EngineTest.check(edt(()->cine.frame.getMaximum()==80),"slider spans all single-frame instances");
            edt(()->{BufferedImage before=cine.canvas.display;cine.frame.setValue(80);EngineTest.check(cine.canvas.display==before,"previous frame remains while new slice decodes");return null;});waitFor(()->!cine.busy&&cine.index==79,"slider jumps to final file");
            edt(()->{for(int n=0;n<100;n++)cine.frame.setValue(1+n%79);cine.frame.setValue(41);return null;});waitFor(()->!cine.busy&&cine.index==40,"rapid scrubbing renders latest requested slice");
            screenshot(cine,screenshots.resolve("stack-viewer-landscape.png"));
            for(Dimension size:List.of(new Dimension(2560,1440),new Dimension(3840,2160),new Dimension(1600,1000),new Dimension(1280,800))){
                edt(()->{cine.setSize(size);cine.validate();return null;});Thread.sleep(200);
                edt(()->{visible(cine.reset,"viewer auto fit");visible(cine.frame,"stack scrubber");visible(cine.play,"play button");for(JButton b:new JButton[]{cine.selectUnmarked,cine.markSelected,cine.unmarkSelected,cine.removeMarked,cine.more,cine.close})visible(b,b.getText());visible(cine.presets,"window presets");return null;});
                screenshot(cine,screenshots.resolve("viewer-"+size.width+"x"+size.height+".png"));
            }
            // Radiology conventions: right-drag window/level, CT presets, overlay-friendly icon transport, Recycle Bin in the More menu.
            edt(()->{cine.setSize(1500,1000);cine.validate();return null;});Thread.sleep(200);
            double[] wl=edt(()->new double[]{((Number)cine.width.getValue()).doubleValue(),((Number)cine.level.getValue()).doubleValue()});
            edt(()->{Point c=new Point(cine.canvas.getVisibleRect().x+200,cine.canvas.getVisibleRect().y+200);long t=System.currentTimeMillis();
                cine.canvas.dispatchEvent(new MouseEvent(cine.canvas,MouseEvent.MOUSE_PRESSED,t,InputEvent.BUTTON3_DOWN_MASK,c.x,c.y,1,false,MouseEvent.BUTTON3));
                cine.canvas.dispatchEvent(new MouseEvent(cine.canvas,MouseEvent.MOUSE_DRAGGED,t+10,InputEvent.BUTTON3_DOWN_MASK,c.x+60,c.y+30,1,false,MouseEvent.BUTTON3));
                cine.canvas.dispatchEvent(new MouseEvent(cine.canvas,MouseEvent.MOUSE_RELEASED,t+20,0,c.x+60,c.y+30,1,false,MouseEvent.BUTTON3));return null;});
            EngineTest.check(edt(()->((Number)cine.width.getValue()).doubleValue()>wl[0]&&((Number)cine.level.getValue()).doubleValue()>wl[1]&&cine.canvas.areas.isEmpty()),"right-drag adjusts window/level without drawing");
            edt(()->{cine.presets.setSelectedIndex(2);return null;});
            EngineTest.check(edt(()->((Number)cine.width.getValue()).doubleValue()==80&&((Number)cine.level.getValue()).doubleValue()==40&&cine.presets.getSelectedIndex()==0),"CT brain preset applies 80/40");
            EngineTest.check(edt(()->cine.play.getText().isEmpty()&&cine.play.getToolTipText()!=null&&!cine.fpsBox.isVisible()),"icon-only transport; frame rate hidden while paused");
            edt(()->{cine.more.doClick();return null;});
            EngineTest.check(edt(()->Arrays.stream(MenuSelectionManager.defaultManager().getSelectedPath()).anyMatch(e->e instanceof JPopupMenu m&&Arrays.stream(m.getComponents()).anyMatch(c->c instanceof JMenuItem i&&i.getText().startsWith("Move originals to Recycle Bin")))),"Recycle Bin action lives in the More menu");
            edt(()->{MenuSelectionManager.defaultManager().clearSelectedPath();return null;});
            edt(()->{cine.frame.setValue(1);return null;});waitFor(()->!cine.busy&&cine.index==0,"cine reset");long start=System.nanoTime();
            edt(()->{cine.fps.setValue(20);cine.play.doClick();return null;});EngineTest.check(edt(()->cine.fpsBox.isVisible()),"frame rate shown while playing");waitFor(()->cine.index>=15,"cine advances automatically");
            edt(()->{cine.play.doClick();return null;});int stopped=edt(()->cine.index);Thread.sleep(200);EngineTest.check(edt(()->!cine.playing&&cine.index<=stopped+1),"pause stops playback");
            EngineTest.check(cine.frames.cachedBytes()<=192L*1024*1024,"frame cache bounded");System.out.println("Cine: at least 15 slices in "+((System.nanoTime()-start)/1_000_000)+" ms, including test polling/pause");
            edt(()->{cine.close.doClick();return null;});waitFor(()->!cine.isVisible(),"viewer closes during background prefetch");
            ReviewDialog large=edt(()->new ReviewDialog(a,Collections.nCopies(8000,a.instances.get(0)),null));
            EngineTest.check(edt(()->large.frame.getMaximum()==8000&&large.files.inputs.size()==8000),"8000-instance list and slider range");
            edt(()->{large.frame.setValue(8000);return null;});waitFor(()->!large.busy&&large.index==7999,"8000-instance final position");edt(()->{large.close.doClick();return null;});
            edt(()->{
                Cleaner.Instance item=a.instances.get(0);
                Path other=root.resolve("second-import");
                Cleaner.Instance moved=new Cleaner.Instance(other.resolve("slice.dcm"),other,Path.of("slice.dcm"),item.sha256(),item.sop(),item.sopClass(),item.study(),item.series(),item.patientKey(),item.patientName(),item.patientId(),item.modality(),item.seriesNumber(),item.instanceNumber(),item.frames(),item.description(),item.problem(),item.studyDate(),item.burnedInHint());
                SourceTree t=new SourceTree();t.setInstances(List.of(item,moved,item));
                EngineTest.check(((SourceTree.Node)t.getModel().getRoot()).getChildCount()==2,"same series imported from separate folders stays under separate source roots");
                EngineTest.check(Arrays.equals(t.order,new int[]{0,2,1}),"tree order is grouped by actual import folder");
                var group=t.pathFor(0).getParentPath();t.setSelectionPaths(new javax.swing.tree.TreePath[]{group,t.pathFor(0)});
                EngineTest.check(Arrays.equals(t.getSelectedIndices(),new int[]{0,2}),"overlapping parent and child selection selects each file once");
                t.refreshStates(i->i==0?SourceTree.MARKED:0);
                EngineTest.check(t.fileLabel(0).contains("[MARKED]")&&group.getLastPathComponent().toString().contains("1 marked"),"collapsed series carries a visible marked count");
                EngineTest.check(Arrays.equals(t.getSelectedIndices(),new int[]{0,2}),"state refresh preserves branch selection");
                t.collapseToSeries();t.expandSeries();EngineTest.check(t.fileLabel(0).contains("[MARKED]"),"collapse and expansion preserve marks");
                ReviewDialog ordered=new ReviewDialog(a,List.of(item,moved,item),null);
                EngineTest.check(ordered.keyAt(1).file()==2&&ordered.keyAt(2).file()==1,"playback timeline follows source-tree order across interleaved imports");
                ordered.close.doClick();
                return null;
            });
            edt(()->{a.addTagRule(0x00180080);return null;});EngineTest.check(edt(()->IdentifierPolicy.load(a.settings).removes(0x00180080)),"context tag rule persists");
            edt(()->{a.dicomdir.setSelected(true);a.minimal.setSelected(false);a.persist(true);a.clear.doClick();a.closeWindow();return null;});
            edt(()->{Main reopened=new Main();EngineTest.check(!reopened.minimal.isSelected(),"explicitly saved research metadata preference is preserved on relaunch");reopened.dispose();return null;});
            System.out.println("GUI assertions: "+EngineTest.assertions);
            System.out.println("PASS: headful import, union, clean/review actions, optional review, resize, landscape layout, native viewer frame, report pages, mouse wheel, file exclusion, stack scrubbing, cine, custom rules and screenshots");
        }catch(Throwable failure){if(app!=null)try{screenshot(app,screenshots.resolve("failure.png"));}catch(Exception ignored){}failure.printStackTrace();System.exit(1);
        }finally{for(Window w:Window.getWindows())w.dispose();DicomIO.deleteTree(root);}
        System.exit(0);
    }
}
