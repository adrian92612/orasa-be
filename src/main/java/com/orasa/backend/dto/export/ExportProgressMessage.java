package com.orasa.backend.dto.export;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportProgressMessage {

    private String status;
    private int progressPercent;
    private String message;
    private String csvData;

    public static ExportProgressMessage preparing(int percent, String message) {
        return ExportProgressMessage.builder()
                .status("PREPARING")
                .progressPercent(percent)
                .message(message)
                .build();
    }

    public static ExportProgressMessage generating(int percent, String message) {
        return ExportProgressMessage.builder()
                .status("GENERATING")
                .progressPercent(percent)
                .message(message)
                .build();
    }

    public static ExportProgressMessage complete(String csvData) {
        return ExportProgressMessage.builder()
                .status("COMPLETE")
                .progressPercent(100)
                .message("Export complete")
                .csvData(csvData)
                .build();
    }

    public static ExportProgressMessage error(String message) {
        return ExportProgressMessage.builder()
                .status("ERROR")
                .progressPercent(0)
                .message(message)
                .build();
    }
}
