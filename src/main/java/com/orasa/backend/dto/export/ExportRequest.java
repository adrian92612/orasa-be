package com.orasa.backend.dto.export;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExportRequest {

    @Min(1)
    @Max(12)
    private int month;

    @Min(2020)
    private int year;
}
