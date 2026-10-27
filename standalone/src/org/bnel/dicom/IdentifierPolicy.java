package org.bnel.dicom;

import com.pixelmed.dicom.*;
import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Operator choices apply only to eligible text; typed fields retain strict DICOM handling. */
final class IdentifierPolicy {
    enum Category {
        PERSON("Other names / physicians / operators"), OTHER_ID("Other IDs / accession / clinic numbers"),
        CONTACT("Addresses / telephone / email"), INSTITUTION("Institutions / departments / locations"),
        DEVICE("Manufacturer / model / device identifiers"), TRIAL("Clinical trial identifiers");
        final String label;Category(String label){this.label=label;}
        public String toString(){return label;}
    }
    enum Mode { REMOVE, DEFAULT, RANDOM, CUSTOM }
    record Rule(Mode mode,String custom){Rule{Objects.requireNonNull(mode);custom=custom==null?"":custom.strip();if(mode==Mode.CUSTOM&&(!custom.matches("[A-Za-z0-9 _^.-]{1,16}")))throw new IllegalArgumentException("Custom placeholders need 1–16 plain letters/numbers or spaces, ^ . _ -. Enter no identifying information.");}}
    private final Map<Category,Rule> rules;
    private final Map<String,String> random=new HashMap<>();
    IdentifierPolicy(){rules=Map.of();}
    IdentifierPolicy(Map<Category,Rule> rules){this.rules=Map.copyOf(rules);}
    Rule rule(Category c){return rules.getOrDefault(c,new Rule(Mode.REMOVE,""));}
    IdentifierPolicy fresh(){return new IdentifierPolicy(rules);}
    static IdentifierPolicy load(AppSettings settings){
        Map<Category,Rule> rules=new EnumMap<>(Category.class);
        for(Category c:Category.values())try{rules.put(c,new Rule(Mode.valueOf(settings.text("identifier."+c.name()+".mode","REMOVE")),settings.text("identifier."+c.name()+".custom","")));}catch(Exception ignored){}
        return new IdentifierPolicy(rules);
    }
    void save(AppSettings settings){for(Category c:Category.values()){Rule r=rule(c);settings.set("identifier."+c.name()+".mode",r.mode);settings.set("identifier."+c.name()+".custom",r.custom);}}
    static Category category(AttributeList list,Attribute a){
        int key=Policy.key(a.getTag());if(key==0x00100010||key==0x00100020)return null;
        String vr=new String(a.getVR(),StandardCharsets.US_ASCII),name=list.getDictionary().getNameFromTag(a.getTag());
        name=name==null?"":name.toLowerCase(Locale.ROOT);
        if(name.contains("clinicaltrial"))return Category.TRIAL;
        if(vr.equals("PN")||name.contains("physician")||name.contains("operator")||name.contains("technician")||name.contains("personname"))return Category.PERSON;
        if(name.contains("address")||name.contains("telephone")||name.contains("telecom")||name.contains("email")||name.contains("phone"))return Category.CONTACT;
        if(name.contains("institution")||name.contains("department")||name.contains("stationname")||name.contains("location")||name.contains("country")||name.contains("region")){
            if(key==0x00201041||name.contains("anatomic")||name.contains("pixel"))return null;
            return Category.INSTITUTION;
        }
        if(name.contains("manufacturer")||name.contains("modelname")||name.contains("deviceserial")||name.contains("deviceid")||name.contains("detectorid")||name.contains("softwareversion"))return Category.DEVICE;
        if(key==0x00080050||key==0x00200010||name.contains("otherpatient")||name.contains("admissionid")||name.contains("medicalrecord")||name.contains("insurance")||name.contains("issuer")||name.contains("accession")||name.contains("patientid")||name.contains("acquisitionid"))return Category.OTHER_ID;
        return null;
    }
    boolean apply(AttributeList list,Attribute a)throws DicomException{
        Category category=category(list,a);if(category==null)return false;
        String vr=new String(a.getVR(),StandardCharsets.US_ASCII);
        // Sequence structure, UIDs and dates must never be replaced with arbitrary strings.
        if(a instanceof SequenceAttribute||Set.of("UI","DA","DT","TM").contains(vr))return false;
        Rule rule=rule(category);
        if(rule.mode==Mode.REMOVE||!Set.of("PN","LO","SH","LT","ST","UT","UC","AE").contains(vr)){list.remove(a.getTag());return true;}
        String source=a.getDelimitedStringValuesOrEmptyString();
        String replacement=switch(rule.mode){
            case DEFAULT->"REDACTED";
            case RANDOM->random.computeIfAbsent(category.name()+"\u0000"+source,k->"R"+UUID.randomUUID().toString().replace("-","").substring(0,15));
            case CUSTOM->rule.custom;
            default->"";
        };
        a.removeValues();a.addValue(replacement);return true;
    }
    static IdentifierPolicy edit(Window owner,IdentifierPolicy current){
        JDialog d=new JDialog(owner,"Identifier handling",Dialog.ModalityType.APPLICATION_MODAL);
        DefaultTableModel model=new DefaultTableModel(new String[]{"Identifier category","Action","Custom non-identifying placeholder"},0){public boolean isCellEditable(int r,int c){return c>0;}};
        for(Category c:Category.values()){Rule r=current.rule(c);model.addRow(new Object[]{c.label,r.mode,r.custom});}
        JTable table=new JTable(model);table.setRowHeight(36);table.putClientProperty("terminateEditOnFocusLost",Boolean.TRUE);
        table.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(new JComboBox<>(Mode.values())));
        table.getColumnModel().getColumn(0).setPreferredWidth(320);table.getColumnModel().getColumn(1).setPreferredWidth(120);table.getColumnModel().getColumn(2).setPreferredWidth(310);
        JTextArea note=new JTextArea("Known fields are detected at every sequence depth in every file.\nREMOVE is the conservative default. DEFAULT uses REDACTED. RANDOM uses fresh consistent placeholders within this cleaning job. CUSTOM uses your non-identifying text.\n\nThese choices apply to eligible text fields only. Dates use the date policy. UIDs are remapped consistently to valid new UIDs. Numeric/coded patient characteristics (including smoking status, age and sex) are removed or emptied; arbitrary text cannot be inserted into these fields. Private and unknown attributes are removed.\n\nPatient Name and Patient ID are entered separately for each new import. The choices in this panel are remembered; original field values and random mappings are never saved.");
        note.setEditable(false);note.setLineWrap(true);note.setWrapStyleWord(true);note.setMargin(new Insets(14,14,14,14));
        IdentifierPolicy[] result={null};d.add(new JScrollPane(table));d.add(new JScrollPane(note),BorderLayout.NORTH);note.setRows(10);
        d.add(Theme.row(Theme.button("Save choices",true,()->{if(table.isEditing()&&!table.getCellEditor().stopCellEditing())return;try{Map<Category,Rule> rules=new EnumMap<>(Category.class);int i=0;for(Category c:Category.values()){rules.put(c,new Rule((Mode)model.getValueAt(i,1),String.valueOf(model.getValueAt(i,2))));i++;}result[0]=new IdentifierPolicy(rules);d.dispose();}catch(Exception e){Theme.error(d,e);}}),Theme.button("Cancel",false,d::dispose)),BorderLayout.SOUTH);
        d.setSize(980,690);d.setLocationRelativeTo(owner);d.setVisible(true);return result[0];
    }
}
