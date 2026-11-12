package org.bnel.dicom;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;

/** One shared search and two simultaneously visible, tag-linked metadata indexes. */
final class MetadataCompare extends JPanel {
    final MetadataUnion original,cleaned;
    final JSplitPane split;
    final JCheckBox link=new JCheckBox("Link selected tag",true);
    boolean selecting;
    MetadataCompare(MetadataUnion original,MetadataUnion cleaned){
        super(new BorderLayout(6,6));setOpaque(false);this.original=original;this.cleaned=cleaned;
        original.hideControls();cleaned.hideControls();cleaned.filter.setDocument(original.filter.getDocument());
        // setDocument does not move the listener registered on the old document.
        original.filter.getDocument().addDocumentListener(new DocumentListener(){
            public void insertUpdate(DocumentEvent e){cleaned.filter();}public void removeUpdate(DocumentEvent e){cleaned.filter();}public void changedUpdate(DocumentEvent e){cleaned.filter();}
        });
        original.filter.putClientProperty("JTextField.placeholderText","Search all tags and values in both panes");
        Theme.search(original.filter);
        JPanel top=new JPanel(new BorderLayout(8,0));top.setOpaque(false);top.add(original.filter);
        original.differences.setText("Varying / missing tags");original.differences.setToolTipText("Show tags whose values differ between files, or which are absent from some files in that pane.");link.setOpaque(false);
        top.add(Theme.line(original.differences,link),BorderLayout.EAST);add(top,BorderLayout.NORTH);
        original.differences.addActionListener(e->{cleaned.differences.setSelected(original.differences.isSelected());cleaned.filter();});
        split=new RatioSplit(JSplitPane.HORIZONTAL_SPLIT,pane("All original metadata",original),pane("All cleaned metadata",cleaned),.5);
        split.setResizeWeight(.5);split.setContinuousLayout(true);split.setBorder(null);
        split.getLeftComponent().setMinimumSize(new Dimension(Theme.u(240),180));split.getRightComponent().setMinimumSize(new Dimension(Theme.u(240),180));add(split);
        original.tagSelected=path->select(cleaned,path);cleaned.tagSelected=path->select(original,path);
        link.addActionListener(e->refreshLink());
        original.split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY,e->alignDetails(original,cleaned));
        cleaned.split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY,e->alignDetails(cleaned,original));

    }
    static JPanel pane(String title,MetadataUnion union){JPanel panel=new JPanel(new BorderLayout(0,Theme.u(12)));panel.setOpaque(false);panel.add(Theme.heading(title,union.headerCount),BorderLayout.NORTH);panel.add(union);return Theme.card(null,panel);}
    boolean aligning;
    void alignDetails(MetadataUnion source,MetadataUnion target){if(aligning||((RatioSplit)source.split).applying)return;aligning=true;try{target.split.setDividerLocation(source.split.getDividerLocation());}finally{aligning=false;}}
    void select(MetadataUnion target,String path){if(selecting||!link.isSelected())return;selecting=true;try{target.selectPath(path);}finally{selecting=false;}}
    void refreshLink(){String path=original.selectedPath();if(path!=null)select(cleaned,path);}
}
