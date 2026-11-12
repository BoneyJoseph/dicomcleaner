package org.bnel.dicom;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;

/** Small, scalable outline command icons; no platform-dependent glyph fonts. */
final class UiIcons implements Icon {
    final String kind;
    UiIcons(String kind){this.kind=kind;}
    public int getIconWidth(){return Theme.u(16);}
    public int getIconHeight(){return Theme.u(16);}
    public void paintIcon(Component c,Graphics graphics,int x,int y){
        Graphics2D g=(Graphics2D)graphics.create();g.translate(x,y);g.scale(getIconWidth()/16d,getIconHeight()/16d);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(1.25f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        g.setColor(c.isEnabled()?c.getForeground():Theme.MUTED);
        switch(kind){
            case "rectangle" -> g.draw(new RoundRectangle2D.Double(2,3,12,10,2,2));
            case "folder" -> path(g,1.5,4,6,4,7.5,5.5,14.5,5.5,13.5,13,2.5,13,1.5,4,1.5,2.5,6,2.5,7.5,4,13,4);
            case "file" -> {path(g,3,1,10,1,13,4,13,15,3,15,3,1);path(g,10,1,10,4,13,4);path(g,5,8,11,8);path(g,5,11,10,11);}
            case "add" -> {g.draw(new RoundRectangle2D.Double(2,2,9,12,2,2));path(g,9,10,15,10);path(g,12,7,12,13);}
            case "clean" -> {path(g,3,13,11.5,4.5,13.5,6.5,5,15,3,13);path(g,10,1,10,3);path(g,13,2,14,1);path(g,14,4,16,4);path(g,3,2,3,6);path(g,1,4,5,4);}
            case "eye" -> {g.draw(new Ellipse2D.Double(5.5,5.5,5,5));g.draw(new QuadCurve2D.Double(1,8,8,-1,15,8));g.draw(new QuadCurve2D.Double(1,8,8,17,15,8));}
            case "export" -> {path(g,2,8,2,14,14,14,14,8);path(g,8,11,8,1);path(g,4.5,4.5,8,1,11.5,4.5);}
            case "check" -> path(g,2,8,6,12,14,3);
            case "flag" -> path(g,3,15,3,2,8,2,10,4,14,4,14,10,9,10,7,8,3,8);
            case "unflag" -> {path(g,3,15,3,2,8,2,10,4,14,4,14,10,9,10);path(g,1,1,15,15);}
            case "undo" -> {path(g,5,2,1,6,5,10);g.draw(new QuadCurve2D.Double(1,6,15,3,14,14));}
            case "play" -> {Path2D p=new Path2D.Double();p.moveTo(4,2);p.lineTo(14,8);p.lineTo(4,14);p.closePath();g.fill(p);}
            case "pause" -> {g.fillRoundRect(3,2,3,12,1,1);g.fillRoundRect(10,2,3,12,1,1);}
            case "previous" -> path(g,10,3,5,8,10,13);
            case "next" -> path(g,6,3,11,8,6,13);
            case "copy" -> {g.draw(new RoundRectangle2D.Double(5,5,9,9,1,1));path(g,10,3,10,1,1,1,1,10,3,10);}
            case "remove" -> {g.draw(new Ellipse2D.Double(1.5,1.5,13,13));path(g,4,8,12,8);}
            case "link" -> {g.rotate(-Math.PI/4,8,8);g.draw(new RoundRectangle2D.Double(5,0.5,6,9,6,6));g.draw(new RoundRectangle2D.Double(5,6.5,6,9,6,6));}
            case "search" -> {g.draw(new Ellipse2D.Double(1.5,1.5,9,9));path(g,9.5,9.5,14.5,14.5);}
            default -> {}
        }
        g.dispose();
    }
    static void path(Graphics2D g,double... points){Path2D p=new Path2D.Double();p.moveTo(points[0],points[1]);for(int i=2;i<points.length;i+=2)p.lineTo(points[i],points[i+1]);g.draw(p);}
    static Icon forAction(String text){
        String type=switch(text){
            case "Import folder" -> "folder";case "Add files" -> "add";case "Clean selected" -> "clean";
            case "Review images" -> "eye";case "Export cleaned" -> "export";case "Remove selected" -> "remove";
            case "Mark all selected" -> "flag";case "Unmark selected" -> "unflag";case "Approve selected" -> "check";
            case "Remove approval of selected", "Remove all approvals", "Undo exclusion" -> "undo";
            case "Collapse" -> "previous";case "Expand" -> "next";case "Previous image" -> "previous";case "Next image" -> "next";case "Play" -> "play";case "Pause" -> "pause";
            case "Copy details", "Copy displayed value" -> "copy";default -> null;
        };
        return type==null?null:new UiIcons(type);
    }
}
