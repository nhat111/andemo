package com.example.andemo.nexacro;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Đọc / ghi định dạng XML Dataset của Nexacro (thứ X-API gửi qua lại với transaction()),
 * chỉ dùng thư viện chuẩn của JDK.
 *
 * <pre>
 * &lt;Root xmlns="http://www.nexacroplatform.com/platform/dataset"&gt;
 *   &lt;Parameters&gt;&lt;Parameter id="ErrorCode" type="int"&gt;0&lt;/Parameter&gt;…&lt;/Parameters&gt;
 *   &lt;Dataset id="ds_list"&gt;
 *     &lt;ColumnInfo&gt;&lt;Column id="barcode" type="string" size="20"/&gt;…&lt;/ColumnInfo&gt;
 *     &lt;Rows&gt;
 *       &lt;Row&gt;&lt;Col id="barcode"&gt;890…&lt;/Col&gt;…&lt;/Row&gt;
 *       &lt;Row type="update"&gt;…&lt;OrgRow&gt;&lt;Col …/&gt;&lt;/OrgRow&gt;&lt;/Row&gt;
 *     &lt;/Rows&gt;
 *   &lt;/Dataset&gt;
 * &lt;/Root&gt;
 * </pre>
 *
 * Theo "Dataset XML Format" trong tài liệu Nexacro (docs.tobesoft.com), có hỗ trợ ConstColumn,
 * rowtype (insert / update / delete) và OrgRow. Chưa hỗ trợ: SSV / binary, cột BLOB.
 */
public final class NexacroXml {

    public static final String NAMESPACE = "http://www.nexacroplatform.com/platform/dataset";

    private NexacroXml() {
    }

