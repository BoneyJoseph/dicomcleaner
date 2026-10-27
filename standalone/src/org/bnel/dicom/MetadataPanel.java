package org.bnel.dicom;

import com.pixelmed.dicom.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

final class MetadataPanel extends JPanel {
    final DefaultTableModel model=new DefaultTableModel(new String[]{"Tag / sequence path","Attribute","VR","VM","Value"},0){
        @Override public boolean isCellEditable(int r,int c){return false;}
    };
    final JTable table=new JTable(model);
    final JTextField search=new JTextField();
    final JLabel count=new JLabel("No instance selected");
    List<DicomIO.Row> rows=List.of();
    MetadataPanel(){
        super(new BorderLayout(0,8));setOpaque(false);
        search.putClientProperty("JTextField.placeholderText","Find any tag or value");search.setToolTipText("Search all tags, names, nested sequence paths, and complete values");
        JPanel top=new JPanel(new BorderLayout(8,0));top.setOpaque(false);top.add(new JLabel("Find"),BorderLayout.WEST);top.add(search);add(top,BorderLayout.NORTH);
        table.setRowHeight(26);table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        int[] widths={165,210,45,40,330};for(int i=0;i<widths.length;i++)table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        table.setFillsViewportHeight(true);table.setShowVerticalLines(false);table.setBackground(Color.WHITE);
        TableRowSorter<DefaultTableModel> sorter=new TableRowSorter<>(model);table.setRowSorter(sorter);
        search.getDocument().addDocumentListener(new DocumentListener(){
            public void insertUpdate(DocumentEvent e){filter();} public void removeUpdate(DocumentEvent e){filter();} public void changedUpdate(DocumentEvent e){filter();}
            void filter(){sorter.setRowFilter(search.getText().isBlank()?null:RowFilter.regexFilter("(?i)"+Pattern.quote(search.getText())));count.setText(table.getRowCount()+" / "+rows.size()+" tags · Double-click for full value");}
        });
        table.addMouseListener(new MouseAdapter(){@Override public void mouseClicked(MouseEvent e){if(e.getClickCount()==2)inspect();}});
        table.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0),"inspect");
        table.getActionMap().put("inspect",new AbstractAction(){public void actionPerformed(ActionEvent e){inspect();}});
        add(Theme.scroll(table),BorderLayout.CENTER);
        JPanel bottom=new JPanel(new BorderLayout(5,0));bottom.setOpaque(false);count.setFont(count.getFont().deriveFont(12f));bottom.add(count);
        bottom.add(Theme.button("Full value",false,this::inspect),BorderLayout.EAST);add(bottom,BorderLayout.SOUTH);
    }
    void setData(AttributeList list){
        rows=list==null?List.of():DicomIO.rows(list);model.setRowCount(0);
        for(DicomIO.Row r:rows)model.addRow(new Object[]{r.path(),r.name(),r.vr(),r.vm(),r.value()});
        count.setText(rows.size()+" tags · Double-click for full value");
    }
    void inspect(){
        int selected=table.getSelectedRow();if(selected<0)return;
        DicomIO.Row row=rows.get(table.convertRowIndexToModel(selected));
        JDialog d=new JDialog(SwingUtilities.getWindowAncestor(this),row.name(),Dialog.ModalityType.APPLICATION_MODAL);
        d.setLayout(new BorderLayout(10,10));
        JTextArea area=new JTextArea(row.value());area.setEditable(false);area.setLineWrap(true);area.setWrapStyleWord(true);
        area.setFont(new Font(Font.MONOSPACED,Font.PLAIN,14));area.setMargin(new Insets(12,12,12,12));
        d.add(new JLabel("  "+row.path()+"   VR "+row.vr()+"   VM "+row.vm()+"   Length "+row.bytes()),BorderLayout.NORTH);
        d.add(Theme.scroll(area),BorderLayout.CENTER);
        JPanel actions=Theme.row(Theme.button("Copy displayed value",false,()->Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(area.getText()),null)));
        if(DicomIO.binary(row.vr())){
            actions.add(Theme.button("Inspect first 64 KB",false,()->{
                try{
                    LimitedBytes bytes=new LimitedBytes(65536);
                    try(DicomOutputStream out=new DicomOutputStream(bytes,null,TransferSyntax.ExplicitVRLittleEndian)){row.attribute().write(out);}catch(PreviewLimit ignored){}
                    area.setText("DICOM-encoded element (tag and value). Preview limited to 64 KB.\nUse Save full element for every byte.\n\n"+HexFormat.ofDelimiter(" ").formatHex(bytes.toByteArray()));
                }catch(Exception e){Theme.error(d,e);}
            }));
            actions.add(Theme.button("Save full element",false,()->{
                JFileChooser chooser=new JFileChooser();chooser.setSelectedFile(new File("dicom-element.bin"));
                if(chooser.showSaveDialog(d)!=JFileChooser.APPROVE_OPTION)return;
                try(DicomOutputStream out=new DicomOutputStream(Files.newOutputStream(chooser.getSelectedFile().toPath(),StandardOpenOption.CREATE_NEW),null,TransferSyntax.ExplicitVRLittleEndian)){
                    row.attribute().write(out);
                }catch(Exception e){Theme.error(d,e);}
            }));
        }
        actions.add(Theme.button("Close",false,d::dispose));d.add(actions,BorderLayout.SOUTH);
        d.setSize(950,600);d.setLocationRelativeTo(this);d.setVisible(true);
    }
    private static class LimitedBytes extends ByteArrayOutputStream {
        final int limit;LimitedBytes(int limit){this.limit=limit;}
        @Override public synchronized void write(int b){if(count<limit)super.write(b);else throw new PreviewLimit();}
        @Override public synchronized void write(byte[] b,int o,int len){int n=Math.min(len,limit-count);super.write(b,o,n);if(n<len)throw new PreviewLimit();}
    }
    private static class PreviewLimit extends RuntimeException {}
}
