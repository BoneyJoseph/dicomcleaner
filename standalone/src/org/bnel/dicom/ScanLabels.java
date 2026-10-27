package org.bnel.dicom;

import com.pixelmed.dicom.AttributeList;
import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Per-series labels are explicitly reviewed. They never infer sequence type from unknown text. */
final class ScanLabels {
    record Labels(String description,String protocol,String sequence) {
        Labels {
            description=validate(description,64);protocol=validate(protocol,64);sequence=validate(sequence,16);
        }
        private static String validate(String s,int limit){
            s=s==null?"":s.strip();
            if(s.length()>limit||!s.matches("[A-Za-z0-9 _.*+()/-]*"))throw new IllegalArgumentException("Scan labels must use plain letters/numbers and simple punctuation; sequence names are limited to 16 characters, other labels to 64.");
            return s;
        }
        void apply(AttributeList list)throws Exception{
            if(!description.isEmpty())Policy.put(list,0x0008103e,description);
            if(!protocol.isEmpty())Policy.put(list,0x00181030,protocol);
            if(!sequence.isEmpty())Policy.put(list,0x00180024,sequence);
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
        JDialog d=new JDialog(owner,"Review non-identifying scan labels",Dialog.ModalityType.APPLICATION_MODAL);
        DefaultTableModel model=new DefaultTableModel(new String[]{"Series","Files","Clean series label","Clean protocol name","Clean sequence name"},0){public boolean isCellEditable(int r,int c){return c>=2;}};
        for(Series s:series){Labels l=previous.getOrDefault(s.uid,new Labels("","",""));model.addRow(new Object[]{s.title,s.files,l.description,l.protocol,l.sequence});}
        JTable table=new JTable(model);table.setRowHeight(30);table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);table.putClientProperty("terminateEditOnFocusLost",Boolean.TRUE);
        JTextArea source=new JTextArea();source.setEditable(false);source.setLineWrap(true);source.setWrapStyleWord(true);source.setMargin(new Insets(12,12,12,12));
        table.getSelectionModel().addListSelectionListener(e->{int i=table.getSelectedRow();if(i<0)return;Series s=series.get(i);source.setText("ALL original values found across "+s.files+" files in this series:\n\nSeries Description:\n"+String.join("\n",s.descriptions)+"\n\nProtocol Name:\n"+String.join("\n",s.protocols)+"\n\nSequence Name:\n"+String.join("\n",s.sequences));source.setCaretPosition(0);});
        JComboBox<String> preset=new JComboBox<>(new String[]{"T1-weighted","T2-weighted","FLAIR","T1 FLAIR","T2 FLAIR","DWI","ADC","SWI","CT","Registration"});
        JCheckBox verified=new JCheckBox("I reviewed the cleaned labels; they contain scan information only, no identifiers");
        Map<String,Labels> result=new HashMap<>();boolean[] saved={false};
        JPanel bottom=new JPanel();bottom.setLayout(new BoxLayout(bottom,BoxLayout.Y_AXIS));
        bottom.add(Theme.row(preset,Theme.button("Set selected series label",false,()->{int i=table.getSelectedRow();if(i>=0)model.setValueAt(preset.getSelectedItem(),i,2);})));
        bottom.add(verified);bottom.add(Theme.row(Theme.button("Save reviewed labels",true,()->{
            if(table.isEditing()&&!table.getCellEditor().stopCellEditing())return;
            if(!verified.isSelected()){Theme.error(d,new IllegalArgumentException("Review the entered scan labels and confirm they contain no identifiers."));return;}
            try{for(int i=0;i<series.size();i++)result.put(series.get(i).uid,new Labels(String.valueOf(model.getValueAt(i,2)),String.valueOf(model.getValueAt(i,3)),String.valueOf(model.getValueAt(i,4))));saved[0]=true;d.dispose();}catch(Exception e){Theme.error(d,e);}
        }),Theme.button("Cancel",false,d::dispose)));
        d.add(new JLabel("  Blank labels are removed. Enter only reviewed scan information. Acquisition parameters remain per instance."),BorderLayout.NORTH);
        JSplitPane split=new JSplitPane(JSplitPane.VERTICAL_SPLIT,Theme.scroll(table),Theme.scroll(source));split.setResizeWeight(.5);d.add(split);d.add(bottom,BorderLayout.SOUTH);
        d.setSize(1140,740);d.setLocationRelativeTo(owner);if(!series.isEmpty())table.setRowSelectionInterval(0,0);d.setVisible(true);return saved[0]?result:null;
    }
}
