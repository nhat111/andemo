package com.example.andemo;

import com.example.andemo.nexacro.NexacroData;
import com.example.andemo.nexacro.NexacroJson;
import com.example.andemo.nexacro.NexacroXml;
import com.example.andemo.nexacro.NxDataset;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NexacroXmlTest {

    @Test
    void parsesParametersTypesRowTypeAndOrgRow() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <Root xmlns="http://www.nexacroplatform.com/platform/dataset">
                  <Parameters>
                    <Parameter id="ErrorCode" type="int">0</Parameter>
                    <Parameter id="ErrorMsg" type="string">SUCC</Parameter>
                  </Parameters>
                  <Dataset id="ds_stock">
                    <ColumnInfo>
                      <Column id="barcode" type="STRING" size="20"/>
                      <Column id="qty" type="INT" size="10"/>
                      <Column id="price" type="BIGDECIMAL" size="16"/>
                      <Column id="inDate" type="DATE" size="8"/>
                    </ColumnInfo>
                    <Rows>
                      <Row><Col id="barcode">A</Col><Col id="qty">1</Col><Col id="price">1.50</Col><Col id="inDate">20260929</Col></Row>
                      <Row type="update"><Col id="barcode">B</Col><Col id="qty">8</Col>
                        <OrgRow><Col id="barcode">B</Col><Col id="qty">5</Col></OrgRow></Row>
                    </Rows>
                  </Dataset>
                </Root>""";

        NexacroData data = NexacroXml.parse(xml);

        assertThat(data.getParams()).containsEntry("ErrorCode", 0L).containsEntry("ErrorMsg", "SUCC");
        NxDataset ds = data.dataset("ds_stock");
        assertThat(ds.getColumns()).containsEntry("qty", "int").containsEntry("inDate", "date");
        Map<String, Object> first = ds.getRows().get(0);
        assertThat(first).containsEntry("qty", 1L).containsEntry("price", new BigDecimal("1.50"))
                .containsEntry("inDate", "20260929").doesNotContainKey(NxDataset.ROW_TYPE);
        Map<String, Object> second = ds.getRows().get(1);
        assertThat(second).containsEntry(NxDataset.ROW_TYPE, "update").doesNotContainKey("price");
        assertThat(second.get(NxDataset.ORG_ROW)).isEqualTo(Map.of("barcode", "B", "qty", 5L));
    }

    @Test
    void jsonToXmlToJsonRoundTrip() {
        Map<String, Object> json = Map.of(
                "params", Map.of("mode", "A"),
                "datasets", Map.of("ds_in", List.of(
                        Map.of("name", "Sữa <1L> & \"ngon\"", "qty", 3),
                        Map.of("_rowType", "insert", "name", "Mì", "qty", 2))));

        NexacroData parsed = NexacroXml.parse(NexacroXml.write(NexacroJson.fromJson(json)));

        assertThat(parsed.getParams()).containsEntry("mode", "A");
        NxDataset ds = parsed.dataset("ds_in");
        assertThat(ds.getColumns()).containsEntry("name", "string").containsEntry("qty", "int");
        assertThat(ds.getRows().get(0)).containsEntry("name", "Sữa <1L> & \"ngon\"").containsEntry("qty", 3L);
        assertThat(ds.getRows().get(1)).containsEntry(NxDataset.ROW_TYPE, "insert");
    }

    @Test
    void rejectsDoctype() {
        String xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE r [<!ENTITY x SYSTEM "file:///etc/passwd">]>
                <Root><Parameters><Parameter id="a">&x;</Parameter></Parameters></Root>""";
        assertThatThrownBy(() -> NexacroXml.parse(xxe)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- Ví dụ nguyên văn trong tài liệu TOBESOFT ----------

    @Test
    void officialExampleWithConstColumns() throws IOException {
        NexacroData data = NexacroXml.parse(resource("official_dataset_example.xml"));

        assertThat(data.getParams()).containsEntry("service", "stock").containsEntry("method", "search");
        NxDataset ds = data.dataset("output");
        assertThat(ds.getColumns()).containsKeys("market", "openprice", "stockCode", "currentprice");
        assertThat(ds.getRows()).hasSize(2);
        // ConstColumn: có mặt ở mọi dòng
        assertThat(ds.getRows().get(0)).containsEntry("market", "kse").containsEntry("openprice", 15000L)
                .containsEntry("currentprice", 5700L)
                // cột không khai báo trong ColumnInfo (lỗi trong tài liệu): đọc như chuỗi, không lỗi
                .containsEntry("currentCode", "10001");
        assertThat(ds.getRows().get(1)).containsEntry("market", "kse").containsEntry("currentprice", 14500L);
    }

    @Test
    void officialRowTypesAndEmptyVersusNull() throws IOException {
        Map<String, Object> json = NexacroJson.toJson(NexacroXml.parse(resource("official_rowtype_example.xml")));

        assertThat(json).containsEntry("errorCode", 0).containsEntry("errorMsg", "SUCC");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) ((Map<String, Object>) json.get("datasets")).get("ds_stock");
        assertThat(rows).hasSize(4);
        assertThat(rows.get(0)).containsEntry("_rowType", "insert").containsEntry("currentprice", 13400L);
        assertThat(rows.get(1)).containsEntry("_rowType", "update")
                .containsEntry("_orgRow", Map.of("currentCode", "10001", "currentprice", 13700L));
        assertThat(rows.get(2)).containsEntry("_rowType", "delete");
        // <Col id="memo"/> = chuỗi rỗng; không có thẻ currentprice = null (không có key)
        assertThat(rows.get(3)).containsEntry("memo", "").doesNotContainKey("currentprice").doesNotContainKey("_rowType");
    }

    @Test
    void writerUsesUppercaseTypesLikeDocumentation() {
        NexacroData data = NexacroData.success();
        NxDataset ds = new NxDataset("ds_list").addColumn("name", "string").addColumn("qty", "int");
        ds.addRow().put("name", "");
        data.addDataset(ds);

        String xml = NexacroXml.write(data);

        assertThat(xml).contains("type=\"STRING\"").contains("type=\"INT\"").contains("ver=\"4000\"");
        assertThat(NexacroXml.parse(xml).dataset("ds_list").getRows().get(0)).containsEntry("name", "");
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = NexacroXmlTest.class.getResourceAsStream("/nexacro/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
