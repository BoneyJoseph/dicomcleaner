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
    record Rule(Mode mode,String custom){Rule{Objects.requireNonNull(mode);custom=custom==null?"":custom.strip();if(mode==Mode.CUSTOM&&(!custom.matches("[A-Za-z0-9 _^.-]{1,16}")))throw new IllegalArgumentException("Custom placeholders need 1–16 plain letters/numbers or spaces, ^ . _ -.");}}
    private final Map<Category,Rule> rules;
    final Set<Integer> removedTags;
    private final Map<String,String> random=new HashMap<>();
    IdentifierPolicy(){this(Map.of(),Set.of());}
    IdentifierPolicy(Map<Category,Rule> rules){this(rules,Set.of());}
    IdentifierPolicy(Map<Category,Rule> rules,Set<Integer> tags){this.rules=Map.copyOf(rules);this.removedTags=Set.copyOf(tags);}
    boolean removes(int tag){return removedTags.contains(tag);}
    IdentifierPolicy withRemovedTag(int tag){validateTag(tag);Set<Integer> tags=new HashSet<>(removedTags);tags.add(tag);return new IdentifierPolicy(rules,tags);}
    static void validateTag(int tag){if(DicomIO.protectedTag(tag))throw new IllegalArgumentException("This tag is needed for image structure, geometry, UID references, or the replacement patient fields. Use the dedicated replacement fields for Patient Name and ID.");}
    static int parseTag(String value){String hex=value.replaceAll("(?i)0x", "").replaceAll("[(),\\s]", "");if(!hex.matches("[0-9a-fA-F]{8}"))throw new IllegalArgumentException("Enter a tag as 0018,1030 or (0018,1030).");int tag=(int)Long.parseLong(hex,16);validateTag(tag);return tag;}
    void removeTags(AttributeList list)throws DicomException{
        for(Attribute a:new ArrayList<Attribute>(list.values())){
            int k=Policy.key(a.getTag());
            if(removes(k)){
                if(Set.of(0x00100030,0x00100040,0x00080020,0x00080030,0x00080050,0x00080070,0x00080090,0x00200010).contains(k))a.removeValues();
                else list.remove(a.getTag());
            }else if(a instanceof SequenceAttribute seq)for(Iterator<?> it=seq.iterator();it.hasNext();)removeTags(((SequenceItem)it.next()).getAttributeList());
        }
    }
    Rule rule(Category c){return rules.getOrDefault(c,new Rule(Mode.REMOVE,""));}
    IdentifierPolicy fresh(){return new IdentifierPolicy(rules,removedTags);}
    static IdentifierPolicy load(AppSettings settings){
        Map<Category,Rule> rules=new EnumMap<>(Category.class);
        for(Category c:Category.values())try{rules.put(c,new Rule(Mode.valueOf(settings.text("identifier."+c.name()+".mode","REMOVE")),settings.text("identifier."+c.name()+".custom","")));}catch(Exception ignored){}
        Set<Integer> tags=new HashSet<>();for(String t:settings.text("identifier.removeTags","").split(";"))try{tags.add(parseTag(t));}catch(Exception ignored){}
        return new IdentifierPolicy(rules,tags);
    }
    void save(AppSettings settings){settings.set("identifier.removeTags",removedTags.stream().sorted().map(t->String.format("%08X",t)).collect(java.util.stream.Collectors.joining(";")));for(Category c:Category.values()){Rule r=rule(c);settings.set("identifier."+c.name()+".mode",r.mode);settings.set("identifier."+c.name()+".custom",r.custom);}}
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
        JDialog d=new JDialog(owner,"Identifier rules",Dialog.ModalityType.APPLICATION_MODAL);Theme.installWindow(d);
        DefaultTableModel model=new DefaultTableModel(new String[]{"Identifier category","Action","Custom replacement"},0){public boolean isCellEditable(int r,int c){return c>0;}};
        for(Category c:Category.values()){Rule r=current.rule(c);model.addRow(new Object[]{c.label,r.mode,r.custom});}
        JTable table=new JTable(model);table.setRowHeight(28);table.putClientProperty("terminateEditOnFocusLost",Boolean.TRUE);
        table.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(new JComboBox<>(Mode.values())));
        table.getColumnModel().getColumn(0).setPreferredWidth(350);table.getColumnModel().getColumn(1).setPreferredWidth(120);table.getColumnModel().getColumn(2).setPreferredWidth(310);
        Set<Integer> tags=new TreeSet<>(current.removedTags);
        DefaultTableModel tagModel=new DefaultTableModel(new String[]{"Always remove tag","Attribute"},0){public boolean isCellEditable(int r,int c){return false;}};
        JTable tagTable=new JTable(tagModel);tagTable.setRowHeight(28);
        Runnable refresh=()->{tagModel.setRowCount(0);for(int tag:tags)tagModel.addRow(new Object[]{String.format("(%04X,%04X)",tag>>>16,tag&65535),DicomDictionary.StandardDictionary.getNameFromTag(Policy.tag(tag))});};refresh.run();
        JTextField entry=new JTextField(13);entry.putClientProperty("JTextField.placeholderText","0018,1030");
        JPanel custom=new JPanel(new BorderLayout(8,10));custom.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));custom.add(Theme.scroll(tagTable));
        custom.add(Theme.row(new JLabel("Tag"),entry,Theme.button("Add removal rule",true,()->{try{tags.add(parseTag(entry.getText()));entry.setText("");refresh.run();}catch(Exception e){Theme.error(d,e);}}),Theme.button("Remove selected rule",false,()->{int[] rows=tagTable.getSelectedRows();for(int i=rows.length-1;i>=0;i--)tags.remove(parseTag(String.valueOf(tagModel.getValueAt(rows[i],0))));refresh.run();})),BorderLayout.SOUTH);
        JTabbedPane tabs=new JTabbedPane();tabs.addTab("Identifier categories",Theme.scroll(table));tabs.addTab("Custom tags",custom);d.add(tabs);
        JTextArea note=new JTextArea("Rules are remembered and apply at every sequence depth in every loaded file. DEFAULT uses REDACTED; RANDOM uses consistent placeholders within a cleaning job. Dates and UIDs use their dedicated handling.\nAdd a custom tag here or right-click it in the instance comparison. Custom removal overrides scan labels. Required empty fields remain empty to keep valid DICOM structure.");
        note.setEditable(false);note.setLineWrap(true);note.setWrapStyleWord(true);note.setRows(4);note.setMargin(new Insets(16,16,16,16));note.setBackground(Theme.PALE);d.add(note,BorderLayout.NORTH);
        IdentifierPolicy[] result={null};d.add(Theme.footer(d,Theme.row(Theme.button("Save rules",true,()->{if(table.isEditing()&&!table.getCellEditor().stopCellEditing())return;try{Map<Category,Rule> rules=new EnumMap<>(Category.class);int i=0;for(Category c:Category.values()){rules.put(c,new Rule((Mode)model.getValueAt(i,1),String.valueOf(model.getValueAt(i,2))));i++;}result[0]=new IdentifierPolicy(rules,tags);d.dispose();}catch(Exception e){Theme.error(d,e);}}),Theme.button("Cancel",false,d::dispose))),BorderLayout.SOUTH);
        d.setMinimumSize(new Dimension(760,500));d.setSize(1080,650);d.setLocationRelativeTo(owner);d.setVisible(true);return result[0];
    }
}
