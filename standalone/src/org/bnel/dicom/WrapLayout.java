package org.bnel.dicom;
import java.awt.*;
/** Flow layout that reports the actual wrapped height, so controls never vanish on portrait screens. */
final class WrapLayout extends FlowLayout {
    WrapLayout(){super(LEFT,10,5);}
    public Dimension preferredLayoutSize(Container target){return size(target,false);}
    public Dimension minimumLayoutSize(Container target){return size(target,true);}
    private Dimension size(Container target,boolean minimum){
        synchronized(target.getTreeLock()){
            int width=target.getWidth();Container parent=target;while(width<=0&&parent.getParent()!=null){parent=parent.getParent();width=parent.getWidth();}
            if(width<=0)width=1200;Insets in=target.getInsets();int max=Math.max(1,width-in.left-in.right-2*getHgap()),rowWidth=0,rowHeight=0,totalHeight=0,totalWidth=0;
            for(Component c:target.getComponents())if(c.isVisible()){
                Dimension d=minimum?c.getMinimumSize():c.getPreferredSize();
                if(rowWidth>0&&rowWidth+getHgap()+d.width>max){totalWidth=Math.max(totalWidth,rowWidth);totalHeight+=rowHeight+getVgap();rowWidth=0;rowHeight=0;}
                rowWidth+=(rowWidth==0?0:getHgap())+d.width;rowHeight=Math.max(rowHeight,d.height);
            }
            totalWidth=Math.max(totalWidth,rowWidth);totalHeight+=rowHeight;return new Dimension(totalWidth+in.left+in.right+2*getHgap(),totalHeight+in.top+in.bottom+2*getVgap());
        }
    }
}
