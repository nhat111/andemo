package com.example.andemo.nexacro;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Công cụ thử chuyển đổi: dán 1 response XML thật của server X-API để xem JSON app sẽ nhận,
 * hoặc ngược lại. Cần đăng nhập. Xem docs/NEXACRO_XAPI_TO_JSON.md mục 5.
 */
@RestController
@RequestMapping("/api/nx-tools")
public class NexacroToolsController {

    @PostMapping(value = "/xml-to-json", consumes = {MediaType.TEXT_XML_VALUE, MediaType.APPLICATION_XML_VALUE,
            MediaType.TEXT_PLAIN_VALUE})
    public ResponseEntity<Map<String, Object>> xmlToJson(@RequestBody String xml) {
        try {
            return ResponseEntity.ok(NexacroJson.toJson(NexacroXml.parse(xml)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("errorCode", -900, "errorMsg", e.getMessage()));
        }
    }

    @PostMapping(value = "/json-to-xml", produces = MediaType.TEXT_XML_VALUE)
    public String jsonToXml(@RequestBody Map<String, Object> json) {
        return NexacroXml.write(NexacroJson.fromJson(json));
    }
}
