package org.bnel.dicom;

import com.pixelmed.dicom.*;
import com.pixelmed.display.SourceImage;
import com.pixelmed.display.WindowCenterAndWidth;
import javax.swing.SwingUtilities;
import java.awt.image.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** One decoder, bounded frame cache, and a single replaceable pending navigation request. */
final class ViewerFrames implements AutoCloseable {
    record Key(int file,int frame) {}
    static final class Frame {
        final BufferedImage raw;final int pages;final boolean gray,signed,inverted,padded;
        final double slope,intercept,pad,padLimit,window,center;final String message;
        private BufferedImage rendered;private double lastWidth=Double.NaN,lastCenter;
        Frame(BufferedImage raw,int pages,SourceImage image,int n,AttributeList attributes,String message){
            this.raw=raw;this.pages=pages;this.message=message;gray=image!=null&&image.isGrayscale();
            signed=image!=null&&image.isSigned();inverted=image!=null&&image.isInverted();padded=image!=null&&image.isPadded();
            slope=image==null?1:image.getModalityTransform().getRescaleSlope(n);intercept=image==null?0:image.getModalityTransform().getRescaleIntercept(n);
            pad=image==null?0:image.getPadValue();padLimit=image==null?0:image.getPadRangeLimit();
            double range=image==null?255:Math.max(1,Math.abs((image.getMaximum()-image.getMinimum())*slope));
            window=Attribute.getSingleDoubleValueOrDefault(attributes,TagFromName.WindowWidth,range);
            center=Attribute.getSingleDoubleValueOrDefault(attributes,TagFromName.WindowCenter,image==null?127:(image.getMaximum()+image.getMinimum())/2*slope+intercept);
        }
        long bytes(){return raw==null?256:(long)raw.getWidth()*raw.getHeight()*12;}
        BufferedImage render(double width,double center){
            if(raw==null||!gray)return raw;
            if(rendered==null||lastWidth!=width||lastCenter!=center){rendered=WindowCenterAndWidth.applyWindowCenterAndWidthLinear(raw,center,width,signed,inverted,slope,intercept,padded,(int)pad,(int)padLimit,false);lastWidth=width;lastCenter=center;}
            return rendered;
        }
    }
    record Result(Key key,Frame frame,String error) {}
    private record Request(long id,Key key,List<Key> ahead,Consumer<Result> result) {}
    final List<Path> paths;
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"BNEL-frame-decoder");t.setDaemon(true);return t;});
    private final LinkedHashMap<Key,Frame> cache=new LinkedHashMap<>(128,.75f,true);
    private final long budget;private long bytes,generation;private boolean running,closed;private Request pending;
    private SourceImage image;private ReportPreview report;private AttributeList attributes;private int opened=-1;
    ViewerFrames(List<Path> paths){this(paths,Math.min(192L*1024*1024,Runtime.getRuntime().maxMemory()/6));}
    ViewerFrames(List<Path> paths,long budget){this.paths=List.copyOf(paths);this.budget=budget;}
    synchronized int cached(){return cache.size();}synchronized long cachedBytes(){return bytes;}
    void request(Key key,List<Key> ahead,Consumer<Result> result){
        Frame hit;long id;
        synchronized(this){if(closed)return;id=++generation;hit=cache.get(key);pending=new Request(id,key,List.copyOf(ahead),hit==null?result:null);if(!running){running=true;worker.execute(this::drain);}}
        if(hit!=null)result.accept(new Result(key,hit,""));
    }
    private void drain(){
        try{while(true){Request r;synchronized(this){if(closed||pending==null)return;r=pending;pending=null;}
            Result result;
            try{result=new Result(r.key,decode(r.key),"");}catch(Exception e){result=new Result(r.key,null,"Preview unavailable for this file. Use Report / text or Full metadata; marking and exclusion are available.");}
            Result value=result;if(r.result!=null)SwingUtilities.invokeLater(()->{synchronized(this){if(closed||r.id!=generation)return;}r.result.accept(value);});
            for(Key next:r.ahead){synchronized(this){if(closed||pending!=null)break;}try{decode(next);}catch(Exception ignored){}}
        }}finally{synchronized(this){running=false;if(!closed&&pending!=null){running=true;worker.execute(this::drain);}}if(closed)release();}
    }
    private Frame decode(Key key)throws Exception{
        synchronized(this){Frame hit=cache.get(key);if(hit!=null)return hit;if(closed)throw new CancellationException();}
        if(opened!=key.file){release();attributes=DicomIO.read(paths.get(key.file)).list();opened=key.file;
            try{if(attributes.getPixelData()!=null)image=new SourceImage(attributes);else report=ReportPreview.open(attributes);}catch(Exception e){release();throw e;}}
        BufferedImage raw=null;int count=1;
        if(image!=null){count=image.getNumberOfBufferedImages();raw=image.getBufferedImage(key.frame);}
        else if(report!=null){count=report.pages();raw=report.page(key.frame);}
        if(raw!=null){ColorModel model=raw.getColorModel();raw=new BufferedImage(model,raw.copyData(null),model.isAlphaPremultiplied(),null);}
        Frame f=new Frame(raw,count,image,key.frame,attributes,raw==null?"Non-image object — use Report / text or Full metadata":"");
        synchronized(this){if(closed)throw new CancellationException();Frame previous=cache.put(key,f);if(previous!=null)bytes-=previous.bytes();bytes+=f.bytes();Iterator<Map.Entry<Key,Frame>> it=cache.entrySet().iterator();while(bytes>budget&&cache.size()>1&&it.hasNext()){bytes-=it.next().getValue().bytes();it.remove();}}
        return f;
    }
    private void release(){if(image!=null)try{image.close();}catch(Throwable ignored){}if(report!=null)try{report.close();}catch(Exception ignored){}image=null;report=null;attributes=null;opened=-1;}
    void releaseFile(int file)throws Exception {
        synchronized(this){generation++;pending=null;}
        worker.submit(()->{if(opened==file)release();}).get();
    }
    void invalidate(int file){synchronized(this){generation++;pending=null;Iterator<Map.Entry<Key,Frame>> it=cache.entrySet().iterator();while(it.hasNext()){var e=it.next();if(e.getKey().file==file){bytes-=e.getValue().bytes();it.remove();}}}worker.execute(()->{if(opened==file)release();});}
    public void close(){synchronized(this){closed=true;generation++;pending=null;cache.clear();bytes=0;}worker.execute(this::release);worker.shutdown();}
}
