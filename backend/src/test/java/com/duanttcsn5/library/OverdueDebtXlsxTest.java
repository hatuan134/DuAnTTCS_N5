package com.duanttcsn5.library;

import com.duanttcsn5.library.dto.report.OverdueDebtReportDtos.Reader;
import com.duanttcsn5.library.service.OverdueDebtXlsx;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipInputStream;
import static org.assertj.core.api.Assertions.*;

class OverdueDebtXlsxTest {
    private Map<String,String> entries(byte[] content) throws Exception {
        var map=new HashMap<String,String>();
        try(var zip=new ZipInputStream(new ByteArrayInputStream(content),StandardCharsets.UTF_8)) {
            for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry())
                map.put(entry.getName(),new String(zip.readAllBytes(),StandardCharsets.UTF_8));
        } return map;
    }
    private Document xml(String value) throws Exception {
        var factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8)));
    }
    @Test void realXlsxSixColumnsUnicodeTextLeadingZerosNumbersVndAndNoFormulas() throws Exception {
        var data=entries(new OverdueDebtXlsx().create(List.of(new Reader(1L,"00123","Nguyễn Văn A & <B>","0901234567",3,15,new BigDecimal("75000")),
            new Reader(2L,"=1+1","=HYPERLINK(\"x\")",null,0,0,BigDecimal.ZERO))));
        for(String value:data.values()) xml(value); // all parts must be well-formed XML
        var sheet=xml(data.get("xl/worksheets/sheet1.xml"));
        assertThat(sheet.getElementsByTagName("row").getLength()).isEqualTo(3);
        assertThat(sheet.getElementsByTagName("c").getLength()).isEqualTo(18);
        assertThat(sheet.getElementsByTagName("f").getLength()).isZero();
        assertThat(sheet.getElementsByTagName("v").getLength()).isEqualTo(6);
        assertThat(sheet.getElementsByTagName("t").item(6).getTextContent()).isEqualTo("00123");
        assertThat(sheet.getElementsByTagName("t").item(7).getTextContent()).isEqualTo("Nguyễn Văn A & <B>");
        assertThat(sheet.getElementsByTagName("t").item(8).getTextContent()).isEqualTo("0901234567");
        assertThat(data.get("xl/styles.xml")).contains("VND");
    }
    @Test void emptyWorkbookHasExactlySixHeaderCells() throws Exception {
        var sheet=xml(entries(new OverdueDebtXlsx().create(List.of())).get("xl/worksheets/sheet1.xml"));
        assertThat(sheet.getElementsByTagName("row").getLength()).isEqualTo(1);
        assertThat(sheet.getElementsByTagName("c").getLength()).isEqualTo(6);
    }
    @Test void illegalXmlControlCharactersCannotCorruptWorkbook() throws Exception {
        var parts=entries(new OverdueDebtXlsx().create(List.of(new Reader(1L,"001","A\u0001B","0901",0,0,BigDecimal.ONE))));
        assertThat(xml(parts.get("xl/worksheets/sheet1.xml")).getDocumentElement().getTextContent()).contains("AB");
    }
}