    public static NexacroData parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // Chặn XXE: không cho XML tham chiếu file / URL bên ngoài
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));

            NexacroData data = new NexacroData();
            Element root = doc.getDocumentElement();
            for (Element parameters : children(root, "Parameters")) {
                for (Element p : children(parameters, "Parameter")) {
                    data.getParams().put(p.getAttribute("id"), convert(p.getTextContent(), p.getAttribute("type")));
                }
            }
            for (Element dsEl : children(root, "Dataset")) {
                data.addDataset(parseDataset(dsEl));
            }
            return data;
        } catch (Exception e) {
            throw new IllegalArgumentException("Không đọc được XML Nexacro: " + e.getMessage(), e);
        }
    }

    private static NxDataset parseDataset(Element dsEl) {
        NxDataset ds = new NxDataset(dsEl.getAttribute("id"));
        // ConstColumn: cột có cùng 1 giá trị cho mọi dòng (khai báo trước Column, có thuộc tính value)
        Map<String, Object> constValues = new LinkedHashMap<>();
        for (Element info : children(dsEl, "ColumnInfo")) {
            for (Element col : children(info, "ConstColumn")) {
                String type = normalizeType(col.getAttribute("type"));
                ds.addColumn(col.getAttribute("id"), type);
                constValues.put(col.getAttribute("id"), convert(col.getAttribute("value"), type));
            }
            for (Element col : children(info, "Column")) {
                ds.addColumn(col.getAttribute("id"), normalizeType(col.getAttribute("type")));
            }
        }
        for (Element rowsEl : children(dsEl, "Rows")) {
            for (Element rowEl : children(rowsEl, "Row")) {
                Map<String, Object> row = ds.addRow();
                row.putAll(constValues);
                readCols(rowEl, ds, row);
                String type = rowEl.getAttribute("type");
                if (!type.isEmpty() && !"normal".equalsIgnoreCase(type)) {
                    row.put(NxDataset.ROW_TYPE, type.toLowerCase(Locale.ROOT));
                }
                for (Element org : children(rowEl, "OrgRow")) {
                    Map<String, Object> orgRow = new LinkedHashMap<>();
                    readCols(org, ds, orgRow);
                    row.put(NxDataset.ORG_ROW, orgRow);
                }
            }
        }
        return ds;
    }

    private static void readCols(Element parent, NxDataset ds, Map<String, Object> target) {
        for (Element col : children(parent, "Col")) {
            String id = col.getAttribute("id");
            target.put(id, convert(col.getTextContent(), ds.getColumns().getOrDefault(id, "string")));
        }
    }

    /**
     * Giá trị trong XML luôn là chữ; đổi sang kiểu Java theo kiểu cột.
     * Theo tài liệu Nexacro: không có thẻ Col = null (không đưa vào Map);
     * {@code <Col id="x"/>} = chuỗi rỗng (cột số: null).
     */
    static Object convert(String text, String type) {
        String t = normalizeType(type);
        if (text == null || text.isEmpty()) {
            return "string".equals(t) || "date".equals(t) || "datetime".equals(t) || "time".equals(t)
                    ? "" : null;
        }
        switch (t) {
            case "int":
                return Long.parseLong(text.trim());
            case "bigdecimal":
            case "float":
            case "double":
            case "decimal":
                return new BigDecimal(text.trim());
            default:
                // string, date (yyyyMMdd), datetime (yyyyMMddHHmmssSSS)…: giữ nguyên dạng chữ
                return text;
        }
    }

    /** Tài liệu dùng chữ hoa (STRING, INT…); trong code dùng chữ thường cho dễ so sánh. */
    static String normalizeType(String type) {
        return type == null || type.isEmpty() ? "string" : type.toLowerCase(Locale.ROOT);
    }

    public static String write(NexacroData data) {
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            Element root = doc.createElementNS(NAMESPACE, "Root");
            root.setAttribute("ver", "4000");
            doc.appendChild(root);

            if (!data.getParams().isEmpty()) {
                Element params = el(doc, root, "Parameters");
                data.getParams().forEach((name, value) -> {
                    Element p = el(doc, params, "Parameter");
                    p.setAttribute("id", name);
                    p.setAttribute("type", typeOf(value).toUpperCase(Locale.ROOT));
                    if (value != null) {
                        p.setTextContent(value.toString());
                    }
                });
            }
            for (NxDataset ds : data.getDatasets().values()) {
                Element dsEl = el(doc, root, "Dataset");
                dsEl.setAttribute("id", ds.getId());
                Element info = el(doc, dsEl, "ColumnInfo");
                ds.getColumns().forEach((name, type) -> {
                    Element c = el(doc, info, "Column");
                    c.setAttribute("id", name);
                    c.setAttribute("type", type.toUpperCase(Locale.ROOT));
                    c.setAttribute("size", "int".equals(type) ? "10" : "256");
                });
                Element rowsEl = el(doc, dsEl, "Rows");
                for (Map<String, Object> row : ds.getRows()) {
                    Element rowEl = el(doc, rowsEl, "Row");
                    String rowType = NxDataset.rowType(row);
                    if (!"normal".equals(rowType)) {
                        rowEl.setAttribute("type", rowType);
                    }
                    writeCols(doc, rowEl, ds, row);
                    Object org = row.get(NxDataset.ORG_ROW);
                    if (org instanceof Map<?, ?> orgRow) {
                        Element orgEl = el(doc, rowEl, "OrgRow");
                        @SuppressWarnings("unchecked")
                        Map<String, Object> typed = (Map<String, Object>) orgRow;
                        writeCols(doc, orgEl, ds, typed);
                    }
                }
            }

            Transformer t = TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            t.setOutputProperty(OutputKeys.INDENT, "yes");
            StringWriter out = new StringWriter();
            t.transform(new DOMSource(doc), new StreamResult(out));
            return out.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Không tạo được XML Nexacro", e);
        }
    }

    private static void writeCols(Document doc, Element parent, NxDataset ds, Map<String, Object> row) {
        for (String name : ds.getColumns().keySet()) {
            Object v = row.get(name);
            if (v != null) {
                Element c = el(doc, parent, "Col");
                c.setAttribute("id", name);
                c.setTextContent(v.toString());
            }
        }
    }

    /** Kiểu Nexacro suy ra từ giá trị Java (dùng khi dữ liệu đến từ JSON). */
    static String typeOf(Object value) {
        if (value instanceof Integer || value instanceof Long || value instanceof Short) {
            return "int";
        }
        if (value instanceof Number) {
            return "bigdecimal";
        }
        return "string";
    }

    private static Element el(Document doc, Element parent, String name) {
        Element e = doc.createElementNS(NAMESPACE, name);
        parent.appendChild(e);
        return e;
    }

    private static java.util.List<Element> children(Element parent, String localName) {
        java.util.List<Element> result = new java.util.ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            if (n instanceof Element e) {
                String name = e.getLocalName() != null ? e.getLocalName() : e.getNodeName();
                if (localName.equals(name)) {
                    result.add(e);
                }
            }
        }
        return result;
    }
}
