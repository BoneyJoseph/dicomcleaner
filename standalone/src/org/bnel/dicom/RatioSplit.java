package org.bnel.dicom;

import javax.swing.*;
import java.awt.*;

/** A user's divider ratio survives resize, temporary minimum-size clamping and tab switches. */
final class RatioSplit extends JSplitPane {
    double ratio;boolean applying;
    /** Optional: height/width the second component actually needs. The ratio then acts as an upper limit, so a
     *  short second pane (e.g. a few files) shrinks to its content instead of showing empty space. Content that fits
     *  within 45% of the space is shown exactly; larger content follows the (draggable, remembered) ratio. */
    java.util.function.IntSupplier secondContent;
    RatioSplit(int orientation,Component left,Component right,double initial){
        super(orientation,left,right);ratio=initial;setContinuousLayout(true);setBorder(null);setResizeWeight(initial);
        addPropertyChangeListener(DIVIDER_LOCATION_PROPERTY,e->{if(!applying&&extent()>getDividerSize())ratio=Math.max(.05,Math.min(.95,getDividerLocation()/(double)(extent()-getDividerSize())));});
    }
    int extent(){return getOrientation()==HORIZONTAL_SPLIT?getWidth():getHeight();}
    @Override public void doLayout(){
        applying=true;
        try{
            int total=extent()-getDividerSize();
            if(total>0){Dimension a=getLeftComponent().getMinimumSize(),b=getRightComponent().getMinimumSize();
                int low=getOrientation()==HORIZONTAL_SPLIT?a.width:a.height,high=total-(getOrientation()==HORIZONTAL_SPLIT?b.width:b.height);
                int location=(int)Math.round(total*ratio);if(secondContent!=null){int need=secondContent.getAsInt();location=need<=total*.45?total-need:Math.max(location,total-need);}if(high>=low)location=Math.max(low,Math.min(high,location));
                super.setDividerLocation(location);
            }
            super.doLayout();
            // BasicSplitPaneUI can distribute an initial remainder using preferred sizes.
            // Reconcile against the actual client geometry, including LAF border insets.
            int a=getOrientation()==HORIZONTAL_SPLIT?getLeftComponent().getWidth():getLeftComponent().getHeight();
            int b=getOrientation()==HORIZONTAL_SPLIT?getRightComponent().getWidth():getRightComponent().getHeight();
            int desired=(int)Math.round((a+b)*ratio);
            if(secondContent!=null){int need=secondContent.getAsInt();desired=need<=(a+b)*.45?a+b-need:Math.max(desired,a+b-need);}
            Dimension leftMin=getLeftComponent().getMinimumSize(),rightMin=getRightComponent().getMinimumSize();
            int low=getOrientation()==HORIZONTAL_SPLIT?leftMin.width:leftMin.height;
            int high=a+b-(getOrientation()==HORIZONTAL_SPLIT?rightMin.width:rightMin.height);
            if(high>=low)desired=Math.max(low,Math.min(high,desired));
            if(a!=desired){super.setDividerLocation(getDividerLocation()+desired-a);super.doLayout();}
        }finally{applying=false;}
    }
    void restore(AppSettings settings,String key){try{double value=Double.parseDouble(settings.text(key,String.valueOf(ratio)));if(value>.04&&value<.96)ratio=value;}catch(NumberFormatException ignored){}revalidate();}
    void remember(AppSettings settings,String key){settings.set(key,ratio);}
}
