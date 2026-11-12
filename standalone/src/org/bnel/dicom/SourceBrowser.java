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
        JPanel top=new JPanel(new BorderLayout(0,Theme.u(12)));top.setOpaque(false);top.add(Theme.heading("Sources",count),BorderLayout.NORTH);
        search.setName("Find in sources");search.putClientProperty("JTextField.placeholderText","Find in sources");search.setToolTipText("Filter source, series and file labels. Hidden files remain imported and selected for operations until you change the selection.");Theme.search(search);search.putClientProperty("JTextField.showClearButton",true);top.add(search);add(top,BorderLayout.NORTH);
        scroll=Theme.scroll(tree);scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);add(scroll);
        javax.swing.Timer filter=new javax.swing.Timer(160,e->tree.setFilter(search.getText()));filter.setRepeats(false);
        search.getDocument().addDocumentListener(new DocumentListener(){public void insertUpdate(DocumentEvent e){filter.restart();}public void removeUpdate(DocumentEvent e){filter.restart();}public void changedUpdate(DocumentEvent e){filter.restart();}});
        count.setText(String.valueOf(tree.inputs.size()));tree.addPropertyChangeListener("instanceCount",e->{count.setText(String.valueOf(tree.inputs.size()));search.setText("");});
        if(expandControls)add(Theme.bar(Theme.line(Theme.button("Collapse",false,tree::collapseToSeries)),Theme.line(Theme.button("Expand",false,tree::expandSeries))),BorderLayout.SOUTH);
        setMinimumSize(new Dimension(Theme.u(176),Theme.u(150)));
    }
}
