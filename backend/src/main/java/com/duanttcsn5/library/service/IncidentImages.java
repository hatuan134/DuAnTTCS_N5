package com.duanttcsn5.library.service;

import com.duanttcsn5.library.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Validate actual file bytes, never trust a browser MIME type alone. */
public final class IncidentImages {
    public static final int MAX_BYTES=5*1024*1024;
    private IncidentImages() {}
    public record Upload(String filename,String contentType,byte[] data) {}
    public static List<Upload> validate(List<MultipartFile> files) {
        if(files==null) return List.of();
        if(files.size()>5) throw invalid("Chỉ được đính kèm tối đa 5 ảnh.");
        var result=new ArrayList<Upload>();
        for(var file:files) {
            if(file==null || file.isEmpty() || file.getSize()>MAX_BYTES) throw invalid("Mỗi ảnh phải có dữ liệu và không vượt 5 MB.");
            String name=file.getOriginalFilename()==null?"anh":file.getOriginalFilename().replace('\\','/');
            name=name.substring(name.lastIndexOf('/')+1).replaceAll("[\\p{Cntrl}]", "");
            if(name.length()>200 || name.isBlank()) throw invalid("Tên ảnh không hợp lệ hoặc vượt 200 ký tự.");
            String lower=name.toLowerCase(Locale.ROOT);
            try(var input=file.getInputStream()) {
                byte[] bytes=input.readNBytes(MAX_BYTES+1);
                if(bytes.length==0 || bytes.length>MAX_BYTES) throw invalid("Mỗi ảnh không vượt 5 MB.");
                String type;
                if(lower.endsWith(".webp")) { validateWebp(bytes); type="image/webp"; }
                else {
                    try(var stream=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                        var readers=ImageIO.getImageReaders(stream);
                        if(!readers.hasNext()) throw invalid("Ảnh phải là JPEG, PNG hoặc WebP hợp lệ.");
                        var reader=readers.next();
                        try {
                            reader.setInput(stream,true,true);
                            String format=reader.getFormatName();
                            boolean png=lower.endsWith(".png") && "PNG".equalsIgnoreCase(format);
                            boolean jpg=(lower.endsWith(".jpg")||lower.endsWith(".jpeg")) && "JPEG".equalsIgnoreCase(format);
                            if(!png && !jpg) throw invalid("Phần mở rộng không khớp dữ liệu ảnh JPEG/PNG.");
                            dimensions(reader.getWidth(0),reader.getHeight(0));
                            if(reader.read(0)==null) throw invalid("Dữ liệu ảnh bị hỏng.");
                            type=png?"image/png":"image/jpeg";
                        } finally { reader.dispose(); }
                    }
                }
                result.add(new Upload(name,type,bytes));
            } catch(IOException|IllegalArgumentException e) { throw invalid("Không đọc được ảnh hoặc dữ liệu ảnh bị hỏng."); }
        }
        return List.copyOf(result);
    }
    private static void dimensions(long w,long h) {
        if(w<1 || h<1 || w*h>25_000_000L) throw invalid("Ảnh không hợp lệ hoặc vượt 25 triệu điểm ảnh.");
    }
    private static long le(byte[] b,int at,int n) {
        if(at<0 || at+n>b.length) throw invalid("Dữ liệu WebP bị thiếu.");
        long value=0; for(int i=0;i<n;i++) value|=(b[at+i]&255L)<<(8*i); return value;
    }
    private static boolean tag(byte[] b,int at,String tag) {
        if(at+4>b.length) return false;
        for(int i=0;i<4;i++) if(b[at+i]!=tag.charAt(i)) return false;
        return true;
    }
    private static void validateWebp(byte[] b) {
        // Java ImageIO has no WebP codec. Validate the RIFF container, chunk bounds,
        // frame signatures and dimensions; preserve original bytes without transcoding.
        if(b.length<26 || !tag(b,0,"RIFF") || !tag(b,8,"WEBP") || le(b,4,4)+8!=b.length)
            throw invalid("Ảnh WebP có cấu trúc không hợp lệ.");
        boolean frame=false;
        for(int p=12;p<b.length;) {
            long size=le(b,p+4,4); int start=p+8;
            if(size>b.length-start || size>Integer.MAX_VALUE) throw invalid("Ảnh WebP bị thiếu dữ liệu.");
            if(tag(b,p,"VP8 ")) {
                if(size<10 || (b[start]&1)!=0 || b[start+3]!=(byte)0x9d || b[start+4]!=1 || b[start+5]!=0x2a)
                    throw invalid("Khung WebP không hợp lệ.");
                dimensions(le(b,start+6,2)&0x3fff,le(b,start+8,2)&0x3fff); frame=true;
            } else if(tag(b,p,"VP8L")) {
                if(size<6 || b[start]!=0x2f || (b[start+4]&0xe0)!=0) throw invalid("Khung WebP không hợp lệ.");
                long bits=le(b,start+1,4); dimensions((bits&0x3fff)+1,((bits>>14)&0x3fff)+1); frame=true;
            } else if(tag(b,p,"VP8X")) {
                if(size!=10 || (b[start]&2)!=0) throw invalid("Vui lòng dùng ảnh WebP tĩnh, không dùng ảnh động.");
                dimensions(le(b,start+4,3)+1,le(b,start+7,3)+1);
            }
            long next=(long)start+size+(size&1);
            if(next>b.length) throw invalid("Ảnh WebP bị thiếu dữ liệu.");
            p=(int)next;
        }
        if(!frame) throw invalid("Không tìm thấy khung ảnh WebP.");
    }
    private static ApiException invalid(String message) { return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_INCIDENT_IMAGES",message); }
}
