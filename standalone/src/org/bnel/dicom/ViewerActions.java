package org.bnel.dicom;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

final class ViewerActions extends JPanel {
    final JComponent marking,exclude,recycle,close;boolean compact;
    ViewerActions(JComponent marking,JComponent exclude,JComponent recycle,JComponent close){
        super(new GridBagLayout());setOpaque(false);this.marking=marking;this.exclude=exclude;this.recycle=recycle;this.close=close;setBorder(BorderFactory.createEmptyBorder(8,0,0,0));arrange(true);

    }
    void arrange(boolean value){compact=value;removeAll();if(compact){put(Theme.bar(marking,close),0);put(Theme.bar(exclude,recycle),1);}else put(Theme.bar(Theme.line(marking,exclude,recycle),close),0);revalidate();}
    void put(Component c,int row){GridBagConstraints g=new GridBagConstraints();g.gridy=row;g.weightx=1;g.fill=GridBagConstraints.HORIZONTAL;g.insets=new Insets(row==0?0:Theme.u(8),0,0,0);add(c,g);}
}
