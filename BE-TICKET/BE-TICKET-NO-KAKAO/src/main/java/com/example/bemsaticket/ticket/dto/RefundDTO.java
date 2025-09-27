package com.example.bemsaticket.ticket.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@NoArgsConstructor
public class RefundDTO {
    private String eventName;
    private String section;
    private int seatNumber;
    private int price;
    private String eventTime;
    private LocalDate purchaseDate;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH:mm:ss")
    private LocalTime purchaseTime;
    private String tid;
    private String NAMESPACE;
//            parameters.put("tax_free_amount", "0");
//        parameters.put("vat_amount", "0");
}
