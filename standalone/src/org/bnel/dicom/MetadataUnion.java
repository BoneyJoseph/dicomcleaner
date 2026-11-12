package org.bnel.dicom;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Union of all paths across all files, not an intersection or representative header. */
final class MetadataUnion extends JPanel {
    record FileEntry(Path path,String label){}
    record Value(String vr,String text,String hash){}
    record Tag(String path,String name,Map<Integer,Value> values,int distinct){}
    record Index(List<FileEntry> files,List<Tag> tags){}
    final DefaultTableModel model=new DefaultTableModel(new String[]{"Tag / sequence path","Attribute","Value / summary","Present","Missing","Values"},0){public boolean isCellEditable(int r,int c){return false;}};
    final DefaultTableModel perFile=new DefaultTableModel(new String[]{"File","VR","Full value (double-click to expand)"},0){public boolean isCellEditable(int r,int c){return false;}};
    final JTable table=new JTable(model),details=new JTable(perFile);
    final JSplitPane split;
    final JTextField filter=new JTextField();final JCheckBox differences=new JCheckBox("Only differences / missing tags");
    final JLabel status=new JLabel("Import files to index every metadata element"),headerCount=new JLabel("0 files · 0 tags");
    JPanel controls;
    java.util.function.Consumer<String> tagSelected=p->{};
    Index index=new Index(List.of(),List.of());
    MetadataUnion(){
        super(new BorderLayout(6,6));setOpaque(false);
        for(JTable t:new JTable[]{table,details}){t.setRowHeight(Theme.u(26));t.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);t.setFillsViewportHeight(true);t.setShowVerticalLines(false);t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);t.setAutoCreateRowSorter(true);}
        int[] widths={110,130,150,60,60,60};for(int i=0;i<widths.length;i++)table.getColumnModel().getColumn(i).setPreferredWidth(Theme.u(widths[i]));
        details.getColumnModel().getColumn(0).setPreferredWidth(Theme.u(140));details.getColumnModel().getColumn(1).setPreferredWidth(Theme.u(40));details.getColumnModel().getColumn(2).setPreferredWidth(Theme.u(200));
        Theme.valueRenderer(table,true);Theme.valueRenderer(details,false);
        table.getColumnModel().getColumn(0).setHeaderValue("Tag / path");details.getColumnModel().getColumn(2).setHeaderValue("Value");
        table.putClientProperty("bnel.flexColumn",2);
        JPanel top=new JPanel(new BorderLayout(8,4));controls=top;top.setOpaque(false);top.add(new JLabel("Find in ALL tags and values"),BorderLayout.WEST);top.add(filter);differences.setOpaque(false);top.add(differences,BorderLayout.EAST);add(top,BorderLayout.NORTH);
        JPanel detail=new JPanel(new BorderLayout(0,8));detail.setOpaque(false);detail.add(Theme.heading("Selected value",new JLabel("Per file")),BorderLayout.NORTH);detail.add(Theme.scroll(details));
        split=new RatioSplit(JSplitPane.VERTICAL_SPLIT,Theme.scroll(table),detail,.75);split.setBackground(Theme.SURFACE);split.getTopComponent().setMinimumSize(new Dimension(150,90));detail.setMinimumSize(new Dimension(150,90));split.setBorder(null);split.setContinuousLayout(true);add(split);JPanel foot=new JPanel(new BorderLayout(8,0));foot.setOpaque(false);foot.add(status);JButton full=Theme.button("Full value",false,this::fullValue);full.setToolTipText("Open the complete value for the selected file. Double-click a file value to do the same.");foot.add(full,BorderLayout.EAST);add(foot,BorderLayout.SOUTH);
        status.setFont(status.getFont().deriveFont((float)Theme.u(12)));status.setToolTipText("Select a tag to see every file, including absent values. Double-click a file value for complete text.");
        DocumentListener listener=new DocumentListener(){public void insertUpdate(DocumentEvent e){filter();}public void removeUpdate(DocumentEvent e){filter();}public void changedUpdate(DocumentEvent e){filter();}};
        filter.getDocument().addDocumentListener(listener);differences.addActionListener(e->filter());
        table.getSelectionModel().addListSelectionListener(e->{if(e.getValueIsAdjusting())return;perFile.setRowCount(0);int row=table.getSelectedRow();if(row<0)return;Tag tag=index.tags.get(table.convertRowIndexToModel(row));
            for(int i=0;i<index.files.size();i++){Value value=tag.values.get(i);perFile.addRow(new Object[]{index.files.get(i).label,value==null?"—":value.vr,value==null?"[Tag absent from this file]":value.text});}
            updateStatus();
            tagSelected.accept(tag.path);
        });
        details.addMouseListener(new MouseAdapter(){public void mouseClicked(MouseEvent e){if(e.getClickCount()==2)fullValue();}});
        details.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,0),"full");details.getActionMap().put("full",new AbstractAction(){public void actionPerformed(ActionEvent e){fullValue();}});
    }
    void hideControls(){remove(controls);}
    String selectedPath(){int row=table.getSelectedRow();return row<0?null:index.tags.get(table.convertRowIndexToModel(row)).path;}
    void selectPath(String path){
        for(int i=0;i<index.tags.size();i++)if(index.tags.get(i).path.equals(path)){int row=table.convertRowIndexToView(i);if(row>=0){table.setRowSelectionInterval(row,row);table.scrollRectToVisible(table.getCellRect(row,0,true));return;}break;}
        table.clearSelection();perFile.setRowCount(0);for(FileEntry file:index.files)perFile.addRow(new Object[]{file.label,"—","[Selected tag absent or filtered from this pane]"});
        status.setText(path+" · absent or hidden by the current filter");
    }
    void fullValue(){int row=details.getSelectedRow();if(row<0)return;String value=String.valueOf(perFile.getValueAt(details.convertRowIndexToModel(row),2));
        JTextArea text=new JTextArea(value);text.setEditable(false);text.setLineWrap(true);text.setWrapStyleWord(true);text.setCaretPosition(0);
        JDialog dialog=new JDialog(SwingUtilities.getWindowAncestor(this),"Complete metadata value",Dialog.ModalityType.APPLICATION_MODAL);dialog.add(Theme.scroll(text));dialog.add(Theme.row(Theme.button("Close",false,dialog::dispose)),BorderLayout.SOUTH);dialog.setSize(950,650);dialog.setLocationRelativeTo(this);dialog.setVisible(true);
    }
    void filter(){
        String query=filter.getText().toLowerCase(Locale.ROOT);
        @SuppressWarnings("unchecked") TableRowSorter<DefaultTableModel> sorter=(TableRowSorter<DefaultTableModel>)table.getRowSorter();
        sorter.setRowFilter(new RowFilter<DefaultTableModel,Integer>(){public boolean include(Entry<? extends DefaultTableModel,? extends Integer> e){Tag t=index.tags.get(e.getIdentifier());if(differences.isSelected()&&t.distinct<2&&t.values.size()==index.files.size())return false;return query.isBlank()||t.path.toLowerCase(Locale.ROOT).contains(query)||t.name.toLowerCase(Locale.ROOT).contains(query)||t.values.values().stream().anyMatch(v->v.text.toLowerCase(Locale.ROOT).contains(query));}});
        updateStatus();
    }
    void updateStatus(){headerCount.setText(index.files.size()+" files · "+index.tags.size()+" tags");status.setText(table.getRowCount()+" / "+index.tags.size()+" tags");}
    void setIndex(Index index){
        String selected=selectedPath();this.index=index;model.setRowCount(0);perFile.setRowCount(0);
        for(Tag t:index.tags){String summary=t.distinct==1?t.values.values().iterator().next().text:"[Values differ across files]";model.addRow(new Object[]{t.path,t.name,summary,t.values.size(),index.files.size()-t.values.size(),t.distinct});}filter();
        if(!index.tags.isEmpty()){String preferred=selected!=null?selected:index.tags.stream().filter(t->t.name.equals("PatientName")).map(Tag::path).findFirst().orElse(index.tags.get(0).path);selectPath(preferred);}
    }
    static Index compute(List<FileEntry> files,BooleanSupplier cancel)throws Exception {
        Map<String,Map<Integer,Value>> values=new TreeMap<>();Map<String,String> names=new HashMap<>();Map<Value,Value> intern=new HashMap<>();
        for(int i=0;i<files.size();i++){
            if(cancel.getAsBoolean())throw new java.util.concurrent.CancellationException();
            for(DicomIO.Row row:DicomIO.rows(DicomIO.read(files.get(i).path).list())){
                int tag=Policy.key(row.attribute().getTag());boolean pixels=tag==0x7fe00010||tag==0x7fe00008||tag==0x7fe00009;
                String hash=DicomIO.binary(row.vr())&&!pixels?DicomIO.attributeHash(row.attribute()):row.value();
                Value value=new Value(row.vr(),row.value(),hash);value=intern.computeIfAbsent(value,k->k);
                values.computeIfAbsent(row.path(),k->new LinkedHashMap<>()).put(i,value);names.put(row.path(),row.name());
            }
        }
        List<Tag> tags=new ArrayList<>();for(var entry:values.entrySet())tags.add(new Tag(entry.getKey(),names.get(entry.getKey()),Map.copyOf(entry.getValue()),(int)entry.getValue().values().stream().distinct().count()));
        return new Index(List.copyOf(files),List.copyOf(tags));
    }
}
