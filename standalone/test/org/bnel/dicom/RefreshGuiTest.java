package org.bnel.dicom;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/** Interaction and geometry acceptance for the supplied UI handoff. Synthetic data only. */
public final class RefreshGuiTest {
    static <T>T edt(java.util.concurrent.Callable<T> action)throws Exception{return GuiTest.edt(action);}
    static void waitFor(java.util.concurrent.Callable<Boolean> action,String message)throws Exception{GuiTest.waitFor(action,message);}
    static void check(boolean ok,String message){EngineTest.check(ok,message);}
    static void click(ReviewDialog review,int file,boolean trailing)throws Exception{
        edt(()->{review.files.ensureIndexIsVisible(file);return null;});Thread.sleep(60);
        Point at=edt(()->{Rectangle r=review.files.getPathBounds(review.files.pathFor(file));Point p=new Point(trailing?review.files.getVisibleRect().width-12:r.x+35,r.y+r.height/2);SwingUtilities.convertPointToScreen(p,review.files);return p;});
        System.out.println("Click leaf "+file+" at "+at);
        Robot robot=new Robot();robot.setAutoDelay(40);robot.mouseMove(at.x,at.y);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        Thread.sleep(150);System.out.println(edt(()->"after click index="+review.index+" lead="+review.files.getLeadSelectionIndex()+" target="+review.targetPosition+" viewport="+review.files.getVisibleRect()+" row="+review.files.getPathBounds(review.files.pathFor(file))));
        waitFor(()->!review.busy&&review.index==file,"one-click file "+file);
        check(edt(()->review.files.getLeadSelectionIndex()==file&&review.frame.getValue()==review.starts[file]+1&&review.fileText.getToolTipText().equals(review.inputs.get(file).source().toString())),"leaf selection, slider and caption agree for file "+file);
    }
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("bnel-refresh-ui-");Path shots=Path.of(args[0]);Files.createDirectories(shots);
        Main main=null;
        try{
            System.setProperty("bnel.settingsDir",root.resolve("settings").toString());Theme.install();
            Path input=root.resolve("source");for(int i=0;i<90;i++){
                var l=EngineTest.image(i+1,false);EngineTest.value(l,0x0008103e,"Series "+i/30+" · synthetic navigation test");EngineTest.value(l,0x0020000e,"2.25.200"+i/30);
                EngineTest.write(l,input.resolve(String.format("series-%d/scan-%04d.dcm",i/30,i)));
            }
            final Main a=main=edt(()->{Main m=new Main();m.setSize(Theme.u(1600),Theme.u(1000));m.setVisible(true);m.importPaths(List.of(input));return m;});
            waitFor(()->a.active==null&&a.instances.size()==90,"fixture import");waitFor(()->a.allOriginal.index.files().size()==90,"union ready");
            check(edt(()->a.tree.getFont().getSize()==Theme.u(13)&&a.allOriginal.table.getFont().getSize()==Theme.u(13)&&a.clean.getFont().getSize()==Theme.u(13)),"body, table and control fonts scale consistently");
            check(edt(()->!a.settingsStrip.compact),"reference width shows all settings without popovers");
            edt(()->{int[] selection=a.tree.getSelectedIndices();a.tree.setFilter("scan-0044");check(Arrays.equals(selection,a.tree.getSelectedIndices()),"search preserves hidden operation selection");check(a.selection.size()==90&&a.instances.size()==90,"search never removes imported files or changes clean scope");a.tree.setFilter("");check(Arrays.equals(selection,a.tree.getSelectedIndices()),"clear search restores complete selection");return null;});
            for(int width:new int[]{1600,1280,1024,1600}){
                edt(()->{a.setSize(Theme.u(width),Theme.u(width==1024?720:1000));a.validate();return null;});Thread.sleep(250);
                edt(()->{
                    for(JComponent c:new JComponent[]{a.importFolder,a.addFiles,a.remove,a.review,a.clean,a.export,a.clear,a.patientName,a.patientId,a.date,a.dateMode})GuiTest.visible(c,c.getName());
                    check(a.patientName.getWidth()==a.patientId.getWidth(),"replacement fields have identical widths at "+width);
                    check(Math.abs(a.allMetadata.split.getLeftComponent().getWidth()-a.allMetadata.split.getRightComponent().getWidth())<=1,"equal default comparison panes at "+width+" left="+a.allMetadata.split.getLeftComponent().getWidth()+" right="+a.allMetadata.split.getRightComponent().getWidth()+" ratio="+((RatioSplit)a.allMetadata.split).ratio);
                    check(SwingUtilities.convertPoint(a.allOriginal.table.getTableHeader(),0,0,a).y==SwingUtilities.convertPoint(a.allCleaned.table.getTableHeader(),0,0,a).y,"matching table baselines at "+width);
                    check(SwingUtilities.convertPoint(a.allOriginal.details.getTableHeader(),0,0,a).y==SwingUtilities.convertPoint(a.allCleaned.details.getTableHeader(),0,0,a).y,"matching detail baselines at "+width);
                    check(a.importFolder.getWidth()==a.addFiles.getWidth()&&a.addFiles.getWidth()==a.remove.getWidth(),"equal source command widths");
                    check(a.clean.getWidth()==a.export.getWidth()&&a.clean.getHeight()==Theme.u(36),"equal clean/export command dimensions");
                    check(a.statusFooter.credit.getText().equals(Main.CREDIT),"credit exact");GuiTest.visible(a.statusFooter.credit,"full footer credit");
                    check(a.statusFooter.credit.getWidth()>=a.statusFooter.credit.getPreferredSize().width,"credit never ellipsized");
                    if(a.settingsStrip.compact){a.settingsStrip.metadataButton.doClick();GuiTest.visible(a.identifiers,"popover identifier rules");check(a.identifiers.isShowing(),"compact settings are usable controls");a.settingsStrip.metadataPopup.setVisible(false);}
                    return null;
                });
                GuiTest.screenshot(a,shots.resolve("refresh-main-"+width+"-scale"+System.getProperty("flatlaf.uiScale","1")+".png"));
            }
            edt(()->{a.outerSplit.setDividerLocation(.31);((RatioSplit)a.allMetadata.split).setDividerLocation(.59);a.allOriginal.split.setDividerLocation(.68);a.persist(false);return null;});
            double sourceRatio=edt(()->a.outerSplit.ratio),pairRatio=edt(()->((RatioSplit)a.allMetadata.split).ratio),detailRatio=edt(()->((RatioSplit)a.allOriginal.split).ratio);
            edt(()->{a.setSize(Theme.u(1024),Theme.u(720));a.validate();return null;});Thread.sleep(100);
            edt(()->{a.setSize(Theme.u(1600),Theme.u(1000));a.metadataTabs.setSelectedIndex(1);a.metadataTabs.setSelectedIndex(0);a.validate();return null;});Thread.sleep(100);
            check(edt(()->Math.abs(a.outerSplit.ratio-sourceRatio)<.0001&&Math.abs(((RatioSplit)a.allMetadata.split).ratio-pairRatio)<.0001&&Math.abs(((RatioSplit)a.allOriginal.split).ratio-detailRatio)<.0001),"user ratios survive compact clamp and tab switches");
            edt(()->{Main reopened=new Main();check(Math.abs(reopened.outerSplit.ratio-sourceRatio)<.0001&&Math.abs(((RatioSplit)reopened.allMetadata.split).ratio-pairRatio)<.0001,"layout ratios restored from preferences");reopened.dispose();return null;});
            final ReviewDialog v=edt(()->{ReviewDialog d=new ReviewDialog(a,List.copyOf(a.instances),null);d.setSize(Theme.u(1600),Theme.u(1000));d.setVisible(true);return d;});
            waitFor(()->!v.busy&&v.shown!=null,"viewer initial frame");
            for(int file:new int[]{0,44,89,30})click(v,file,true);
            edt(()->{v.files.setFilter("scan-0044");return null;});click(v,44,true);edt(()->{v.files.setFilter("");return null;});
            edt(()->{v.files.setSelectedIndex(0);v.setPlaying(true);return null;});click(v,5,true);check(edt(()->!v.playing),"manual tree click pauses playback");
            edt(()->{v.files.setSelectedIndex(2);v.files.setSelectedIndex(63);v.files.setSelectedIndex(39);check(v.busy?!v.draw.isEnabled()&&!v.width.isEnabled()&&!v.markedCurrent.isEnabled():true,"old pixels cannot be edited under loading selection");return null;});
            waitFor(()->!v.busy&&v.index==39,"latest A B C selection wins");
            edt(()->{v.files.setSelectedIndices(new int[]{10,40,70});v.markSelection(true);check(v.marked.size()==3&&v.files.getSelectedIndices().length==3,"multi-series marking preserves selection");v.markSelection(false);check(v.marked.isEmpty(),"unmark selected is idempotent");v.files.setSelectedIndex(5);v.setPlaying(true);return null;});
            waitFor(()->!v.busy&&v.index>=6,"play before manual browse");
            int browse=edt(()->{v.files.expandSeries();v.sourceBrowser.scroll.dispatchEvent(new MouseWheelEvent(v.sourceBrowser.scroll,MouseEvent.MOUSE_WHEEL,System.currentTimeMillis(),0,100,100,0,false,MouseWheelEvent.WHEEL_UNIT_SCROLL,3,2));v.sourceBrowser.scroll.getVerticalScrollBar().setValue(900);return v.sourceBrowser.scroll.getVerticalScrollBar().getValue();});
            Thread.sleep(400);
            check(edt(()->!v.followPlayback&&Math.abs(v.sourceBrowser.scroll.getVerticalScrollBar().getValue()-browse)<=1),"manual tree scrolling remains stable during autoplay");
            edt(()->{v.queueStep(1);return null;});waitFor(()->!v.busy,"transport after manual browse");check(edt(()->v.followPlayback),"explicit transport resumes follow mode");
            for(int width:new int[]{1600,1280,960}){
                edt(()->{v.setSize(Theme.u(width),Theme.u(width==960?700:1000));v.validate();return null;});Thread.sleep(200);
                edt(()->{for(JComponent c:new JComponent[]{v.previous,v.play,v.next,v.frame,v.zoom,v.width,v.level,v.reset,v.markedCurrent,v.selectUnmarked,v.markSelected,v.unmarkSelected,v.removeMarked,v.restore,v.more,v.presets,v.close})GuiTest.visible(c,c.getName());check(v.frame.getWidth()>=Theme.u(80),"usable slider width at "+width);return null;});
                GuiTest.screenshot(v,shots.resolve("refresh-viewer-"+width+"-scale"+System.getProperty("flatlaf.uiScale","1")+".png"));
            }
            edt(()->{v.dispose();return null;});
            long start=System.nanoTime();final ReviewDialog large=edt(()->new ReviewDialog(a,Collections.nCopies(3597,a.instances.get(0)),null));
            long setup=(System.nanoTime()-start)/1_000_000;
            edt(()->{large.files.setSelectedIndex(10);large.files.setSelectedIndex(1800);large.files.setSelectedIndex(3596);return null;});waitFor(()->!large.busy&&large.index==3596,"3597-file rapid tree navigation");check(large.frames.cachedBytes()<=192L*1024*1024,"bounded cache on large tree");edt(()->{large.dispose();return null;});
            System.out.println("3597-node fixture setup: "+setup+" ms; pixel decoding asynchronous.");
            System.out.println("PASS: "+EngineTest.assertions+" refresh assertions; scale="+System.getProperty("flatlaf.uiScale","1"));
        }catch(Throwable t){t.printStackTrace();if(main!=null)GuiTest.screenshot(main,shots.resolve("refresh-failure.png"));for(Window w:Window.getWindows())if(w instanceof ReviewDialog&&w.isVisible())GuiTest.screenshot(w,shots.resolve("refresh-viewer-failure.png"));System.exit(1);}
        finally{for(Window w:Window.getWindows())w.dispose();DicomIO.deleteTree(root);}System.exit(0);
    }
}
