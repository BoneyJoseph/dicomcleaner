package org.bnel.dicom;

import com.pixelmed.dicom.AttributeList;
import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Optional per-series overrides; null description retains each source instance value. */
final class ScanLabels {
    record Labels(String description,String protocol,String sequence) {
        Labels {
            description=description==null?null:validate(description,64);protocol=validate(protocol,64);sequence=validate(sequence,16);
        }
        private static String validate(String s,int limit){
            s=s==null?"":s.strip();
            if(s.length()>limit||s.chars().anyMatch(c->c<32||c==92))throw new IllegalArgumentException("Use up to 16 characters for Sequence Name and 64 for other labels; backslashes and control characters are not supported.");
            return s;
        }
        void apply(AttributeList list)throws Exception{
            if(description!=null)Policy.put(list,0x0008103e,description);
            Policy.put(list,0x00181030,protocol);
            Policy.put(list,0x00180024,sequence);
        }
    }
    record Series(String uid,String title,Set<String> descriptions,Set<String> protocols,Set<String> sequences,int files){}
    static List<Series> collect(List<Cleaner.Instance> files,Cleaner.Progress progress,BooleanSupplier cancel)throws Exception{
        Map<String,List<Cleaner.Instance>> groups=new LinkedHashMap<>();for(var f:files)groups.computeIfAbsent(f.series(),k->new ArrayList<>()).add(f);
        List<Series> result=new ArrayList<>();int n=0;
        for(var entry:groups.entrySet()){
            Set<String> desc=new LinkedHashSet<>(),protocol=new LinkedHashSet<>(),seq=new LinkedHashSet<>();
            for(var f:entry.getValue()){
                if(cancel.getAsBoolean())throw new java.util.concurrent.CancellationException();
                AttributeList l=DicomIO.read(f.source()).list();desc.add(Policy.value(l,0x0008103e));protocol.add(Policy.value(l,0x00181030));seq.add(Policy.value(l,0x00180024));progress.update("Reading scan labels in every file",++n,files.size());
            }
            result.add(new Series(entry.getKey(),entry.getValue().get(0).modality()+" · series "+(result.size()+1),desc,protocol,seq,entry.getValue().size()));
        }return result;
    }
    static Map<String,Labels> edit(Window owner,List<Series> series,Map<String,Labels> previous){
        JDialog d=new JDialog(owner,"Scan labels",Dialog.ModalityType.APPLICATION_MODAL);
        Theme.installWindow(d);
        final String keep="[Keep each source value]";
        DefaultTableModel model=new DefaultTableModel(new String[]{"Series","Files","Clean series label","Clean protocol name","Clean sequence name"},0){public boolean isCellEditable(int r,int c){return c>=2;}};
        for(Series s:series){Labels l=previous.getOrDefault(s.uid,new Labels(null,"",""));model.addRow(new Object[]{s.title,s.files,l.description==null?(s.descriptions.size()==1?s.descriptions.iterator().next():keep):l.description,l.protocol,l.sequence});}
        JTable table=new JTable(model);table.setRowHeight(28);table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);table.putClientProperty("terminateEditOnFocusLost",Boolean.TRUE);
        JTextArea source=new JTextArea();source.setEditable(false);source.setLineWrap(true);source.setWrapStyleWord(true);source.setMargin(new Insets(12,12,12,12));
        table.getSelectionModel().addListSelectionListener(e->{int i=table.getSelectedRow();if(i<0)return;Series s=series.get(i);source.setText("ALL original values found across "+s.files+" files in this series:\n\nSeries Description:\n"+String.join("\n",s.descriptions)+"\n\nProtocol Name:\n"+String.join("\n",s.protocols)+"\n\nSequence Name:\n"+String.join("\n",s.sequences));source.setCaretPosition(0);});
        Map<String,Labels> result=new HashMap<>();boolean[] saved={false};
        JPanel bottom=Theme.row(Theme.button("Save labels",true,()->{
            if(table.isEditing()&&!table.getCellEditor().stopCellEditing())return;
            try{for(int i=0;i<series.size();i++){String desc=String.valueOf(model.getValueAt(i,2));result.put(series.get(i).uid,new Labels(desc.equals(keep)||(series.get(i).descriptions.size()==1&&series.get(i).descriptions.contains(desc))?null:desc,String.valueOf(model.getValueAt(i,3)),String.valueOf(model.getValueAt(i,4))));}saved[0]=true;d.dispose();}catch(Exception e){Theme.error(d,e);}
        }),Theme.button("Cancel",false,d::dispose));
        d.add(new JLabel("  Series descriptions keep each source value by default. Protocol and sequence names start blank."),BorderLayout.NORTH);
        JSplitPane split=new JSplitPane(JSplitPane.VERTICAL_SPLIT,Theme.scroll(table),Theme.scroll(source));split.setResizeWeight(.5);d.add(split);d.add(Theme.footer(d,bottom),BorderLayout.SOUTH);
        d.setSize(1140,740);d.setLocationRelativeTo(owner);if(!series.isEmpty())table.setRowSelectionInterval(0,0);d.setVisible(true);return saved[0]?result:null;
    }
}
