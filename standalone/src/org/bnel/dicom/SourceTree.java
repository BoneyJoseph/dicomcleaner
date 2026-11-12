package org.bnel.dicom;

import javax.swing.*;
import javax.swing.tree.*;
import javax.swing.event.TreeSelectionEvent;
import java.awt.event.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.function.IntUnaryOperator;

/** Shared source/subject/study/series hierarchy. Indices always refer to the caller's list. */
final class SourceTree extends JTree {
    static final int MARKED=1, REVIEWED=2, EXCLUDED=4;
    enum Kind { ROOT, SOURCE, SUBJECT, STUDY, SERIES, FILE }
    static final class Node extends DefaultMutableTreeNode {
        final Kind kind;
        final String title, location;
        final int file;
        int[] members=new int[0];
        int marked, reviewed, excluded;
        int burnedIn;   // files likely to carry burned-in text (metadata cues; see Cleaner.burnedInHint)
        String burnedInWhy="";
        Node(Kind kind,String title,String location,int file){super(title);this.kind=kind;this.title=title;this.location=location;this.file=file;}
        @Override public String toString(){
            String flags=(marked>0?" · "+marked+" marked":"")+(excluded>0?" · "+excluded+" excluded":"")+(reviewed>0?" · "+reviewed+" reviewed":"");
            if(kind==Kind.FILE)return (marked>0?"[MARKED] ":"")+(excluded>0?"[Excluded] ":reviewed>0?"[Reviewed] ":"")+title;
            return title+" ("+members.length+")"+flags;
        }
    }
    List<Cleaner.Instance> inputs=List.of();
    Node[] leaves=new Node[0];
    int[] order=new int[0];
    int current=-1;
    boolean filtering;
    String query="";
    BitSet operationSelection=new BitSet();
    java.util.List<TreePath> savedExpansion=java.util.List.of();
    FilterModel filteredModel;
    static final class FilterModel extends DefaultTreeModel {
        final Map<Node,List<Node>> visible=new IdentityHashMap<>();boolean active;
        FilterModel(Node root){super(root);}
        public Object getChild(Object parent,int i){return active?visible.getOrDefault((Node)parent,List.of()).get(i):super.getChild(parent,i);}
        public int getChildCount(Object parent){return active?visible.getOrDefault((Node)parent,List.of()).size():super.getChildCount(parent);}
        public int getIndexOfChild(Object parent,Object child){return active?visible.getOrDefault((Node)parent,List.of()).indexOf(child):super.getIndexOfChild(parent,child);}
        public boolean isLeaf(Object node){return ((Node)node).kind==Kind.FILE;}
        boolean include(Node node,String text,boolean ancestor){
            boolean matched=ancestor||node.title.toLowerCase(Locale.ROOT).contains(text)||node.location.toLowerCase(Locale.ROOT).contains(text);
            List<Node> children=new ArrayList<>();for(int i=0;i<node.getChildCount();i++){Node child=(Node)node.getChildAt(i);if(include(child,text,matched))children.add(child);}
            visible.put(node,children);return matched||!children.isEmpty();
        }
        boolean shown(TreePath path){if(!active)return true;Object[] parts=path.getPath();for(int i=1;i<parts.length;i++)if(getIndexOfChild(parts[i-1],parts[i])<0)return false;return true;}
    }

