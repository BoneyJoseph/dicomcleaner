package org.bnel.dicom;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.BooleanSupplier;

final class MetadataDifferences {
    record Value(String fingerprint,String value) {}
    record Difference(String path,String name,Map<Integer,Value> values,int variants,int missing) {}
    static List<Difference> compute(List<Cleaner.Instance> files,Cleaner.Progress progress,BooleanSupplier cancel) throws Exception {
        Map<String,Map<Integer,Value>> values=new TreeMap<>();Map<String,String> names=new HashMap<>();
        for(int i=0;i<files.size();i++) {
            if(cancel.getAsBoolean())throw new java.util.concurrent.CancellationException();
            for(DicomIO.Row row:DicomIO.rows(DicomIO.read(files.get(i).source()).list())) {
                int key=Policy.key(row.attribute().getTag());
                if(key==0x7fe00010||key==0x7fe00008||key==0x7fe00009)continue;
                String hash=DicomIO.binary(row.vr())?DicomIO.attributeHash(row.attribute()):row.vr()+":"+row.value();
                values.computeIfAbsent(row.path(),k->new TreeMap<>()).put(i,new Value(hash,row.value()));names.put(row.path(),row.name());
            }
            progress.update("Comparing all metadata",i+1,files.size());
        }
        List<Difference> result=new ArrayList<>();
        for(var e:values.entrySet()) {
            int variants=(int)e.getValue().values().stream().map(Value::fingerprint).distinct().count();
            int missing=files.size()-e.getValue().size();
            if(variants>1||missing>0)result.add(new Difference(e.getKey(),names.get(e.getKey()),e.getValue(),variants,missing));
        }
        return result;
    }
    static void show(Window owner,List<Cleaner.Instance> files,List<Difference> result) {
        JDialog d=new JDialog(owner,"Differences across every imported instance",Dialog.ModalityType.APPLICATION_MODAL);
        DefaultTableModel model=new DefaultTableModel(new String[]{"Tag / sequence path","Attribute","Distinct values","Files missing tag"},0){public boolean isCellEditable(int r,int c){return false;}};
        for(Difference r:result)model.addRow(new Object[]{r.path,r.name,r.variants,r.missing});
        JTable table=new JTable(model);table.setRowHeight(27);table.setAutoCreateRowSorter(true);
        JTextArea detail=new JTextArea();detail.setEditable(false);detail.setLineWrap(true);detail.setWrapStyleWord(true);detail.setMargin(new Insets(12,12,12,12));
        table.getSelectionModel().addListSelectionListener(e->{
            int selected=table.getSelectedRow();if(selected<0)return;
            Difference r=result.get(table.convertRowIndexToModel(selected));StringBuilder text=new StringBuilder(r.name+"  "+r.path+"\n\n");
            for(int i=0;i<files.size();i++){Value v=r.values.get(i);text.append(i+1).append(". ").append(files.get(i).source().getFileName()).append("\n").append(v==null?"[Tag absent]":v.value).append("\n\n");}
            detail.setText(text.toString());detail.setCaretPosition(0);
        });
        JSplitPane split=new JSplitPane(JSplitPane.VERTICAL_SPLIT,Theme.scroll(table),Theme.scroll(detail));split.setResizeWeight(.55);
        d.add(new JLabel("  "+files.size()+" files compared · "+result.size()+" differing/missing tags · Image pixel payloads excluded"),BorderLayout.NORTH);
        d.add(split);d.add(Theme.row(Theme.button("Close",false,d::dispose)),BorderLayout.SOUTH);
        d.setSize(1100,750);d.setLocationRelativeTo(owner);if(!result.isEmpty())table.setRowSelectionInterval(0,0);d.setVisible(true);
    }
}
