package com.orasa.backend.dto.activity;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single field change in an activity log.
 * Used to build structured details for the frontend.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldChange {
    
    /**
     * Display-friendly name of the field
     * Example: "Customer Name", "Start Time", "Status"
     */
    private String field;
    
    /**
     * Value before the change
     */
    private String before;
    
    /**
     * Value after the change
     */
    private String after;
    
    /**
     * Helper to create a list of changes and serialize to JSON
     */
    public static String toJson(List<FieldChange> changes) {
        if (changes == null || changes.isEmpty()) {
            return null;
        }
        
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < changes.size(); i++) {
            FieldChange c = changes.get(i);
            if (i > 0) json.append(",");
            json.append("{")
                .append("\"field\":\"").append(escapeJson(c.getField())).append("\",")
                .append("\"before\":\"").append(escapeJson(c.getBefore())).append("\",")
                .append("\"after\":\"").append(escapeJson(c.getAfter())).append("\"")
                .append("}");
        }
        json.append("]");
        return json.toString();
    }
    
    private static String escapeJson(String value) {
        if (value == null) return "";
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
