package com.example.andemo.disposal;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Số thứ tự chứng từ theo cửa hàng + ngày, để sinh 전표번호 không trùng. */
@Entity
@Table(name = "document_sequence")
@Data
@NoArgsConstructor
public class DocumentSequence {

    /** Ví dụ DSP-S001-20260929 */
    @Id
    private String sequenceKey;

    private int lastSeq;

    public DocumentSequence(String sequenceKey) {
        this.sequenceKey = sequenceKey;
    }
}
