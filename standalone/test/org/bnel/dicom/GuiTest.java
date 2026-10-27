package org.bnel.dicom;

import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.WindowEvent;
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
    static void screenshot(Window w,Path target)throws Exception{edt(()->{BufferedImage image=new BufferedImage(w.getWidth(),w.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D g=image.createGraphics();w.paintAll(g);g.dispose();ImageIO.write(image,"png",target.toFile());return null;});}
    static void visible(JComponent c,String name){Rectangle r=c.getVisibleRect();if(r.width<c.getWidth()-2||r.height<c.getHeight()-2)throw new AssertionError("Clipped control: "+name+" visible="+r+" size="+c.getSize());}
    public static void main(String[] args)throws Exception{
        Path screenshots=Path.of(args[0]);Files.createDirectories(screenshots);Path root=Files.createTempDirectory("bnel-ui-synthetic-");Main app=null;
        try{
            System.setProperty("bnel.settingsDir",root.resolve("settings").toString());Theme.install();
            Path source=root.resolve("source");EngineTest.write(EngineTest.image(1,false),source.resolve("CT/1.dcm"));EngineTest.write(EngineTest.image(2,true),source.resolve("MR/2.dcm"));
            app=edt(()->{Main m=new Main();m.setSize(1500,1000);m.setVisible(true);m.importPaths(List.of(source));return m;});final Main a=app;
            waitFor(()->a.active==null&&a.instances.size()==2,"folder import");waitFor(()->a.allOriginal.index.files().size()==2,"complete metadata union");
            EngineTest.check(edt(()->a.patientId.getText().isBlank()&&a.patientName.getText().isBlank()),"new import resets replacement fields");
            edt(()->{a.patientName.setText("Research^001");a.patientId.setText("BNEL-001");a.clean.doClick();return null;});
            waitFor(()->a.active==null&&a.batch!=null,"clean button action");waitFor(()->a.allCleaned.index.files().size()==2,"cleaned union");
            for(Dimension size:List.of(new Dimension(2560,1440),new Dimension(1200,1900),new Dimension(1100,900))){
                edt(()->{a.setSize(size);a.validate();return null;});Thread.sleep(200);
                edt(()->{for(JButton b:new JButton[]{a.importFolder,a.addFiles,a.clean,a.review,a.export,a.cancel,a.identifiers,a.scanLabels})visible(b,b.getText());return null;});
                screenshot(a,screenshots.resolve("main-"+size.width+"x"+size.height+".png"));
            }
            edt(()->{a.metadataTabs.setSelectedIndex(1);a.allOriginal.filter.setText("ProtocolName");return null;});
            EngineTest.check(edt(()->a.allOriginal.table.getRowCount()>0),"union search works");
            screenshot(a,screenshots.resolve("all-files-metadata.png"));edt(()->{a.allOriginal.filter.setText("");a.setSize(1500,1000);return null;});
            SwingUtilities.invokeLater(a.review::doClick);
            waitFor(()->Arrays.stream(Window.getWindows()).anyMatch(w->w instanceof ReviewDialog&&w.isVisible()),"review action opens viewer");
            ReviewDialog review=edt(()->(ReviewDialog)Arrays.stream(Window.getWindows()).filter(w->w instanceof ReviewDialog&&w.isVisible()).findFirst().orElseThrow());
            waitFor(()->!review.busy&&review.image!=null,"pixel decoder");
            EngineTest.check(edt(()->!review.approve.isEnabled()),"approval requires assessment");screenshot(review,screenshots.resolve("image-review.png"));
            edt(()->{review.textClear.doClick();review.faceClear.doClick();review.approve.doClick();return null;});waitFor(()->!review.busy&&review.index==1,"review next instance");
            edt(()->{review.textClear.doClick();review.faceClear.doClick();review.approve.doClick();review.close.doClick();return null;});waitFor(()->a.export.isEnabled(),"review gates export");
            edt(()->{a.patientId.setText("BNEL-002");return null;});EngineTest.check(edt(()->a.batch==null&&!a.export.isEnabled()),"changed policy invalidates prior approvals");
            // Inspect reports before cleaning; navigation continues across individual files and PDF pages.
            Path reports=root.resolve("reports");EngineTest.write(EngineTest.image(1,false),reports.resolve("1.dcm"));EngineTest.write(EngineTest.image(2,true),reports.resolve("2.dcm"));EngineTest.write(EngineTest.reportFixture(),reports.resolve("report.dcm"));
            edt(()->{a.clear.doClick();a.importPaths(List.of(reports));return null;});waitFor(()->a.active==null&&a.instances.size()==3,"report import");
            SwingUtilities.invokeLater(a.review::doClick);waitFor(()->Arrays.stream(Window.getWindows()).anyMatch(w->w instanceof ReviewDialog&&w.isVisible()),"source viewer");
            ReviewDialog sourceReview=edt(()->(ReviewDialog)Arrays.stream(Window.getWindows()).filter(w->w instanceof ReviewDialog&&w.isVisible()).findFirst().orElseThrow());
            waitFor(()->!sourceReview.busy&&sourceReview.image!=null,"source image render");edt(()->{sourceReview.canvas.dispatchEvent(new java.awt.event.MouseWheelEvent(sourceReview.canvas,java.awt.event.MouseEvent.MOUSE_WHEEL,System.currentTimeMillis(),0,100,100,0,false,java.awt.event.MouseWheelEvent.WHEEL_UNIT_SCROLL,1,1));return null;});waitFor(()->!sourceReview.busy&&sourceReview.index==1,"wheel-style navigation crosses single-frame files");
            edt(()->{sourceReview.queueStep(1);return null;});waitFor(()->!sourceReview.busy&&sourceReview.report!=null,"embedded PDF renders");
            screenshot(sourceReview,screenshots.resolve("embedded-report-review.png"));edt(()->{sourceReview.queueStep(1);return null;});waitFor(()->!sourceReview.busy&&sourceReview.frame.getValue()==2,"PDF next page");
            edt(()->{sourceReview.markedCurrent.doClick();sourceReview.removeMarked.doClick();sourceReview.close.doClick();return null;});waitFor(()->a.instances.size()==2,"excluded report removed from session");
            EngineTest.check(Files.exists(reports.resolve("report.dcm")),"exclusion keeps original report intact");EngineTest.check(edt(()->a.batch==null&&!a.export.isEnabled()),"exclusion requires fresh clean and reference checks");
            edt(()->{a.dicomdir.setSelected(true);a.persist(true);a.clear.doClick();a.closeWindow();return null;});
            System.out.println("PASS: headful import, union, clean/review actions, privacy gates, resize, portrait layout, report pages, mouse wheel, file exclusion and screenshots");
        }catch(Throwable failure){if(app!=null)try{screenshot(app,screenshots.resolve("failure.png"));}catch(Exception ignored){}failure.printStackTrace();System.exit(1);
        }finally{for(Window w:Window.getWindows())w.dispose();DicomIO.deleteTree(root);}
        System.exit(0);
    }
}
