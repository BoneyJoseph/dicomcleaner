package org.bnel.dicom;

import com.pixelmed.dicom.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Reject hidden textual/thumbnail metadata carried inside supported compressed frame streams. */
final class PixelSafety {
    static void inspect(DicomIO.Loaded loaded)throws Exception{
        TransferSyntax syntax=new TransferSyntax(loaded.syntax());
        if(!syntax.isEncapsulated())return;
        Attribute pixels=loaded.list().getPixelData();if(pixels==null)return;
        if(!(pixels instanceof ByteFrameSource source))throw new IOException("Compressed frames could not be inspected. Decompress a copy with a trusted DICOM tool and reimport.");
        int frames=Attribute.getSingleIntegerValueOrDefault(loaded.list(),TagFromName.NumberOfFrames,1);
        for(int i=0;i<frames;i++){
            byte[] bytes=source.getByteValuesForSelectedFrame(i);
            if(loaded.syntax().equals(TransferSyntax.RLE)){
                if(bytes.length<64)throw new IOException("Invalid RLE frame.");
                java.nio.ByteBuffer header=java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                int segments=header.getInt();if(segments<1||segments>15)throw new IOException("Invalid RLE header.");
                for(int k=0;k<15;k++){int offset=header.getInt();if(k>=segments&&offset!=0)throw new IOException("Nonstandard data in an RLE header. Decompress a trusted copy before cleaning.");}
            }else inspectJpeg(bytes);
        }
    }
    static void inspectJpeg(byte[] bytes)throws IOException{
        if(bytes.length<4||(bytes[0]&255)!=255||(bytes[1]&255)!=216)
            throw new IOException("This compressed stream cannot be checked for hidden metadata by this release. Use a trusted DICOM decompressor and reimport. The source is unchanged.");
        int p=2;boolean entropy=false,ended=false;
        while(p<bytes.length){
            if((bytes[p++]&255)!=255){if(entropy)continue;throw new IOException("Unexpected data in compressed image header.");}
            while(p<bytes.length&&(bytes[p]&255)==255)p++;
            if(p>=bytes.length)break;int marker=bytes[p++]&255;
            if(entropy&&(marker<0x80||marker>=0xd0&&marker<=0xd7))continue;
            if(marker==0xd9){ended=true;break;}
            if(marker==0xd8||marker==0x01)continue;
            if(p+2>bytes.length)throw new IOException("Truncated compressed header.");
            int length=((bytes[p]&255)<<8)|(bytes[p+1]&255);if(length<2||p+length>bytes.length)throw new IOException("Invalid compressed header length.");
            int start=p+2;
            if(marker>=0xe0&&marker<=0xef||marker==0xfe){
                boolean jfif=marker==0xe0&&length==16&&starts(bytes,start,"JFIF\u0000")&&bytes[start+12]==0&&bytes[start+13]==0;
                boolean adobe=marker==0xee&&length==14&&starts(bytes,start,"Adobe");
                if(!jfif&&!adobe)throw new IOException("Hidden metadata, a comment, or a thumbnail is present inside compressed image data. Decompress a copy with a trusted DICOM tool and reimport; export is blocked.");
            }
            p+=length;if(marker==0xda)entropy=true;
        }
        if(!ended)throw new IOException("Compressed image has no valid end marker.");
        while(p<bytes.length)if(bytes[p++]!=0)throw new IOException("Trailing data in compressed image. Decompress a trusted copy before cleaning.");
    }
    private static boolean starts(byte[] bytes,int start,String value){byte[] prefix=value.getBytes(StandardCharsets.ISO_8859_1);return start+prefix.length<=bytes.length&&Arrays.equals(bytes,start,start+prefix.length,prefix,0,prefix.length);}
}
