package org.bnel.dicom;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/** Two shared column tracks; compact mode moves only long option groups into bottom popovers. */
final class SettingsStrip extends JPanel {
    final Main app;
    final JPanel grid=new JPanel(new GridBagLayout()),metadataWide,exportWide,replace,date;
    final JPanel metadataCompact,exportCompact;
    final JPopupMenu metadataPopup=new JPopupMenu(),exportPopup=new JPopupMenu();
    final JLabel nameLabel=new JLabel("Patient Name"),idLabel=new JLabel("Patient ID"),metaSummary=new JLabel(),exportSummary=new JLabel();
    final JButton metadataButton,exportButton;
    boolean compact;int wideMinimum;
    SettingsStrip(Main app){
        super(new BorderLayout());this.app=app;setOpaque(false);setName("Cleaning and export settings");grid.setOpaque(false);
        metadataWide=Theme.line(Theme.label("Metadata",13,true),app.minimal,app.siemens,app.identifiers,app.scanLabels,app.differences);
        exportWide=Theme.line(Theme.label("Export",13,true),app.hierarchy,app.originalNames,app.zip,app.dicomdir);
        replace=Theme.line(Theme.label("Replace",13,true),nameLabel,app.patientName,app.linkedIdentity,idLabel,app.patientId);
        date=Theme.line(new JLabel("Synthetic date"),app.dateMode,app.date);
        for(JTextField field:new JTextField[]{app.patientName,app.patientId})fixed(field,152);
        fixed(app.date,106);fixed(app.dateMode,150);
        metadataButton=Theme.button("Metadata options…",false,()->show(metadataPopup,metadataButton()));metadataButton.setToolTipText("Research metadata, identifier rules, scan labels and file comparison.");
        exportButton=Theme.button("Export options…",false,()->show(exportPopup,exportButton()));exportButton.setToolTipText("Choose folder hierarchy, original names, ZIP output and DICOMDIR.");
        metadataCompact=Theme.line(metadataButton,metaSummary);exportCompact=Theme.line(exportButton,exportSummary);
        metaSummary.setForeground(Theme.MUTED);exportSummary.setForeground(Theme.MUTED);
        wideMinimum=Math.max(metadataWide.getPreferredSize().width,replace.getPreferredSize().width)+Math.max(exportWide.getPreferredSize().width,date.getPreferredSize().width)+Theme.u(60);
        add(Theme.card(null,grid));arrange(false);

        for(JCheckBox b:new JCheckBox[]{app.minimal,app.siemens,app.hierarchy,app.originalNames,app.zip,app.dicomdir})b.addActionListener(e->summary());
        summary();
    }
    JButton metadataButton(){return metadataButton;} JButton exportButton(){return exportButton;}
    static void fixed(JComponent c,int width){Dimension d=new Dimension(Theme.u(width),Theme.u(32));c.setPreferredSize(d);c.setMinimumSize(d);c.setMaximumSize(d);}
    /** Compact-mode summaries name the options that are on, e.g. "Export: hierarchy, ZIP". */
    void summary(){
        java.util.List<String> meta=new java.util.ArrayList<>(),export=new java.util.ArrayList<>();
        if(app.minimal.isSelected())meta.add("essential metadata");
        if(app.siemens.isSelected())meta.add("DWI/fMRI tags kept");
        if(app.hierarchy.isSelected())export.add("hierarchy");
        if(app.originalNames.isSelected())export.add("original names");
        if(app.zip.isSelected())export.add("ZIP");
        if(app.dicomdir.isSelected())export.add("DICOMDIR");
        metaSummary.setText(meta.isEmpty()?"Default cleaner":String.join(", ",meta));
        exportSummary.setText(export.isEmpty()?"Plain folder":String.join(", ",export));
        metaSummary.setToolTipText(metaSummary.getText());exportSummary.setToolTipText(exportSummary.getText());
    }
    void show(JPopupMenu menu,JButton anchor){summary();menu.show(anchor,0,-menu.getPreferredSize().height-Theme.u(8));}
    void arrange(boolean value){
        compact=value;metadataPopup.setVisible(false);exportPopup.setVisible(false);grid.removeAll();metadataPopup.removeAll();exportPopup.removeAll();
        if(value){
            popup(metadataPopup,app.minimal,app.siemens,app.identifiers,app.scanLabels,app.differences);popup(exportPopup,app.hierarchy,app.originalNames,app.zip,app.dicomdir);
        }else{
            metadataWide.removeAll();append(metadataWide,Theme.label("Metadata",13,true),app.minimal,app.siemens,app.identifiers,app.scanLabels,app.differences);
            exportWide.removeAll();append(exportWide,Theme.label("Export",13,true),app.hierarchy,app.originalNames,app.zip,app.dicomdir);
        }
        nameLabel.setText(value?"Name":"Patient Name");idLabel.setText(value?"ID":"Patient ID");
        for(JTextField field:new JTextField[]{app.patientName,app.patientId})fixed(field,value?132:152);
        put(value?metadataCompact:metadataWide,0,0,.53);put(value?exportCompact:exportWide,2,0,.47);put(replace,0,1,.53);put(date,2,1,.47);
        JSeparator divider=new JSeparator(SwingConstants.VERTICAL);divider.setForeground(Theme.BORDER);GridBagConstraints c=new GridBagConstraints();c.gridx=1;c.gridy=0;c.gridheight=2;c.fill=GridBagConstraints.VERTICAL;c.insets=new Insets(0,Theme.u(12),0,Theme.u(12));grid.add(divider,c);
        summary();revalidate();repaint();
    }
    void put(Component component,int x,int y,double weight){GridBagConstraints c=new GridBagConstraints();c.gridx=x;c.gridy=y;c.weightx=weight;c.fill=GridBagConstraints.HORIZONTAL;c.anchor=GridBagConstraints.WEST;c.insets=new Insets(y==0?0:Theme.u(8),0,0,0);grid.add(component,c);}
    static void append(JPanel p,Component... components){for(int i=0;i<components.length;i++){if(i>0)p.add(Box.createHorizontalStrut(Theme.u(8)));p.add(components[i]);}}
    static void popup(JPopupMenu menu,JComponent... items){JPanel p=new JPanel(new GridLayout(0,1,0,Theme.u(8)));p.setBackground(Theme.SURFACE);p.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));for(JComponent item:items)p.add(item);menu.add(p);}
}
