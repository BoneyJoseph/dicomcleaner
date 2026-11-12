package org.bnel.dicom;

import com.pixelmed.dicom.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

/** Read-only rendering; PDFs, scripts, links, forms and attachments are never launched. */
final class ReportPreview implements AutoCloseable {
    final PDDocument document;final PDFRenderer renderer;
    int cachedIndex=-1;BufferedImage cachedPage;
    private ReportPreview(PDDocument document){this.document=document;this.renderer=new PDFRenderer(document);renderer.setSubsamplingAllowed(true);}
    static ReportPreview open(AttributeList list)throws Exception{
        Attribute embedded=list.get(Policy.tag(0x00420011));
        if(embedded==null||!Policy.value(list,0x00420012).equalsIgnoreCase("application/pdf"))return null;
        if(embedded.getVL()>64L*1024*1024)throw new IOException("This embedded report exceeds the 64 MB preview limit. It remains excluded from cleaning; inspect its metadata or remove the file.");
        PDDocument pdf=Loader.loadPDF(embedded.getByteValues());
        if(pdf.getNumberOfPages()==0){pdf.close();throw new IOException("The embedded report has no pages.");}
        return new ReportPreview(pdf);
    }
    int pages(){return document.getNumberOfPages();}
    BufferedImage page(int index)throws IOException{
        if(cachedIndex==index&&cachedPage!=null)return cachedPage;
        var box=document.getPage(index).getCropBox();float longest=Math.max(box.getWidth(),box.getHeight());
        if(!Float.isFinite(longest)||longest<=0)throw new IOException("Invalid report page size.");
        float scale=Math.min(1.7f,3000f/longest);cachedPage=renderer.renderImage(index,scale,ImageType.RGB);cachedIndex=index;return cachedPage;
    }
    static String text(AttributeList list){
        StringBuilder out=new StringBuilder();
        for(DicomIO.Row row:DicomIO.rows(list))if(!DicomIO.binary(row.vr())&&!row.vr().equals("SQ")&&!row.value().isBlank())
            out.append(row.path()).append("  ").append(row.name()).append("\n").append(row.value()).append("\n\n");
        return out.toString();
    }
    public void close()throws IOException{document.close();}
}
