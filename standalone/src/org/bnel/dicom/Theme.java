package org.bnel.dicom;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;

final class Theme {
    static final Color INK=new Color(31,42,69), MUTED=new Color(79,94,116), BLUE=new Color(40,98,213),
        PURPLE=new Color(71,92,179), BORDER=new Color(195,205,218), PALE=new Color(233,238,244);
    static void install() {
        Font font=new Font("Segoe UI",Font.PLAIN,14);
        for (Object k:UIManager.getDefaults().keySet().toArray()) if(k.toString().endsWith(".font")) UIManager.put(k,font);
        UIManager.put("Label.foreground",INK);UIManager.put("TextField.foreground",INK);
        UIManager.put("Table.selectionBackground",new Color(222,233,255));UIManager.put("Table.selectionForeground",INK);
        UIManager.put("Tree.selectionBackground",new Color(222,233,255));UIManager.put("Tree.selectionForeground",INK);
        UIManager.put("Table.gridColor",BORDER);UIManager.put("TableHeader.background",new Color(233,239,249));
        UIManager.put("ScrollBar.width",12);
        UIManager.put("Panel.background",PALE);UIManager.put("TextField.background",new Color(246,249,252));
        UIManager.put("TabbedPane.background",PALE);UIManager.put("TabbedPane.selected",new Color(249,251,254));
        UIManager.put("CheckBox.foreground",INK);UIManager.put("CheckBox.background",PALE);
    }
    static class Background extends JPanel {
        Background(LayoutManager layout){super(layout);}
        @Override protected void paintComponent(Graphics g){
            Graphics2D q=(Graphics2D)g.create();
            q.setPaint(new GradientPaint(0,0,new Color(229,236,242),getWidth(),getHeight(),new Color(237,239,245)));
            q.fillRect(0,0,getWidth(),getHeight());q.dispose();
        }
    }
    static JPanel card(String title,JComponent content){
        JPanel p=new JPanel(new BorderLayout(0,10)){
            protected void paintComponent(Graphics g){Graphics2D q=(Graphics2D)g.create();q.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                for(int i=7;i>=1;i--){q.setColor(new Color(110,130,153,4*(8-i)));q.fillRoundRect(7+i,7+i,getWidth()-21,getHeight()-21,22,22);}
                q.setColor(new Color(255,255,255,180));q.fillRoundRect(3,3,getWidth()-16,getHeight()-16,22,22);
                q.setPaint(new GradientPaint(0,0,new Color(240,244,248),getWidth(),getHeight(),new Color(228,235,242)));q.fillRoundRect(7,7,getWidth()-21,getHeight()-21,20,20);q.dispose();}
        };p.setOpaque(false);
        p.setBorder(new EmptyBorder(20,22,22,22));
        if(title!=null&&!title.isEmpty()){JLabel l=new JLabel(title);l.setFont(l.getFont().deriveFont(Font.BOLD,17f));p.add(l,BorderLayout.NORTH);}
        p.add(content,BorderLayout.CENTER);return p;
    }
    static JLabel label(String text,int size,boolean bold){JLabel l=new JLabel(text);l.setFont(l.getFont().deriveFont(bold?Font.BOLD:Font.PLAIN,(float)size));return l;}
    static JPanel row(Component... cs){JPanel p=new JPanel(new WrapLayout());p.setOpaque(false);for(Component c:cs)p.add(c);return p;}
    static JScrollPane scroll(Component component){
        JScrollPane pane=new JScrollPane(component);pane.setWheelScrollingEnabled(false);pane.getVerticalScrollBar().setUnitIncrement(20);pane.setBorder(BorderFactory.createLineBorder(BORDER));
        final double[] remainder={0};pane.addMouseWheelListener(e->{
            JScrollBar bar=e.isShiftDown()?pane.getHorizontalScrollBar():pane.getVerticalScrollBar();
            remainder[0]+=e.getPreciseWheelRotation()*48;int delta=(int)remainder[0];remainder[0]-=delta;bar.setValue(bar.getValue()+delta);e.consume();
        });return pane;
    }
    static JButton button(String text,boolean primary,Runnable action){
        JButton b=new JButton(text){
            @Override protected void paintComponent(Graphics g){
                Graphics2D q=(Graphics2D)g.create();q.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                Color a=isEnabled()?(primary?BLUE:PALE):new Color(235,239,246),z=isEnabled()?(primary?PURPLE:PALE):a;
                if(getModel().isPressed()){a=a.darker();z=z.darker();}
                else if(getModel().isRollover()){a=primary?a.brighter():new Color(226,235,255);}
                q.setColor(getModel().isPressed()?new Color(170,186,206):new Color(255,255,255,220));q.drawRoundRect(1,1,getWidth()-5,getHeight()-5,12,12);
                q.setPaint(new GradientPaint(0,0,a,getWidth(),getHeight(),z));q.fillRoundRect(3,3,getWidth()-7,getHeight()-7,12,12);
                q.setColor(primary?new Color(79,112,194):BORDER);q.drawRoundRect(3,3,getWidth()-7,getHeight()-7,12,12);
                if(hasFocus()){q.setColor(BLUE);q.drawRoundRect(3,3,getWidth()-7,getHeight()-7,9,9);}
                q.dispose();super.paintComponent(g);
            }
        };
        b.setName(text);b.setContentAreaFilled(false);b.setBorderPainted(false);b.setOpaque(false);b.setRolloverEnabled(true);
        b.setForeground(primary?Color.WHITE:BLUE);b.setBorder(new EmptyBorder(10,16,10,16));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));b.addActionListener(e->action.run());return b;
    }
    static void error(Component owner,Throwable error){
        Throwable e=error;
        while((e instanceof java.util.concurrent.ExecutionException || e instanceof java.lang.reflect.InvocationTargetException)&&e.getCause()!=null)e=e.getCause();
        String msg=(e instanceof IllegalArgumentException || e instanceof java.io.IOException)?e.getMessage():"The operation could not finish ("+e.getClass().getSimpleName()+"). No source files were changed.";
        JOptionPane.showMessageDialog(owner,msg,"BNEL Dicom Cleaner",JOptionPane.ERROR_MESSAGE);
    }
}
