package com.example.andemo.nexacro;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Nội dung 1 lần gửi / nhận của transaction Nexacro (tương đương PlatformData của X-API):
 * các biến (Parameters / VariableList) + các Dataset.
 */
public class NexacroData {

    public static final String ERROR_CODE = "ErrorCode";
    public static final String ERROR_MSG = "ErrorMsg";

    private final Map<String, Object> params = new LinkedHashMap<>();
    private final Map<String, NxDataset> datasets = new LinkedHashMap<>();

    public Map<String, Object> getParams() {
        return params;
    }

    public Map<String, NxDataset> getDatasets() {
        return datasets;
    }

    public NexacroData addDataset(NxDataset ds) {
        datasets.put(ds.getId(), ds);
        return this;
    }

    public NxDataset dataset(String id) {
        return datasets.get(id);
    }

    public static NexacroData success() {
        NexacroData data = new NexacroData();
        data.params.put(ERROR_CODE, 0);
        data.params.put(ERROR_MSG, "SUCC");
        return data;
    }

    public static NexacroData error(int code, String message) {
        NexacroData data = new NexacroData();
        data.params.put(ERROR_CODE, code);
        data.params.put(ERROR_MSG, message);
        return data;
    }
}
