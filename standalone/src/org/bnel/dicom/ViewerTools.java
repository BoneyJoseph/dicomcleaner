package org.bnel.dicom;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/** Explicit wide/compact control rows, leaving every control reachable without text shrinking. */
final class ViewerTools extends JPanel {
    final JComponent transport,scrub,adjustments,blackout;boolean compact,stackBelow;
    ViewerTools(JComponent transport,JComponent scrub,JComponent adjustments,JComponent blackout){
        super(new GridBagLayout());setOpaque(false);this.transport=transport;this.scrub=scrub;this.adjustments=adjustments;this.blackout=blackout;
        arrange(true);
    }
    void arrange(boolean value){compact=value;stackBelow=getWidth()<Theme.u(680);removeAll();int row=0;
        if(stackBelow){put(transport,row++);put(scrub,row++);}else{JPanel top=new JPanel(new BorderLayout(Theme.u(12),0));top.setOpaque(false);top.add(transport,BorderLayout.WEST);top.add(scrub);put(top,row++);}
        if(compact){put(adjustments,row++);if(blackout.isVisible())put(blackout,row);}else put(Theme.bar(adjustments,blackout),row);revalidate();}
    void put(Component c,int row){GridBagConstraints g=new GridBagConstraints();g.gridy=row;g.weightx=1;g.fill=GridBagConstraints.HORIZONTAL;g.anchor=GridBagConstraints.WEST;g.insets=new Insets(Theme.u(8),0,0,0);add(c,g);}
}