    SourceTree(){
        setName("Sources and series");setRootVisible(false);setShowsRootHandles(true);setRowHeight(Theme.u(26));setLargeModel(true);
        setScrollsOnExpand(false);setExpandsSelectedPaths(false);
        getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
        setBackground(Theme.NAVIGATION);setBorder(BorderFactory.createEmptyBorder(4,2,4,2));
        putClientProperty("FlatLaf.style","wideSelection: true; wideCellRenderer: true");
        setCellRenderer(new RowRenderer());
        addTreeSelectionListener(e->{}); // Keep semantic selection tracking active even before a caller installs listeners.
        ToolTipManager.sharedInstance().registerComponent(this);setInstances(List.of());
    }
    void setInstances(List<Cleaner.Instance> items){
        int previous=inputs.size();query="";operationSelection.clear();inputs=List.copyOf(items);leaves=new Node[items.size()];current=-1;
        Node root=new Node(Kind.ROOT,"Imported files","",-1);
        Map<Path,List<Integer>> sources=new LinkedHashMap<>();
        for(int i=0;i<items.size();i++)sources.computeIfAbsent(items.get(i).importRoot(),k->new ArrayList<>()).add(i);
        for(var source:sources.entrySet()){
            Path path=source.getKey();Node folder=new Node(Kind.SOURCE,path.getFileName()==null?path.toString():path.getFileName().toString(),path.toString(),-1);root.add(folder);
            for(List<Integer> subject:groups(source.getValue(),i->items.get(i).patientKey()).values()){
                Cleaner.Instance first=items.get(subject.get(0));String name=first.patientName().isBlank()?"Unnamed subject":first.patientName();
                Node patient=new Node(Kind.SUBJECT,name+(first.patientId().isBlank()?"":" · "+first.patientId()),path.toString(),-1);folder.add(patient);
                int number=0;
                for(List<Integer> study:groups(subject,i->items.get(i).study()).values()){
                    Node st=new Node(Kind.STUDY,"Study "+(++number),path.toString(),-1);patient.add(st);
                    for(List<Integer> series:groups(study,i->items.get(i).series()).values()){
                        series.sort(Comparator.comparingInt((Integer i)->items.get(i).instanceNumber()).thenComparing(i->items.get(i).source().toString()));
                        Cleaner.Instance f=items.get(series.get(0));
                        String label=f.description().isBlank()?"Series "+f.seriesNumber():f.description();
                        String folders=String.join(" | ",series.stream().map(i->items.get(i).source().getParent().toString()).distinct().toList());
                        Node se=new Node(Kind.SERIES,f.modality()+" · "+label,folders,-1);st.add(se);
                        for(int index:series){Cleaner.Instance instance=items.get(index);Node file=new Node(Kind.FILE,instance.toString(),instance.source().toString(),index);if(instance.likelyBurnedIn()){file.burnedIn=1;file.burnedInWhy=instance.burnedInHint();}leaves[index]=file;se.add(file);}
                    }
                }
            }
        }
        collect(root);sumBurnedIn(root);order=root.members.clone();filteredModel=new FilterModel(root);setModel(filteredModel);collapseToSeries();firePropertyChange("instanceCount",previous,items.size());
    }
    private static Map<String,List<Integer>> groups(List<Integer> indices,java.util.function.IntFunction<String> key){
        Map<String,List<Integer>> result=new LinkedHashMap<>();for(int i:indices)result.computeIfAbsent(key.apply(i),k->new ArrayList<>()).add(i);return result;
    }
    private static int sumBurnedIn(Node node){if(node.file>=0)return node.burnedIn;int n=0;for(int c=0;c<node.getChildCount();c++)n+=sumBurnedIn((Node)node.getChildAt(c));return node.burnedIn=n;}
    private static int[] collect(Node node){
        if(node.file>=0)return node.members=new int[]{node.file};
        java.util.stream.IntStream.Builder b=java.util.stream.IntStream.builder();
        for(int c=0;c<node.getChildCount();c++)for(int i:collect((Node)node.getChildAt(c)))b.add(i);
        return node.members=b.build().toArray();
    }
    void collapseToSeries(){
        for(int r=getRowCount()-1;r>=0;r--)collapseRow(r);
        for(int r=0;r<getRowCount();r++){Node n=(Node)getPathForRow(r).getLastPathComponent();if(n.kind!=Kind.SERIES&&n.kind!=Kind.FILE)expandRow(r);}
    }
    void expandSeries(){for(int r=0;r<getRowCount();r++)expandRow(r);}
    @Override protected void fireValueChanged(TreeSelectionEvent event){
        if(!filtering&&operationSelection!=null){operationSelection.clear();TreePath[] paths=getSelectionPaths();if(paths!=null)for(TreePath path:paths)for(int i:((Node)path.getLastPathComponent()).members)operationSelection.set(i);}
        super.fireValueChanged(event);
    }
    int[] getSelectedIndices(){return Arrays.stream(order).filter(operationSelection::get).toArray();}
    void setFilter(String text){
        text=text.strip().toLowerCase(Locale.ROOT);if(text.equals(query))return;
        if(query.isEmpty()){var expanded=getExpandedDescendants(new TreePath(filteredModel.getRoot()));savedExpansion=expanded==null?List.of():Collections.list(expanded);}
        BitSet chosen=(BitSet)operationSelection.clone();filtering=true;
        try{
            query=text;filteredModel.visible.clear();filteredModel.active=!text.isEmpty();
            if(filteredModel.active)filteredModel.include((Node)filteredModel.getRoot(),text,false);
            filteredModel.reload();
            if(query.isEmpty()){for(TreePath path:savedExpansion)expandPath(path);}else{for(int row=0;row<getRowCount();row++){TreePath path=getPathForRow(row);if(((Node)path.getLastPathComponent()).kind!=Kind.FILE)expandPath(path);}}
            List<TreePath> paths=new ArrayList<>();compress((Node)filteredModel.getRoot(),chosen,paths);
            setSelectionPaths(paths.stream().filter(filteredModel::shown).toArray(TreePath[]::new));
            operationSelection=chosen;
        }finally{filtering=false;}
    }
    @Override public boolean getScrollableTracksViewportWidth(){return true;}
    @Override protected void processMouseEvent(MouseEvent event){
        // Wide painting alone does not make BasicTreeUI's hit test span the row.
        // Delegate with a hit inside the same node, preserving Ctrl/Shift and native
        // expand/collapse behavior. The expander gutter is deliberately untouched.
        if(SwingUtilities.isLeftMouseButton(event)){
            TreePath path=getClosestPathForLocation(event.getX(),event.getY());Rectangle row=path==null?null:getPathBounds(path);
            if(row!=null&&event.getY()>=row.y&&event.getY()<row.y+row.height&&event.getX()>=row.x){
                MouseEvent hit=new MouseEvent(this,event.getID(),event.getWhen(),event.getModifiersEx(),row.x+Theme.u(8),event.getY(),event.getXOnScreen(),event.getYOnScreen(),event.getClickCount(),event.isPopupTrigger(),event.getButton());
                super.processMouseEvent(hit);if(hit.isConsumed())event.consume();return;
            }
        }
        super.processMouseEvent(event);
    }
    @Override public String getToolTipText(MouseEvent e){TreePath path=getClosestPathForLocation(e.getX(),e.getY());Rectangle r=path==null?null:getPathBounds(path);if(r==null||e.getY()<r.y||e.getY()>=r.y+r.height)return null;Node n=(Node)path.getLastPathComponent();return "<html>"+escape(n.toString())+"<br>"+escape(n.location)+(n.burnedIn==0?"":"<br><b>"+(n.kind==Kind.FILE?"May contain burned-in text: "+escape(n.burnedInWhy):n.burnedIn+" file(s) may contain burned-in text")+"</b><br>Check the image, then remove or black out if needed.")+"</html>";}
    private static String escape(String text){return text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
    final class RowRenderer extends JPanel implements TreeCellRenderer {
        final JLabel label=new JLabel(),badge=new JLabel();
        RowRenderer(){super(new BorderLayout(Theme.u(8),0));setOpaque(false);label.setOpaque(false);badge.setOpaque(false);add(label);add(badge,BorderLayout.EAST);setBorder(BorderFactory.createEmptyBorder(0,3,0,6));}
        public Component getTreeCellRendererComponent(JTree tree,Object value,boolean selected,boolean expanded,boolean leaf,int row,boolean focus){
            Node n=(Node)value;setFont(tree.getFont().deriveFont(Font.PLAIN));label.setFont(getFont());badge.setFont(getFont());label.putClientProperty("html.disable",true);
            label.setText(n.kind==Kind.FILE?n.toString():n.title+(n.marked>0?" · "+n.marked+" marked":"")+(n.excluded>0?" · "+n.excluded+" excluded":"")+(n.burnedIn>0?" · "+n.burnedIn+" may have burned-in text":""));
            label.setForeground(n.excluded>0?Theme.MUTED:n.marked>0?Theme.WARNING:Theme.INK);badge.setForeground(Theme.MUTED);
            label.setIcon(new UiIcons(n.marked>0?"flag":n.burnedIn>0&&n.excluded<n.members.length?"alert":n.kind==Kind.FILE?(n.reviewed>0?"check":"file"):"folder"));label.setIconTextGap(Theme.u(6));
            badge.setHorizontalAlignment(SwingConstants.RIGHT);badge.setPreferredSize(new Dimension(n.kind==Kind.FILE?0:Theme.u(44),Theme.u(26)));badge.setText(n.kind==Kind.FILE?"":String.valueOf(n.members.length));
            return this;
        }
    }
    int getSelectedIndex(){int[] indices=getSelectedIndices();return indices.length==0?-1:indices[0];}
    int getLeadSelectionIndex(){TreePath p=getSelectionModel().getLeadSelectionPath();if(p==null)return -1;int[] indices=((Node)p.getLastPathComponent()).members;return indices.length==0?-1:indices[0];}
    void setSelectedIndex(int index){if(index<0){clearSelection();return;}setSelectionPath(pathFor(index));}
    TreePath pathFor(int index){return new TreePath(leaves[index].getPath());}
    void setSelectedIndices(int[] indices){
        BitSet chosen=new BitSet(inputs.size());for(int i:indices)if(i>=0&&i<inputs.size())chosen.set(i);
        List<TreePath> paths=new ArrayList<>();compress((Node)getModel().getRoot(),chosen,paths);setSelectionPaths(paths.stream().filter(filteredModel::shown).toArray(TreePath[]::new));operationSelection=chosen;
    }
    private void compress(Node node,BitSet chosen,List<TreePath> paths){
        if(node.members.length==0)return;boolean all=true,any=false;
        for(int i:node.members){all&=chosen.get(i);any|=chosen.get(i);}if(!any)return;
        if(all&&node.kind!=Kind.ROOT){paths.add(new TreePath(node.getPath()));return;}
        for(int c=0;c<node.getChildCount();c++)compress((Node)node.getChildAt(c),chosen,paths);
    }
    void selectFileInterval(int first,int last){setSelectedIndices(java.util.stream.IntStream.rangeClosed(Math.min(first,last),Math.max(first,last)).toArray());}
    void ensureIndexIsVisible(int index){scrollPathToVisible(pathFor(index));}
    @Override public void scrollPathToVisible(TreePath path){
        if(path==null||!filteredModel.shown(path))return;
        int x=getVisibleRect().x;makeVisible(path);Rectangle row=getPathBounds(path);
        if(row!=null){row.x=x;row.width=1;scrollRectToVisible(row);}
    }
    String fileLabel(int index){return leaves[index].toString();}
    void setCurrent(int index){current=index;repaint();}
    void refreshStates(IntUnaryOperator state){
        for(Node file:leaves){int flags=state.applyAsInt(file.file);file.marked=(flags&MARKED)==0?0:1;file.reviewed=(flags&REVIEWED)==0?0:1;file.excluded=(flags&EXCLUDED)==0?0:1;}
        sumStates((Node)getModel().getRoot());
        // A display-only change: preserve expansion, current file and bulk selection.
        // treeDidChange invalidates row widths without firing a structure/selection event.
        notifyLabels((Node)getModel().getRoot());treeDidChange();repaint();
    }
    private void notifyLabels(Node node){
        if(node.getChildCount()==0)return;filteredModel.nodesChanged(node,java.util.stream.IntStream.range(0,filteredModel.getChildCount(node)).toArray());
        for(int c=0;c<node.getChildCount();c++)notifyLabels((Node)node.getChildAt(c));
    }
    private void sumStates(Node node){
        if(node.file>=0)return;node.marked=node.reviewed=node.excluded=0;
        for(int c=0;c<node.getChildCount();c++){Node child=(Node)node.getChildAt(c);sumStates(child);node.marked+=child.marked;node.reviewed+=child.reviewed;node.excluded+=child.excluded;}
    }
}
