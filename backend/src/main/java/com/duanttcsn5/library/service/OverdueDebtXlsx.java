package com.duanttcsn5.library.service;

import com.duanttcsn5.library.dto.report.OverdueDebtReportDtos.Reader;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Minimal OOXML workbook. Text is inlineStr (never a formula); numbers remain numeric cells. */
@Component
public class OverdueDebtXlsx {
    private static final String NS="http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    public static final List<String> HEADERS=List.of("Mã thẻ","Họ tên","Số điện thoại","Số cuốn quá hạn","Số ngày trễ dài nhất","Tổng phí đang nợ");
    public byte[] create(List<Reader> rows) throws IOException {
        if(rows.size()>1_048_575) throw new IOException("Danh sách vượt giới hạn dòng của Excel.");
        var bytes=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(bytes,StandardCharsets.UTF_8)) {
            entry(zip,"[Content_Types].xml","""
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                <Default Extension="xml" ContentType="application/xml"/>
                <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
                </Types>""");
            entry(zip,"_rels/.rels","""
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>""");
            entry(zip,"xl/workbook.xml","<workbook xmlns=\""+NS+"\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets><sheet name=\"Quá hạn và nợ phí\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            entry(zip,"xl/_rels/workbook.xml.rels","""
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                </Relationships>""");
            entry(zip,"xl/styles.xml","""
                <styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                <numFmts count="1"><numFmt numFmtId="164" formatCode="#,##0 &quot;VND&quot;"/></numFmts>
                <fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>
                <fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
                <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
                <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
                <cellXfs count="4">
                <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
                <xf numFmtId="49" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
                <xf numFmtId="164" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/>
                <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyAlignment="1"><alignment wrapText="1"/></xf>
                </cellXfs>
                <cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles>
                </styleSheet>""");
            zip.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            var out=new OutputStreamWriter(zip,StandardCharsets.UTF_8);
            int last=rows.size()+1;
            out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet xmlns=\""+NS+"\"><dimension ref=\"A1:F"+last+"\"/><sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews><cols><col min=\"1\" max=\"1\" width=\"22\" customWidth=\"1\"/><col min=\"2\" max=\"2\" width=\"32\" customWidth=\"1\"/><col min=\"3\" max=\"6\" width=\"24\" customWidth=\"1\"/></cols><sheetData><row r=\"1\" ht=\"32\" customHeight=\"1\">");
            for(int col=0;col<6;col++) text(out,""+(char)('A'+col)+1,HEADERS.get(col),3);
            out.write("</row>");
            int row=2;
            for(var reader:rows) {
                out.write("<row r=\""+row+"\">");
                text(out,"A"+row,reader.cardNumber(),1); text(out,"B"+row,reader.fullName(),0); text(out,"C"+row,reader.phone(),1);
                number(out,"D"+row,Long.toString(reader.overdueBooks()),0);
                number(out,"E"+row,Long.toString(reader.maxOverdueDays()),0);
                number(out,"F"+row,reader.outstandingVnd().toPlainString(),2);
                out.write("</row>");row++;
            }
            out.write("</sheetData><autoFilter ref=\"A1:F"+last+"\"/></worksheet>");out.flush();zip.closeEntry();
        }
        return bytes.toByteArray();
    }
    private static void entry(ZipOutputStream zip,String name,String xml) throws IOException {
        zip.putNextEntry(new ZipEntry(name));zip.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"+xml).getBytes(StandardCharsets.UTF_8));zip.closeEntry();
    }
    private static void text(Writer out,String cell,String value,int style) throws IOException {
        out.write("<c r=\""+cell+"\" s=\""+style+"\" t=\"inlineStr\"><is><t xml:space=\"preserve\">"+escape(value)+"</t></is></c>");
    }
    private static void number(Writer out,String cell,String value,int style) throws IOException {
        out.write("<c r=\""+cell+"\" s=\""+style+"\"><v>"+value+"</v></c>");
    }
    private static String escape(String value) {
        if(value==null) return "";
        var safe=new StringBuilder();
        value.codePoints().filter(c->c==9 || c==10 || c==13 || (c>=32 && c<=0xD7FF)
            || (c>=0xE000 && c<=0xFFFD) || (c>=0x10000 && c<=0x10FFFF)).forEach(safe::appendCodePoint);
        return safe.toString().replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");
    }
}
