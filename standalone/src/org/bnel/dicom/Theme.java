package org.bnel.dicom;

import com.formdev.flatlaf.FlatLightLaf;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;

final class Theme {
    // Opaque neutral surfaces keep text contrast stable at every size and DPI.
    static final Color INK=new Color(0x202420), MUTED=new Color(0x596159), ACCENT=new Color(0x5C7564), BLUE=ACCENT,
        PURPLE=ACCENT, BORDER=new Color(0xE3E4E1), PALE=new Color(0xF4F4F3), SURFACE=Color.WHITE,
        NAVIGATION=new Color(0xF7F7F6), SELECTION=new Color(0xE1E8E1), WARNING=new Color(0x805D24);
    static Font bodyFont,headingFont;
    static final class SilentLightLaf extends FlatLightLaf {
        @Override public void provideErrorFeedback(Component component) { /* Deliberately silent. */ }
    }
    static void install() {
        // Native decorated frames supply Windows caption buttons, snapping and double-click maximize.
        JFrame.setDefaultLookAndFeelDecorated(false);JDialog.setDefaultLookAndFeelDecorated(false);
        com.formdev.flatlaf.FlatLaf.setGlobalExtraDefaults(java.util.Map.of("@accentColor","#5c7564"));
        try{UIManager.setLookAndFeel(new SilentLightLaf());}catch(UnsupportedLookAndFeelException e){throw new IllegalStateException(e);}
        java.util.Set<String> fonts=java.util.Set.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        // Java does not apply WinUI's optical-size axis. Use the installed static Segoe UI face
        // and the platform's normal text rasterization instead of synthesizing variable-font weights.
        String font=fonts.contains("Segoe UI")?"Segoe UI":Font.SANS_SERIF;
        bodyFont=new Font(font,Font.PLAIN,13);
        headingFont=fonts.contains("Segoe UI Semibold")?new Font("Segoe UI Semibold",Font.PLAIN,14):bodyFont.deriveFont(Font.BOLD);
        UIManager.put("defaultFont",bodyFont);
        bodyFont=bodyFont.deriveFont((float)u(13));headingFont=headingFont.deriveFont((float)u(14));
        UIManager.put("defaultFont",bodyFont);
        UIManager.put("AuditoryCues.playList",new Object[0]);
        for(String key:new String[]{"errorSound","informationSound","questionSound","warningSound"})UIManager.put("OptionPane."+key,null);
        UIManager.put("Component.arc",12);UIManager.put("Button.arc",12);UIManager.put("TextComponent.arc",12);
        UIManager.put("Component.focusWidth",2);UIManager.put("Component.borderColor",new Color(0xC8CCC7));UIManager.put("Component.focusColor",ACCENT);
        UIManager.put("Component.hideMnemonics",true);UIManager.put("Button.margin",new Insets(5,12,5,12));
        UIManager.put("Component.minimumHeight",32);UIManager.put("Button.minimumHeight",32);
        UIManager.put("Button.background",SURFACE);UIManager.put("Button.foreground",INK);
        UIManager.put("Button.hoverBackground",new Color(0xF1F2F0));UIManager.put("Button.pressedBackground",new Color(0xE5E7E3));
        UIManager.put("Button.disabledText",new Color(0x8A9089));UIManager.put("Button.disabledBackground",new Color(0xEFEFED));
        UIManager.put("ToggleButton.margin",new Insets(5,10,5,10));UIManager.put("ToggleButton.selectedBackground",SELECTION);
        UIManager.put("ToggleButton.selectedForeground",ACCENT);UIManager.put("ToggleButton.arc",12);
        for(String c:new String[]{"Label","TextField","TextArea","ComboBox","Spinner","CheckBox","RadioButton"})UIManager.put(c+".foreground",INK);
        for(String c:new String[]{"TextField","TextArea","ComboBox","Spinner","Viewport"})UIManager.put(c+".background",SURFACE);
        UIManager.put("TextComponent.margin",new Insets(5,8,5,8));UIManager.put("ComboBox.padding",new Insets(4,8,4,8));
        UIManager.put("Panel.background",PALE);UIManager.put("CheckBox.background",PALE);
        for(String control:new String[]{"Table","List","Tree"}){
            UIManager.put(control+".background",SURFACE);UIManager.put(control+".foreground",INK);
            UIManager.put(control+".selectionBackground",SELECTION);UIManager.put(control+".selectionForeground",INK);
            UIManager.put(control+".selectionInactiveBackground",new Color(0xE1E8E1));
            UIManager.put(control+".selectionInactiveForeground",INK);
        }
        UIManager.put("Tree.rowHeight",26);UIManager.put("Tree.selectionArc",8);UIManager.put("Tree.selectionInsets",new Insets(1,2,1,2));
        UIManager.put("Tree.wideSelection",true);UIManager.put("Tree.wideCellRenderer",true);UIManager.put("Tree.leftChildIndent",6);UIManager.put("Tree.rightChildIndent",10);
        UIManager.put("Tree.icon.expandedColor",MUTED);UIManager.put("Tree.icon.collapsedColor",MUTED);
        UIManager.put("Table.showHorizontalLines",true);UIManager.put("Table.showVerticalLines",false);
        UIManager.put("Table.alternateRowColor",new Color(0xFAFAF9));UIManager.put("TableHeader.background",new Color(0xEFEFED));UIManager.put("TableHeader.height",30);
        UIManager.put("Table.gridColor",BORDER);UIManager.put("TableHeader.foreground",INK);UIManager.put("TableHeader.separatorColor",BORDER);UIManager.put("Table.cellMargins",new Insets(0,8,0,8));
        UIManager.put("ScrollBar.width",12);UIManager.put("ScrollBar.thumbArc",999);UIManager.put("ScrollBar.showButtons",false);
        UIManager.put("ScrollBar.track",new Color(0xF1F2F0));UIManager.put("ScrollBar.thumb",new Color(0x969D94));UIManager.put("ScrollBar.hoverThumbColor",new Color(0x697267));
        UIManager.put("ScrollBar.thumbInsets",new Insets(2,2,2,2));
        UIManager.put("ScrollPane.smoothScrolling",true);UIManager.put("SplitPane.dividerSize",12);UIManager.put("SplitPane.background",PALE);UIManager.put("SplitPaneDivider.style","plain");
        UIManager.put("TabbedPane.background",PALE);UIManager.put("TabbedPane.selectedBackground",SURFACE);
        UIManager.put("TabbedPane.foreground",MUTED);UIManager.put("TabbedPane.selectedForeground",INK);
        UIManager.put("TabbedPane.tabType","underlined");UIManager.put("TabbedPane.cardTabArc",8);UIManager.put("TabbedPane.cardTabSelectionHeight",3);
        UIManager.put("TabbedPane.hoverColor",new Color(0xEAEAEA));UIManager.put("TabbedPane.focusColor",SELECTION);
        UIManager.put("TabbedPane.underlineColor",BLUE);UIManager.put("TabbedPane.inactiveUnderlineColor",BLUE);UIManager.put("TabbedPane.tabSelectionHeight",3);UIManager.put("TabbedPane.showTabSeparators",false);
        UIManager.put("TabbedPane.tabInsets",new Insets(6,16,6,16));UIManager.put("TabbedPane.tabHeight",36);UIManager.put("TabbedPane.contentAreaColor",BORDER);
        UIManager.put("ProgressBar.foreground",BLUE);UIManager.put("ProgressBar.background",new Color(0xE0E0E0));UIManager.put("ProgressBar.arc",8);
        UIManager.put("Slider.trackColor",new Color(0xC8CCC7));UIManager.put("Slider.thumbColor",BLUE);UIManager.put("Slider.trackValueColor",BLUE);
        UIManager.put("ToolTip.background",Color.WHITE);UIManager.put("ToolTip.foreground",INK);
        UIManager.put("CheckBox.icon.borderColor",new Color(0x777777));UIManager.put("CheckBox.icon.checkmarkColor",Color.WHITE);
        UIManager.put("CheckBox.icon.selectedBackground",ACCENT);UIManager.put("CheckBox.icon.selectedBorderColor",ACCENT);
        UIManager.put("CheckBox.icon.focusedSelectedBackground",ACCENT);UIManager.put("CheckBox.icon.hoverSelectedBackground",new Color(0x526A5A));
        ToolTipManager.sharedInstance().setInitialDelay(450);ToolTipManager.sharedInstance().setDismissDelay(25000);
    }
    static void installWindow(Window window){
        try{window.setIconImage(javax.imageio.ImageIO.read(Theme.class.getResource("/app-icon.png")));}catch(Exception ignored){}
    }
    static class Background extends JPanel {
        Background(LayoutManager layout){super(layout);setBackground(PALE);setOpaque(true);}
    }
    static JPanel card(String title,JComponent content){return panel(title,content,SURFACE);}
    static JPanel navigation(String title,JComponent content){return panel(title,content,NAVIGATION);}
    private static JPanel panel(String title,JComponent content,Color color){
        JPanel p=new JPanel(new BorderLayout(0,u(12))){
            protected void paintComponent(Graphics g){super.paintComponent(g);Graphics2D q=(Graphics2D)g.create();q.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                q.setColor(color);q.fillRoundRect(0,0,getWidth()-1,getHeight()-1,16,16);
                q.setColor(BORDER);q.drawRoundRect(0,0,getWidth()-1,getHeight()-1,16,16);q.dispose();}
        };p.setOpaque(false);p.setBackground(color);p.setBorder(new EmptyBorder(u(12),u(12),u(12),u(12)));
        if(title!=null&&!title.isEmpty())p.add(label(title,14,true),BorderLayout.NORTH);p.add(content);return p;
    }
    static void search(JTextField field){field.putClientProperty("JComponent.roundRect",null);field.putClientProperty("JTextField.leadingIcon",new UiIcons("search"));}
    static JLabel label(String text,int size,boolean strong){JLabel l=new JLabel(text);l.setFont((strong?headingFont:bodyFont).deriveFont((float)u(Math.max(11,size))));return l;}
    static JPanel row(Component... cs){return alignedRow(FlowLayout.LEFT,cs);}
    static JPanel centered(Component... cs){return alignedRow(FlowLayout.CENTER,cs);}
    static JPanel alignedRow(int alignment,Component... cs){JPanel p=new JPanel(new WrapLayout(alignment));p.setOpaque(false);for(Component c:cs)p.add(c);return p;}
    /** Two command groups share a baseline when they fit, otherwise use two full rows. */
    static JPanel paired(JComponent left,JComponent right){
        JPanel p=new JPanel(new LayoutManager(){
            public void addLayoutComponent(String n,Component c){}public void removeLayoutComponent(Component c){}
            public Dimension minimumLayoutSize(Container c){return new Dimension(100,preferredLayoutSize(c).height);}
            public Dimension preferredLayoutSize(Container c){int w=c.getWidth();if(w<=0)w=1600;Dimension a=left.isVisible()?left.getPreferredSize():new Dimension(),b=right.isVisible()?right.getPreferredSize():new Dimension();return new Dimension(a.width+b.width+16,a.width+b.width+16<=w?Math.max(a.height,b.height):a.height+b.height+4);}
            public void layoutContainer(Container c){int w=c.getWidth();Dimension a=left.isVisible()?left.getPreferredSize():new Dimension(),b=right.isVisible()?right.getPreferredSize():new Dimension();
                if(a.width+b.width+16<=w){int h=Math.max(a.height,b.height);left.setBounds(0,(h-a.height)/2,a.width,a.height);right.setBounds(w-b.width,(h-b.height)/2,b.width,b.height);}
                else{left.setBounds(0,0,w,a.height);right.setBounds(0,a.height+4,w,b.height);}}
        });p.setOpaque(false);p.add(left);p.add(right);return p;
    }
    static JScrollPane scroll(Component component){
        JScrollPane p=new JScrollPane(component);p.getVerticalScrollBar().setUnitIncrement(24);p.getHorizontalScrollBar().setUnitIncrement(24);p.setBorder(BorderFactory.createEmptyBorder());
        Color surface=component.getBackground();p.setBackground(surface);p.getViewport().setBackground(surface);p.setOpaque(true);p.getViewport().setOpaque(true);
        JPanel corner=new JPanel();corner.setBackground(surface);p.setCorner(JScrollPane.LOWER_RIGHT_CORNER,corner);
        if(component instanceof JTable table&&table.getAutoResizeMode()==JTable.AUTO_RESIZE_OFF){
            int last=table.getClientProperty("bnel.flexColumn") instanceof Integer i?i:table.getColumnCount()-1,min=table.getClientProperty("bnel.flexMin") instanceof Integer m?m:table.getColumnModel().getColumn(last).getPreferredWidth();
            p.getViewport().addComponentListener(new ComponentAdapter(){public void componentResized(ComponentEvent e){
                int used=0;for(int n=0;n<table.getColumnCount();n++)if(n!=last)used+=table.getColumnModel().getColumn(n).getWidth();
                int width=Math.max(min,p.getViewport().getWidth()-used);table.getColumnModel().getColumn(last).setPreferredWidth(width);table.getColumnModel().getColumn(last).setWidth(width);
                SwingUtilities.invokeLater(()->{int selected=table.getSelectedRow();if(selected>=0){Rectangle row=table.getCellRect(selected,0,true);row.x=p.getViewport().getViewPosition().x;row.width=1;table.scrollRectToVisible(row);}});
            }});
        }return p;
    }
    static void reflow(JComponent root){root.revalidate();root.repaint();}
    static JButton button(String text,boolean primary,Runnable action){
        JButton b=new JButton(text,UiIcons.forAction(text));b.setIconTextGap(u(6));b.setName(text);b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        if(primary){b.setBackground(BLUE);b.setForeground(Color.WHITE);b.putClientProperty("FlatLaf.style","hoverBackground: #526a5a; pressedBackground: #465d4f; borderColor: #5c7564; disabledBackground: #ededed; disabledText: #8a9089");}
        b.getAccessibleContext().setAccessibleName(text);b.setToolTipText(ActionHelp.forButton(text));b.addActionListener(e->action.run());return b;
    }
    static JPanel footer(Window window,JComponent content){JPanel p=new JPanel(new BorderLayout());p.setOpaque(false);p.add(content);p.setBorder(new EmptyBorder(8,0,4,0));return p;}
    static int u(int value){return com.formdev.flatlaf.util.UIScale.scale(value);}
    static JPanel line(Component... components){
        JPanel p=new JPanel();p.setOpaque(false);p.setLayout(new BoxLayout(p,BoxLayout.X_AXIS));
        for(int i=0;i<components.length;i++){if(i>0)p.add(Box.createHorizontalStrut(u(8)));p.add(components[i]);}return p;
    }
    static JPanel bar(JComponent left,JComponent right){
        JPanel p=new JPanel(new BorderLayout(u(12),0));p.setOpaque(false);p.add(left,BorderLayout.WEST);p.add(right,BorderLayout.EAST);return p;
    }
    static JPanel heading(String title,JLabel detail){
        JPanel p=new JPanel(new BorderLayout(u(8),0));p.setOpaque(false);p.add(label(title,14,true));detail.setFont(bodyFont.deriveFont((float)u(12)));detail.setForeground(MUTED);detail.setHorizontalAlignment(SwingConstants.RIGHT);p.add(detail,BorderLayout.EAST);return p;
    }
    static void equalButtons(int height,JButton... buttons){
        int width=0;for(JButton b:buttons)width=Math.max(width,b.getPreferredSize().width);
        for(JButton b:buttons){Dimension d=new Dimension(width,u(height));b.setPreferredSize(d);b.setMinimumSize(d);b.setMaximumSize(d);}
    }
    static void valueRenderer(JTable table,boolean tagPaths){
        table.setDefaultRenderer(Object.class,new javax.swing.table.DefaultTableCellRenderer(){
            public Component getTableCellRendererComponent(JTable t,Object v,boolean selected,boolean focused,int r,int c){
                super.getTableCellRendererComponent(t,v,selected,focused,r,c);setBorder(new EmptyBorder(0,u(6),0,u(6)));setToolTipText(v==null?null:v.toString());setFont(t.getFont());
                if(tagPaths&&t.convertColumnIndexToModel(c)==0&&v!=null){String path=v.toString();int last=path.lastIndexOf("(0x");String leaf=last>=0?path.substring(last).replace("0x",""):path;setText(leaf);setFont(new Font(Font.MONOSPACED,Font.PLAIN,t.getFont().getSize()));}
                if(!tagPaths&&t.convertColumnIndexToModel(c)==0&&v!=null){String path=v.toString().replace('\\','/');int slash=path.lastIndexOf('/');String file=slash>=0?path.substring(slash+1):path;String folder=slash>0?path.substring(0,slash):"";if(file.length()>24)file=file.substring(0,8)+"…"+file.substring(file.length()-13);setText(file+(folder.isBlank()?"":" · "+folder.substring(folder.lastIndexOf('/')+1)));}
                return this;
            }
        });
    }
    static void error(Component owner,Throwable error){Throwable e=error;while((e instanceof java.util.concurrent.ExecutionException||e instanceof java.lang.reflect.InvocationTargetException)&&e.getCause()!=null)e=e.getCause();
        String msg=(e instanceof IllegalArgumentException||e instanceof java.io.IOException)?e.getMessage():"The operation could not finish ("+e.getClass().getSimpleName()+").";
        if(e instanceof NoClassDefFoundError)msg="A bundled Java component is missing: "+String.valueOf(e.getMessage()).replace('/', '.')+".\nExtract the complete application ZIP into a new folder and try again.";
        // PLAIN_MESSAGE plus a silent LAF avoids native notification/error sounds.
        JOptionPane.showMessageDialog(owner,msg,"BNEL Dicom Cleaner",JOptionPane.PLAIN_MESSAGE);
    }
}
