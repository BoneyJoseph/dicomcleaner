package org.bnel.dicom;

import javax.swing.*;
import javax.swing.event.*;
import java.awt.*;

/** View-only search. Hidden files stay in the imported set and current operation selection. */
final class SourceBrowser extends JPanel {
    final JTextField search=new JTextField();
    final JLabel count=new JLabel();
    final JScrollPane scroll;
    SourceBrowser(SourceTree tree,boolean expandControls){
        super(new BorderLayout(0,Theme.u(8)));setOpaque(false);
        JPanel top=new JPanel(new BorderLayout(0,Theme.u(12)));top.setOpaque(false);JPanel heading=Theme.heading("Sources",count);
        if(expandControls){JPanel icons=Theme.line(Theme.iconButton("collapseAll","Collapse to series",tree::collapseToSeries),Theme.iconButton("expandAll","Expand every series to files",tree::expandSeries));
            JPanel right=new JPanel(new BorderLayout(Theme.u(6),0));right.setOpaque(false);right.add(count);right.add(icons,BorderLayout.EAST);heading=new JPanel(new BorderLayout(Theme.u(8),0));heading.setOpaque(false);heading.add(Theme.label("Sources",14,true));heading.add(right,BorderLayout.EAST);count.setFont(Theme.bodyFont.deriveFont((float)Theme.u(12)));count.setForeground(Theme.MUTED);}
        top.add(heading,BorderLayout.NORTH);
        search.setName("Find in sources");search.putClientProperty("JTextField.placeholderText","Find in sources");search.setToolTipText("Filter source, series and file labels. Hidden files remain imported and selected for operations until you change the selection.");Theme.search(search);search.putClientProperty("JTextField.showClearButton",true);top.add(search);add(top,BorderLayout.NORTH);
        scroll=Theme.scroll(tree);scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);add(scroll);
        javax.swing.Timer filter=new javax.swing.Timer(160,e->tree.setFilter(search.getText()));filter.setRepeats(false);
        search.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){filter.restart();}public void removeUpdate(DocumentEvent e){filter.restart();}public void changedUpdate(DocumentEvent e){filter.restart();}});
        count.setText(String.valueOf(tree.inputs.size()));tree.addPropertyChangeListener("instanceCount",e->{count.setText(String.valueOf(tree.inputs.size()));search.setText("");});
        setMinimumSize(new Dimension(Theme.u(176),Theme.u(150)));
    }
}
