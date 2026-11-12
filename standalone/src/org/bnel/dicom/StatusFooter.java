package org.bnel.dicom;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

final class StatusFooter extends JPanel {
    final JLabel status,credit;
    final JPanel progressGroup,activity;
    boolean compact=true;
    StatusFooter(JLabel status,JLabel percent,JProgressBar progress,JButton cancel){
        super(new GridBagLayout());this.status=status;setOpaque(false);setBorder(BorderFactory.createEmptyBorder(8,0,8,0));setName("Status and author credit");
        status.setFont(Theme.bodyFont.deriveFont((float)Theme.u(12)));status.setForeground(Theme.MUTED);status.setMinimumSize(new Dimension(Theme.u(180),Theme.u(24)));status.setPreferredSize(new Dimension(Theme.u(320),Theme.u(24)));status.addPropertyChangeListener("text",e->status.setToolTipText(status.getText()));
        credit=Theme.label(Main.CREDIT,11,false);credit.setForeground(Theme.MUTED);credit.setHorizontalAlignment(SwingConstants.RIGHT);
        progress.setPreferredSize(new Dimension(Theme.u(150),Theme.u(5)));progress.setMinimumSize(new Dimension(Theme.u(100),Theme.u(5)));progress.setBorderPainted(false);
        percent.setFont(Theme.bodyFont.deriveFont((float)Theme.u(12)));percent.setHorizontalAlignment(SwingConstants.RIGHT);percent.setPreferredSize(new Dimension(Theme.u(145),Theme.u(24)));
        progressGroup=new JPanel(new GridBagLayout());progressGroup.setOpaque(false);
        GridBagConstraints c=new GridBagConstraints();c.weightx=1;c.fill=GridBagConstraints.HORIZONTAL;progressGroup.add(progress,c);c.weightx=0;c.insets=new Insets(0,Theme.u(8),0,0);progressGroup.add(percent,c);
        activity=new JPanel(new BorderLayout(Theme.u(12),0));activity.setOpaque(false);activity.add(progressGroup);activity.add(cancel,BorderLayout.EAST);
        arrange(false);

    }
    void arrange(boolean value){compact=value;removeAll();GridBagConstraints c=new GridBagConstraints();c.gridy=0;c.gridx=0;c.weightx=.4;c.fill=GridBagConstraints.HORIZONTAL;c.insets=new Insets(0,0,0,Theme.u(16));add(status,c);c.gridx=1;c.weightx=.6;add(activity,c);
        c.gridx=compact?0:2;c.gridy=compact?1:0;c.gridwidth=compact?2:1;c.weightx=0;c.insets=new Insets(compact?Theme.u(6):0,0,0,0);add(credit,c);revalidate();}
}
